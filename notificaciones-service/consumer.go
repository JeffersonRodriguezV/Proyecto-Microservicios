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

	eventosRelevantes := []string{"empleado.creado", "empleado.retirado", "vacaciones.programadas"}
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

	log.Println("Escuchando eventos: empleado.creado, empleado.retirado, vacaciones.programadas")

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

func construirNotificacion(envelope EventoEnvelope) *Notificacion {
	empleadoID, _ := envelope.Data["empleadoId"].(string)
	email, _ := envelope.Data["email"].(string)

	var tipo, mensaje string

	switch envelope.Type {
	case "empleado.creado":
		nombre, _ := envelope.Data["nombre"].(string)
		apellido, _ := envelope.Data["apellido"].(string)
		tipo = "BIENVENIDA"
		mensaje = fmt.Sprintf("Bienvenido %s %s a la empresa", nombre, apellido)

	case "empleado.retirado":
		tipo = "DESVINCULACION"
		mensaje = "Su cuenta ha sido desactivada, gracias por su trabajo"

	case "vacaciones.programadas":
		fechaInicio, _ := envelope.Data["fechaInicio"].(string)
		fechaFin, _ := envelope.Data["fechaFin"].(string)
		tipo = "VACACIONES"
		mensaje = fmt.Sprintf("Sus vacaciones del %s al %s han sido confirmadas", fechaInicio, fechaFin)

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