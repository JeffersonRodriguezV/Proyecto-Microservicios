package main

import (
	"log"
	"net/http"
)

// construirMux registra todas las rutas del servicio. Está separado de main()
// para poder probar los endpoints sin abrir un puerto real.
func construirMux() *http.ServeMux {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", manejarHealth)
	mux.HandleFunc("/notificaciones", manejarNotificaciones)
	mux.HandleFunc("/notificaciones/", manejarNotificaciones)
	mux.HandleFunc("/notificaciones/docs", servirSwaggerUI)
	mux.HandleFunc("/notificaciones/openapi.json", servirOpenAPI)
	return mux
}

func main() {
	inicializarDB()

	go iniciarConsumidor()

	log.Println("notificaciones-service escuchando en el puerto 8084")
	log.Fatal(http.ListenAndServe(":8084", construirMux()))
}
