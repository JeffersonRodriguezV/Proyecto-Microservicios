package main

import (
	"strings"
	"testing"

	amqp "github.com/rabbitmq/amqp091-go"
)

// reconocedor registra si el mensaje se confirmó (Ack) o se devolvió a la cola (Nack).
type reconocedor struct{ acks, nacks int }

func (r *reconocedor) Ack(uint64, bool) error        { r.acks++; return nil }
func (r *reconocedor) Nack(uint64, bool, bool) error { r.nacks++; return nil }
func (r *reconocedor) Reject(uint64, bool) error     { r.nacks++; return nil }

func entrega(r *reconocedor, cuerpo string) amqp.Delivery {
	return amqp.Delivery{Acknowledger: r, Body: []byte(cuerpo)}
}

func contar(t *testing.T, tabla string) int {
	t.Helper()
	var n int
	if err := db.QueryRow("SELECT COUNT(*) FROM " + tabla).Scan(&n); err != nil {
		t.Fatal(err)
	}
	return n
}

func TestConstruirNotificacionPorTipoDeEvento(t *testing.T) {
	casos := []struct {
		nombre       string
		evento       EventoEnvelope
		tipo         string
		contiene     string
		destinatario string
	}{
		{"retiro", EventoEnvelope{ID: "1", Type: "empleado.retirado", Data: map[string]interface{}{"empleadoId": "E1", "email": "a@x.com"}}, "DESVINCULACION", "desactivada", "a@x.com"},
		{"vacaciones", EventoEnvelope{ID: "2", Type: "vacaciones.programadas", Data: map[string]interface{}{"fechaInicio": "2026-12-01", "fechaFin": "2026-12-15", "email": "a@x.com"}}, "VACACIONES", "2026-12-01 al 2026-12-15", "a@x.com"},
		{"usuario creado trae enlace con token", EventoEnvelope{ID: "3", Type: "usuario.creado", Data: map[string]interface{}{"tokenActivacion": "TOK123", "expiraEn": "mañana", "email": "a@x.com"}}, "SEGURIDAD", "reset?token=TOK123", "a@x.com"},
		{"recuperación", EventoEnvelope{ID: "4", Type: "usuario.recuperacion", Data: map[string]interface{}{"tokenRecuperacion": "REC9", "email": "a@x.com"}}, "SEGURIDAD", "reset?token=REC9", "a@x.com"},
		{"baja permanente", EventoEnvelope{ID: "5", Type: "cuenta.desactivada", Data: map[string]interface{}{"permanente": true}}, "CUENTA", "permanente", ""},
		{"suspensión temporal", EventoEnvelope{ID: "6", Type: "cuenta.desactivada", Data: map[string]interface{}{"permanente": false, "motivo": "VACACIONES"}}, "CUENTA", "temporalmente (motivo: VACACIONES)", ""},
		{"reactivación fin de vacaciones", EventoEnvelope{ID: "7", Type: "cuenta.activada", Data: map[string]interface{}{"motivo": "FIN_VACACIONES"}}, "CUENTA", "regreso", ""},
		{"activación inicial", EventoEnvelope{ID: "8", Type: "cuenta.activada", Data: map[string]interface{}{"motivo": "ACTIVACION"}}, "CUENTA", "Ya puede iniciar sesión", ""},
	}
	for _, c := range casos {
		t.Run(c.nombre, func(t *testing.T) {
			n := construirNotificacion(c.evento)
			if n == nil {
				t.Fatal("no se generó la notificación")
			}
			if n.Tipo != c.tipo || !strings.Contains(n.Mensaje, c.contiene) || n.Destinatario != c.destinatario {
				t.Errorf("resultado inesperado: %+v", n)
			}
		})
	}
}

func TestEventoDesconocidoNoGeneraNotificacion(t *testing.T) {
	if construirNotificacion(EventoEnvelope{ID: "x", Type: "algo.raro"}) != nil {
		t.Error("un evento no manejado no debe generar notificación")
	}
}

