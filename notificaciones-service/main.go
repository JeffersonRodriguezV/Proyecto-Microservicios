package main

import (
	"encoding/json"
	"log"
	"net/http"
)

func main() {
	inicializarDB()

	go iniciarConsumidor()

	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]string{
			"servicio": "notificaciones-service",
			"estado":   "activo",
		})
	})

	http.HandleFunc("/notificaciones", manejarNotificaciones)
	http.HandleFunc("/notificaciones/", manejarNotificaciones)

	log.Println("notificaciones-service escuchando en el puerto 8084")
	log.Fatal(http.ListenAndServe(":8084", nil))
}