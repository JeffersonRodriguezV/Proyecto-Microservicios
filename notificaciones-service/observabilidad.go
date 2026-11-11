package main

// Observabilidad (Reto 8): métricas Prometheus, logs JSON con traceId y trazas OpenTelemetry (Zipkin).

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"strconv"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	amqp "github.com/rabbitmq/amqp091-go"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/exporters/zipkin"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
	"go.opentelemetry.io/otel/trace"
)

const nombreServicio = "notificaciones-service"

// Los nombres y etiquetas son los mismos que usan los demás servicios: así las reglas de
// grabación de Prometheus y el dashboard funcionan igual para los siete.
var (
	peticiones = prometheus.NewCounterVec(prometheus.CounterOpts{
		Name: "http_requests_total", Help: "Peticiones HTTP",
	}, []string{"method", "status"})
	duracion = prometheus.NewHistogramVec(prometheus.HistogramOpts{
		Name: "http_request_duration_seconds", Help: "Latencia HTTP", Buckets: prometheus.DefBuckets,
	}, []string{"method", "status"})
	registro = prometheus.NewRegistry()

	// Métricas de NEGOCIO: cuántas notificaciones generó el sistema y cuántas fallaron al procesarse.
	notificacionesEnviadas = prometheus.NewCounterVec(prometheus.CounterOpts{
		Name: "notificaciones_enviadas_total", Help: "Notificaciones generadas y guardadas, por tipo",
	}, []string{"tipo"})
	notificacionesErrores = prometheus.NewCounterVec(prometheus.CounterOpts{
		Name: "notificaciones_errores_total", Help: "Errores al procesar o guardar notificaciones, por causa",
	}, []string{"causa"})
)

func init() {
	// CPU y memoria del proceso (process_*) y del runtime de Go (go_*).
	registro.MustRegister(peticiones, duracion, notificacionesEnviadas, notificacionesErrores)
	registro.MustRegister(prometheus.NewProcessCollector(prometheus.ProcessCollectorOpts{}))
	registro.MustRegister(prometheus.NewGoCollector())
}

// ---------------------------------------------------------------- logs JSON con traceId

// manejadorTraza decora el handler JSON de slog: si el contexto lleva un span activo agrega `traceId`.
type manejadorTraza struct{ slog.Handler }

func (m manejadorTraza) Handle(ctx context.Context, r slog.Record) error {
	if sc := trace.SpanContextFromContext(ctx); sc.IsValid() {
		r.AddAttrs(slog.String("traceId", sc.TraceID().String()))
	}
	return m.Handler.Handle(ctx, r)
}

func (m manejadorTraza) WithAttrs(a []slog.Attr) slog.Handler {
	return manejadorTraza{m.Handler.WithAttrs(a)}
}
func (m manejadorTraza) WithGroup(n string) slog.Handler {
	return manejadorTraza{m.Handler.WithGroup(n)}
}

// configurarLogs deja slog como logger por defecto. Al hacerlo, las llamadas existentes a log.Println/log.Printf
// también salen como JSON (campo "msg"), sin tener que reescribirlas todas.
func configurarLogs() {
	base := slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		ReplaceAttr: func(_ []string, a slog.Attr) slog.Attr {
			if a.Key == slog.TimeKey {
				a.Key = "timestamp"
			}
			return a
		},
	})
	slog.SetDefault(slog.New(manejadorTraza{base}).With("service", nombreServicio))
}

// ---------------------------------------------------------------- trazas OpenTelemetry

