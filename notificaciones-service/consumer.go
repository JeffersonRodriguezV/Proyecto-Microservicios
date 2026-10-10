package main

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

// obtenerEnv lee una variable de entorno, o usa el valor por defecto.
func obtenerEnv(clave, porDefecto string) string {
	if valor := os.Getenv(clave); valor != "" {
		return valor
	}
	return porDefecto
}

func iniciarConsumidor() {
	host := obtenerEnv("RABBITMQ_HOST", "localhost")
	puerto := obtenerEnv("RABBITMQ_PORT", "5672")
	usuario := obtenerEnv("RABBITMQ_USERNAME", "admin")
	password := obtenerEnv("RABBITMQ_PASSWORD", "admin")
	exchange := obtenerEnv("EVENTOS_EXCHANGE", "ecosistema.eventos")

	url := fmt.Sprintf("amqp://%s:%s@%s:%s/", usuario, password, host, puerto)

	conn, err := amqp.Dial(url)
	if err != nil {
		log.Fatal("No se pudo conectar a RabbitMQ:", err)
	}

	ch, err := conn.Channel()
	if err != nil {
		log.Fatal("No se pudo abrir el canal:", err)
	}

	err = ch.ExchangeDeclare(exchange, "topic", true, false, false, false, nil)
	if err != nil {
		log.Fatal("No se pudo declarar el exchange:", err)
	}

	cola, err := ch.QueueDeclare("notificaciones-service.eventos", true, false, false, false, nil)
	if err != nil {
		log.Fatal("No se pudo declarar la cola:", err)
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
			log.Fatal("No se pudo enlazar la cola para", routingKey, ":", err)
		}
	}

	mensajes, err := ch.Consume(cola.Name, "", false, false, false, false, nil)
	if err != nil {
		log.Fatal("No se pudo iniciar el consumo:", err)
	}

	log.Println("Escuchando eventos: empleado.retirado, vacaciones.programadas, usuario.creado, usuario.recuperacion, cuenta.activada, cuenta.desactivada")

	for msg := range mensajes {
		procesarMensaje(msg)
	}
}

func procesarMensaje(msg amqp.Delivery) {
	var envelope EventoEnvelope
	if err := json.Unmarshal(msg.Body, &envelope); err != nil {
		log.Println("Mensaje con formato inválido, se descarta:", err)
		msg.Ack(false)
		return
	}

	// Deduplicación: si ya procesamos este id, confirmamos sin repetir el efecto.
	var existe int
	err := db.QueryRow("SELECT COUNT(*) FROM eventos_procesados WHERE id = ?", envelope.ID).Scan(&existe)
	if err != nil {
		log.Println("Error verificando deduplicación:", err)
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

	guardarNotificacion(db, *notificacion)
	registrarEventoProcesado(db, envelope.ID)

	fmt.Printf("[NOTIFICACIÓN] Tipo: %s | Para: %s | Mensaje: \"%s\"\n",
		notificacion.Tipo, notificacion.Destinatario, notificacion.Mensaje)

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

func guardarNotificacion(db *sql.DB, n Notificacion) {
	_, err := db.Exec(
		"INSERT INTO notificaciones (id, tipo, destinatario, mensaje, fecha_envio, empleado_id) VALUES (?, ?, ?, ?, ?, ?)",
		n.ID, n.Tipo, n.Destinatario, n.Mensaje, n.FechaEnvio, n.EmpleadoID,
	)
	if err != nil {
		log.Println("Error guardando notificación:", err)
	}
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