func TestNingunMensajeContieneContrasena(t *testing.T) {
	// Regla del proyecto: nunca se transmiten contraseñas, solo enlaces con token.
	n := construirNotificacion(EventoEnvelope{ID: "9", Type: "usuario.creado", Data: map[string]interface{}{"tokenActivacion": "T", "expiraEn": "x"}})
	if strings.Contains(strings.ToLower(n.Mensaje), "contraseña temporal") {
		t.Error("el mensaje no debe contener contraseñas temporales")
	}
}

func TestProcesarMensajeGuardaYConfirma(t *testing.T) {
	prepararDB(t)
	r := &reconocedor{}
	procesarMensaje(entrega(r, `{"id":"ev-1","type":"vacaciones.programadas","data":{"empleadoId":"E1","email":"a@x.com","fechaInicio":"2026-12-01","fechaFin":"2026-12-10"}}`))
	if r.acks != 1 || r.nacks != 0 {
		t.Errorf("esperaba 1 ack, obtuve acks=%d nacks=%d", r.acks, r.nacks)
	}
	if contar(t, "notificaciones") != 1 || contar(t, "eventos_procesados") != 1 {
		t.Error("la notificación y el evento procesado deben quedar guardados")
	}
}

func TestEventoDuplicadoSeDescarta(t *testing.T) {
	prepararDB(t)
	r := &reconocedor{}
	cuerpo := `{"id":"ev-dup","type":"cuenta.activada","data":{"empleadoId":"E1","email":"a@x.com"}}`
	procesarMensaje(entrega(r, cuerpo))
	procesarMensaje(entrega(r, cuerpo))
	if r.acks != 2 {
		t.Errorf("ambos mensajes deben confirmarse, acks=%d", r.acks)
	}
	if n := contar(t, "notificaciones"); n != 1 {
		t.Errorf("el duplicado no debe repetir el efecto: %d notificaciones", n)
	}
}

func TestMensajeConFormatoInvalidoSeDescartaConAck(t *testing.T) {
	prepararDB(t)
	r := &reconocedor{}
	procesarMensaje(entrega(r, `esto no es json`))
	if r.acks != 1 || contar(t, "notificaciones") != 0 {
		t.Errorf("mensaje inválido: acks=%d", r.acks)
	}
}

func TestEventoNoManejadoSeConfirmaSinGuardar(t *testing.T) {
	prepararDB(t)
	r := &reconocedor{}
	procesarMensaje(entrega(r, `{"id":"ev-x","type":"otro.evento","data":{}}`))
	if r.acks != 1 || contar(t, "notificaciones") != 0 {
		t.Errorf("evento no manejado: acks=%d", r.acks)
	}
}

func TestFalloDeBaseDeDatosDevuelveElMensajeALaCola(t *testing.T) {
	prepararDB(t)
	db.Close()
	r := &reconocedor{}
	procesarMensaje(entrega(r, `{"id":"ev-err","type":"cuenta.activada","data":{}}`))
	if r.nacks != 1 || r.acks != 0 {
		t.Errorf("debía hacer Nack para reintentar: acks=%d nacks=%d", r.acks, r.nacks)
	}
}

func TestTextoYEnlaceReset(t *testing.T) {
	if texto(map[string]interface{}{"a": "b"}, "a") != "b" || texto(map[string]interface{}{}, "a") != "" {
		t.Error("texto() devuelve un valor inesperado")
	}
	if enlaceReset("ZZ") != "https://app.empresa.com/reset?token=ZZ" {
		t.Error("enlaceReset() inesperado")
	}
}

func TestErroresDeEscrituraSeRegistranSinCaer(t *testing.T) {
	prepararDB(t)
	db.Close()
	// Con la BD cerrada ambas funciones solo registran el error en el log (no hacen panic).
	guardarNotificacion(db, Notificacion{ID: "x"})
	registrarEventoProcesado(db, "x")
}