// iniciarTrazas configura el exportador Zipkin y la propagación W3C. Devuelve la función de cierre
// (vacía el lote pendiente). Si el exportador no se puede crear, el servicio sigue sin trazas.
func iniciarTrazas() func(context.Context) error {
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(propagation.TraceContext{}, propagation.Baggage{}))

	endpoint := obtenerEnv("OTEL_EXPORTER_ZIPKIN_ENDPOINT", "http://zipkin:9411/api/v2/spans")
	exportador, err := zipkin.New(endpoint)
	if err != nil {
		slog.Error("no se pudo crear el exportador Zipkin; el servicio sigue sin trazas", "error", err)
		return func(context.Context) error { return nil }
	}
	proveedor := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exportador, sdktrace.WithBatchTimeout(2*time.Second)),
		sdktrace.WithResource(resource.NewSchemaless(semconv.ServiceName(obtenerEnv("OTEL_SERVICE_NAME", nombreServicio)))),
	)
	otel.SetTracerProvider(proveedor)
	return proveedor.Shutdown
}

func tracer() trace.Tracer { return otel.Tracer(nombreServicio) }

// ---------------------------------------------------------------- middleware HTTP

type registradorEstado struct {
	http.ResponseWriter
	codigo int
}

func (r *registradorEstado) WriteHeader(c int) { r.codigo = c; r.ResponseWriter.WriteHeader(c) }

// Rutas que Prometheus y el healthcheck de Docker consultan cada pocos segundos: ni trazas ni log.
func esRutaSilenciosa(ruta string) bool { return ruta == "/health" || ruta == "/metrics" }

// middlewareObservabilidad cuenta y mide cada petición, abre un span (continuando el `traceparent` entrante,
// si existe) y escribe una línea de log JSON con el traceId.
func middlewareObservabilidad(siguiente http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		inicio := time.Now()
		rec := &registradorEstado{ResponseWriter: w, codigo: http.StatusOK}
		ctx := r.Context()

		var span trace.Span
		if !esRutaSilenciosa(r.URL.Path) {
			ctx = otel.GetTextMapPropagator().Extract(ctx, propagation.HeaderCarrier(r.Header))
			ctx, span = tracer().Start(ctx, r.Method+" "+r.URL.Path, trace.WithSpanKind(trace.SpanKindServer))
		}

		siguiente.ServeHTTP(rec, r.WithContext(ctx))

		etiquetas := prometheus.Labels{"method": r.Method, "status": strconv.Itoa(rec.codigo)}
		peticiones.With(etiquetas).Inc()
		duracion.With(etiquetas).Observe(time.Since(inicio).Seconds())

		if span != nil {
			span.SetAttributes(attribute.Int("http.status_code", rec.codigo))
			if rec.codigo >= 500 {
				span.SetStatus(codes.Error, fmt.Sprintf("HTTP %d", rec.codigo))
			}
			span.End()
			slog.InfoContext(ctx, "peticion", "method", r.Method, "path", r.URL.Path,
				"status", rec.codigo, "duracionMs", float64(time.Since(inicio).Microseconds())/1000)
		}
	})
}

func manejarMetrics(w http.ResponseWriter, r *http.Request) {
	promhttp.HandlerFor(registro, promhttp.HandlerOpts{}).ServeHTTP(w, r)
}

// ---------------------------------------------------------------- propagación por el broker

// cabecerasAmqp adapta amqp.Table (valores interface{}) a la interfaz TextMapCarrier de OpenTelemetry.
type cabecerasAmqp amqp.Table

func (c cabecerasAmqp) Get(clave string) string {
	if v, ok := c[clave].(string); ok {
		return v
	}
	return ""
}
func (c cabecerasAmqp) Set(clave, valor string) { c[clave] = valor }
func (c cabecerasAmqp) Keys() []string {
	claves := make([]string, 0, len(c))
	for k := range c {
		claves = append(claves, k)
	}
	return claves
}

// contextoDelMensaje extrae el `traceparent` de las cabeceras del mensaje: el span que se cree a partir de este
// contexto cuelga de la traza original (la del scheduler / auth), en vez de abrir una traza nueva.
func contextoDelMensaje(msg amqp.Delivery) context.Context {
	return otel.GetTextMapPropagator().Extract(context.Background(), cabecerasAmqp(msg.Headers))
}
