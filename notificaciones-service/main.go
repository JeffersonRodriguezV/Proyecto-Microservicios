package main

import (
	"context"
	"log"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

// construirMux registra todas las rutas del servicio. Está separado de main()
// para poder probar los endpoints sin abrir un puerto real.
func construirMux() *http.ServeMux {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", manejarHealth)
	mux.HandleFunc("/metrics", manejarMetrics)
	mux.HandleFunc("/notificaciones", manejarNotificaciones)
	mux.HandleFunc("/notificaciones/", manejarNotificaciones)
	mux.HandleFunc("/notificaciones/docs", servirSwaggerUI)
	mux.HandleFunc("/notificaciones/openapi.json", servirOpenAPI)
	return mux
}

func main() {
	configurarLogs()
	cerrarTrazas := iniciarTrazas()
	inicializarDB()

	go iniciarConsumidor()

	servidor := &http.Server{Addr: ":8084", Handler: middlewareObservabilidad(construirMux())}

	// Al recibir SIGTERM (docker stop) se vacía el lote de trazas pendiente antes de salir.
	go func() {
		senal := make(chan os.Signal, 1)
		signal.Notify(senal, syscall.SIGTERM, os.Interrupt)
		<-senal
		ctx, cancelar := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancelar()
		cerrarTrazas(ctx)
		servidor.Shutdown(ctx)
	}()

	slog.Info("notificaciones-service escuchando", "puerto", 8084)
	if err := servidor.ListenAndServe(); err != http.ErrServerClosed {
		log.Fatal(err)
	}
}
