package main

import (
	"encoding/json"
	"net/http"
	"strings"
)

func manejarNotificaciones(w http.ResponseWriter, r *http.Request) {
	// Si la ruta tiene algo después de /notificaciones/, es una
	// consulta por empleadoId; si no, se listan todas.
	ruta := strings.TrimPrefix(r.URL.Path, "/notificaciones")
	ruta = strings.Trim(ruta, "/")

	if ruta == "" {
		listarTodasLasNotificaciones(w, r)
		return
	}

	listarNotificacionesPorEmpleado(w, r, ruta)
}

func listarTodasLasNotificaciones(w http.ResponseWriter, r *http.Request) {
	filas, err := db.Query("SELECT id, tipo, destinatario, mensaje, fecha_envio, empleado_id FROM notificaciones ORDER BY fecha_envio DESC")
	if err != nil {
		http.Error(w, `{"status":500,"mensaje":"Error consultando notificaciones"}`, http.StatusInternalServerError)
		return
	}
	defer filas.Close()

	notificaciones := escanearNotificaciones(filas)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(notificaciones)
}

func listarNotificacionesPorEmpleado(w http.ResponseWriter, r *http.Request, empleadoID string) {
	filas, err := db.Query(
		"SELECT id, tipo, destinatario, mensaje, fecha_envio, empleado_id FROM notificaciones WHERE empleado_id = ? ORDER BY fecha_envio DESC",
		empleadoID,
	)
	if err != nil {
		http.Error(w, `{"status":500,"mensaje":"Error consultando notificaciones"}`, http.StatusInternalServerError)
		return
	}
	defer filas.Close()

	notificaciones := escanearNotificaciones(filas)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(notificaciones)
}

func escanearNotificaciones(filas interface {
	Next() bool
	Scan(...interface{}) error
}) []Notificacion {
	notificaciones := []Notificacion{}
	for filas.Next() {
		var n Notificacion
		filas.Scan(&n.ID, &n.Tipo, &n.Destinatario, &n.Mensaje, &n.FechaEnvio, &n.EmpleadoID)
		notificaciones = append(notificaciones, n)
	}
	return notificaciones
}