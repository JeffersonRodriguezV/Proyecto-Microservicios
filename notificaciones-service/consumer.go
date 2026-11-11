package main

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"log/slog"
	"os"
	"sync/atomic"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"
)

// obtenerEnv lee una variable de entorno, o usa el valor por defecto.
func obtenerEnv(clave, porDefecto string) string {
	if valor := os.Getenv(clave); valor != "" {
		return valor
	}
	return porDefecto
}

// brokerConectado refleja si el consumidor está escuchando la cola (lo lee /health).
var brokerConectado atomic.Bool

// iniciarConsumidor mantiene vivo el consumo de eventos: si RabbitMQ no está disponible o la
// conexión se cae, espera y vuelve a intentarlo (con espera creciente, máximo 30 s).
// Antes terminaba para siempre al caer el broker y el servicio dejaba de recibir eventos.
func iniciarConsumidor() {
	if obtenerEnv("RABBITMQ_PASSWORD", "") == "" {
		log.Fatal("RABBITMQ_PASSWORD no está definido (revise el archivo .env)")
	}
	espera := time.Second
	for {
		inicio := time.Now()
		err := consumirUnaVez()
		brokerConectado.Store(false)
		log.Println("Consumidor detenido:", err, "- reintentando en", espera)
		time.Sleep(espera)
		espera = siguienteEspera(espera, time.Since(inicio))
	}
}

// siguienteEspera calcula cuánto esperar antes del próximo reintento: se duplica hasta 30 s,
// y vuelve a 1 s si la conexión anterior duró más de un minuto (fue estable).
func siguienteEspera(actual, duracionConexion time.Duration) time.Duration {
	if duracionConexion > time.Minute {
		return time.Second
	}
	if actual < 30*time.Second {
		return actual * 2
	}
	return actual
}

// consumirUnaVez abre la conexión, declara la topología y procesa mensajes hasta que el canal se cierra.
func consumirUnaVez() error {
	host := obtenerEnv("RABBITMQ_HOST", "localhost")
	puerto := obtenerEnv("RABBITMQ_PORT", "5672")
	usuario := obtenerEnv("RABBITMQ_USERNAME", "admin")
	password := obtenerEnv("RABBITMQ_PASSWORD", "")
	exchange := obtenerEnv("EVENTOS_EXCHANGE", "ecosistema.eventos")

	url := fmt.Sprintf("amqp://%s:%s@%s:%s/", usuario, password, host, puerto)

	conn, err := amqp.Dial(url)
	if err != nil {
		return fmt.Errorf("no se pudo conectar a RabbitMQ: %w", err)
	}

	defer conn.Close()

	ch, err := conn.Channel()
	if err != nil {
		return fmt.Errorf("no se pudo abrir el canal: %w", err)
	}

	err = ch.ExchangeDeclare(exchange, "topic", true, false, false, false, nil)
	if err != nil {
		return fmt.Errorf("no se pudo declarar el exchange: %w", err)
	}

	cola, err := ch.QueueDeclare("notificaciones-service.eventos", true, false, false, false, nil)
	if err != nil {
		return fmt.Errorf("no se pudo declarar la cola: %w", err)
	}

	// Reto 5: la bienvenida del onboarding sale de usuario.creado (trae el token de
	// activación), no de empleado.creado. Se retira ese enlace de la cola, que es
	// durable y conserva el binding del Reto 4; si ya no existe, el unbind es inocuo.
	if err := ch.QueueUnbind(cola.Name, "empleado.creado", exchange, nil); err != nil {
		log.Println("Aviso al desenlazar empleado.creado:", err)
	}

	eventosRelevantes := []string{
		"empleado.retirado",
		"vacaciones.programadas",
		"usuario.creado",
		"usuario.recuperacion",
		"cuenta.activada",
		"cuenta.desactivada",
	}
	for _, routingKey := range eventosRelevantes {
		err = ch.QueueBind(cola.Name, routingKey, exchange, false, nil)
		if err != nil {
			return fmt.Errorf("no se pudo enlazar la cola para %s: %w", routingKey, err)
		}
	}

	mensajes, err := ch.Consume(cola.Name, "", false, false, false, false, nil)
	if err != nil {
		return fmt.Errorf("no se pudo iniciar el consumo: %w", err)
	}

	brokerConectado.Store(true)
	log.Println("Escuchando eventos: empleado.retirado, vacaciones.programadas, usuario.creado, usuario.recuperacion, cuenta.activada, cuenta.desactivada")

	for msg := range mensajes {
		procesarMensaje(msg)
	}

	// El canal se cerró (RabbitMQ cayó o cerró la conexión): /health pasa a DOWN.
	brokerConectado.Store(false)
	return fmt.Errorf("la conexión con RabbitMQ se cerró")
}

