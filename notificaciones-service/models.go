package main

// Notificacion representa una fila de la tabla notificaciones,
// con el mismo formato que pide el reto.
type Notificacion struct {
	ID           string `json:"id"`
	Tipo         string `json:"tipo"`
	Destinatario string `json:"destinatario"`
	Mensaje      string `json:"mensaje"`
	FechaEnvio   string `json:"fechaEnvio"`
	EmpleadoID   string `json:"empleadoId"`
}

// EventoEnvelope es el sobre técnico del Catálogo de Eventos:
// {id, type, version, occurredAt, producer, data}
type EventoEnvelope struct {
	ID         string                 `json:"id"`
	Type       string                 `json:"type"`
	Version    int                    `json:"version"`
	OccurredAt string                 `json:"occurredAt"`
	Producer   string                 `json:"producer"`
	Data       map[string]interface{} `json:"data"`
}