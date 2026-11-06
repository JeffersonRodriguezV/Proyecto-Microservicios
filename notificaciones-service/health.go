package main

import (
	"context"
	"encoding/json"
	"net/http"
	"time"
)

// RespuestaHealth es el formato común de /health de todos los microservicios.
type RespuestaHealth struct {
	Status  string            `json:"status"`
	Service string            `json:"service"`
	Checks  map[string]string `json:"checks"`
}

func estado(ok bool) string {
	if ok {
		return "UP"
	}
	return "DOWN"
}

// manejarHealth responde 200 si la base de datos y el broker están UP, y 503 si alguno está DOWN.
func manejarHealth(w http.ResponseWriter, r *http.Request) {
	ctx, cancelar := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancelar()

	baseDatosOk := db != nil && db.PingContext(ctx) == nil
	brokerOk := brokerConectado.Load()

	respuesta := RespuestaHealth{
		Status:  estado(baseDatosOk && brokerOk),
		Service: "notificaciones-service",
		Checks: map[string]string{
			"database":      estado(baseDatosOk),
			"messageBroker": estado(brokerOk),
		},
	}

	w.Header().Set("Content-Type", "application/json")
	if respuesta.Status == "DOWN" {
		w.WriteHeader(http.StatusServiceUnavailable)
	}
	json.NewEncoder(w).Encode(respuesta)
}