func procesarMensaje(msg amqp.Delivery) {
	var envelope EventoEnvelope
	if err := json.Unmarshal(msg.Body, &envelope); err != nil {
		log.Println("Mensaje con formato inválido, se descarta:", err)
		notificacionesErrores.WithLabelValues("formato_invalido").Inc()
		msg.Ack(false)
		return
	}

	// Continúa la traza del productor: el `traceparent` viaja en las cabeceras del mensaje.
	ctx, span := tracer().Start(contextoDelMensaje(msg), "notificaciones.procesar "+envelope.Type,
		trace.WithSpanKind(trace.SpanKindConsumer))
	defer span.End()
	span.SetAttributes(attribute.String("messaging.system", "rabbitmq"),
		attribute.String("messaging.message.id", envelope.ID), attribute.String("evento.tipo", envelope.Type))

	// Deduplicación: si ya procesamos este id, confirmamos sin repetir el efecto.
	var existe int
	err := db.QueryRow("SELECT COUNT(*) FROM eventos_procesados WHERE id = ?", envelope.ID).Scan(&existe)
	if err != nil {
		log.Println("Error verificando deduplicación:", err)
		notificacionesErrores.WithLabelValues("deduplicacion").Inc()
		msg.Nack(false, true)
		return
	}
	if existe > 0 {
		log.Printf("Evento %s ya procesado, se descarta (deduplicación)\n", envelope.ID)
		msg.Ack(false)
		return
	}

	notificacion := construirNotificacion(envelope)
	if notificacion == nil {
		msg.Ack(false)
		return
	}

	if guardarNotificacion(db, *notificacion) {
		notificacionesEnviadas.WithLabelValues(notificacion.Tipo).Inc()
	}
	registrarEventoProcesado(db, envelope.ID)

	slog.InfoContext(ctx, "notificacion generada", "tipo", notificacion.Tipo,
		"destinatario", notificacion.Destinatario, "evento", envelope.Type, "mensaje", notificacion.Mensaje)

	msg.Ack(false)
}

// texto extrae un campo string del data del evento ("" si no existe).
func texto(data map[string]interface{}, clave string) string {
	valor, _ := data[clave].(string)
	return valor
}

// enlaceReset simula el enlace que llevaría el correo real (Reto 5).
func enlaceReset(token string) string {
	return "https://app.empresa.com/reset?token=" + token
}

func construirNotificacion(envelope EventoEnvelope) *Notificacion {
	empleadoID := texto(envelope.Data, "empleadoId")
	email := texto(envelope.Data, "email")

	var tipo, mensaje string

	switch envelope.Type {
	case "empleado.retirado":
		tipo = "DESVINCULACION"
		mensaje = "Su cuenta ha sido desactivada, gracias por su trabajo"

	case "vacaciones.programadas":
		tipo = "VACACIONES"
		mensaje = fmt.Sprintf("Sus vacaciones del %s al %s han sido confirmadas",
			texto(envelope.Data, "fechaInicio"), texto(envelope.Data, "fechaFin"))

	case "usuario.creado":
		// Correo de bienvenida: es el que trae el token de activación.
		tipo = "SEGURIDAD"
		mensaje = fmt.Sprintf("Bienvenido. Para establecer su contraseña use este enlace: %s (expira %s)",
			enlaceReset(texto(envelope.Data, "tokenActivacion")), texto(envelope.Data, "expiraEn"))

	case "usuario.recuperacion":
		// Este evento no trae empleadoId (ver catálogo 3.5): solo email y token.
		tipo = "SEGURIDAD"
		mensaje = fmt.Sprintf("Para restablecer su contraseña use este enlace: %s (expira %s)",
			enlaceReset(texto(envelope.Data, "tokenRecuperacion")), texto(envelope.Data, "expiraEn"))

	case "cuenta.desactivada":
		tipo = "CUENTA"
		permanente, _ := envelope.Data["permanente"].(bool)
		if permanente {
			mensaje = "Su cuenta fue desactivada de forma permanente."
		} else {
			mensaje = fmt.Sprintf("Su cuenta fue desactivada temporalmente (motivo: %s). Se reactivará al finalizar el período.",
				texto(envelope.Data, "motivo"))
		}

	case "cuenta.activada":
		tipo = "CUENTA"
		if texto(envelope.Data, "motivo") == "FIN_VACACIONES" {
			mensaje = "Bienvenido de regreso. Su cuenta fue reactivada."
		} else {
			mensaje = "Su cuenta fue activada. Ya puede iniciar sesión."
		}

	default:
		log.Println("Tipo de evento no manejado:", envelope.Type)
		return nil
	}

	return &Notificacion{
		ID:           envelope.ID,
		Tipo:         tipo,
		Destinatario: email,
		Mensaje:      mensaje,
		FechaEnvio:   time.Now().UTC().Format(time.RFC3339),
		EmpleadoID:   empleadoID,
	}
}

// guardarNotificacion persiste la notificación y devuelve true si quedó guardada (para la métrica de negocio).
func guardarNotificacion(db *sql.DB, n Notificacion) bool {
	_, err := db.Exec(
		"INSERT INTO notificaciones (id, tipo, destinatario, mensaje, fecha_envio, empleado_id) VALUES (?, ?, ?, ?, ?, ?)",
		n.ID, n.Tipo, n.Destinatario, n.Mensaje, n.FechaEnvio, n.EmpleadoID,
	)
	if err != nil {
		log.Println("Error guardando notificación:", err)
		notificacionesErrores.WithLabelValues("guardado").Inc()
		return false
	}
	return true
}

func registrarEventoProcesado(db *sql.DB, id string) {
	_, err := db.Exec(
		"INSERT INTO eventos_procesados (id, procesado_en) VALUES (?, ?)",
		id, time.Now().UTC().Format(time.RFC3339),
	)
	if err != nil {
		log.Println("Error registrando evento procesado:", err)
	}
}