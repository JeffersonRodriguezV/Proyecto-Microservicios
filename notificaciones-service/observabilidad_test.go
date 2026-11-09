package main

import (
	"context"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"
)

// proveedorEnMemoria instala un TracerProvider que guarda los spans terminados (sin red ni Zipkin).
func proveedorEnMemoria(t *testing.T) *tracetest.SpanRecorder {
	t.Helper()
	grabador := tracetest.NewSpanRecorder()
	anterior := otel.GetTracerProvider()
	otel.SetTracerProvider(sdktrace.NewTracerProvider(sdktrace.WithSpanProcessor(grabador)))
	otel.SetTextMapPropagator(propagation.TraceContext{})
	t.Cleanup(func() { otel.SetTracerProvider(anterior) })
	return grabador
}

func TestMetricsExponeMetricasDelProcesoYPeticiones(t *testing.T) {
	prepararDB(t)
	manejador := middlewareObservabilidad(construirMux())
	manejador.ServeHTTP(httptest.NewRecorder(), httptest.NewRequest(http.MethodGet, "/notificaciones", nil))

	rec := httptest.NewRecorder()
	manejador.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/metrics", nil))
	cuerpo := rec.Body.String()
	if rec.Code != http.StatusOK {
		t.Fatalf("/metrics respondió %d", rec.Code)
	}
	for _, esperado := range []string{"process_cpu_seconds_total", "process_resident_memory_bytes",
		`http_requests_total{method="GET",status="200"}`, "http_request_duration_seconds_bucket"} {
		if !strings.Contains(cuerpo, esperado) {
			t.Errorf("falta %q en /metrics", esperado)
		}
	}
}

func TestProcesarMensajeContinuaLaTrazaDelTraceparent(t *testing.T) {
	prepararDB(t)
	grabador := proveedorEnMemoria(t)
	const traceID = "4bf92f3577b34da6a3ce929d0e0e4736"

	r := &reconocedor{}
	msg := entrega(r, `{"id":"ev-trz","type":"cuenta.activada","data":{"empleadoId":"E1","email":"a@x.com"}}`)
	msg.Headers = amqp.Table{"traceparent": "00-" + traceID + "-00f067aa0ba902b7-01"}
	procesarMensaje(msg)

	spans := grabador.Ended()
	if len(spans) != 1 {
		t.Fatalf("se esperaba 1 span, hubo %d", len(spans))
	}
	if got := spans[0].SpanContext().TraceID().String(); got != traceID {
		t.Errorf("el span debe pertenecer a la traza original: %s != %s", got, traceID)
	}
	if got := spans[0].Parent().SpanID().String(); got != "00f067aa0ba902b7" {
		t.Errorf("el padre debe ser el span del productor, fue %s", got)
	}
}

func TestMiddlewareContinuaTrazaHTTPYOmiteHealth(t *testing.T) {
	prepararDB(t)
	grabador := proveedorEnMemoria(t)
	manejador := middlewareObservabilidad(construirMux())

	req := httptest.NewRequest(http.MethodGet, "/notificaciones", nil)
	req.Header.Set("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
	manejador.ServeHTTP(httptest.NewRecorder(), req)
	manejador.ServeHTTP(httptest.NewRecorder(), httptest.NewRequest(http.MethodGet, "/health", nil))

	spans := grabador.Ended()
	if len(spans) != 1 {
		t.Fatalf("solo /notificaciones debe generar span; hubo %d", len(spans))
	}
	if spans[0].SpanContext().TraceID().String() != "4bf92f3577b34da6a3ce929d0e0e4736" {
		t.Error("el span HTTP debe continuar el traceparent entrante")
	}
}

func TestCabecerasAmqp(t *testing.T) {
	c := cabecerasAmqp(amqp.Table{"traceparent": "x", "otra": 5})
	if c.Get("traceparent") != "x" || c.Get("otra") != "" || c.Get("nada") != "" {
		t.Error("Get debe devolver solo valores string")
	}
	c.Set("k", "v")
	if len(c.Keys()) != 3 {
		t.Errorf("Keys inesperadas: %v", c.Keys())
	}
}

func TestConfigurarLogsEscribeJSONConTraceId(t *testing.T) {
	var salida strings.Builder
	grabador := proveedorEnMemoria(t)
	_ = grabador
	manejador := manejadorTraza{slog.NewJSONHandler(&salida, nil)}
	ctx, span := tracer().Start(context.Background(), "prueba")
	defer span.End()
	slog.New(manejador).With("a", 1).WithGroup("g").InfoContext(ctx, "hola")
	if !strings.Contains(salida.String(), `"traceId":"`+span.SpanContext().TraceID().String()+`"`) {
		t.Errorf("el log debe incluir traceId: %s", salida.String())
	}
	configurarLogs() // no debe fallar
}

func TestIniciarTrazasDevuelveCierre(t *testing.T) {
	t.Setenv("OTEL_EXPORTER_ZIPKIN_ENDPOINT", "http://127.0.0.1:1/api/v2/spans")
	cerrar := iniciarTrazas()
	ctx, cancelar := context.WithTimeout(context.Background(), time.Second)
	defer cancelar()
	_ = cerrar(ctx)
}

func TestMiddlewareMarcaErrorEnSpanDe5xx(t *testing.T) {
	grabador := proveedorEnMemoria(t)
	h := middlewareObservabilidad(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	h.ServeHTTP(httptest.NewRecorder(), httptest.NewRequest(http.MethodGet, "/x", nil))
	if s := grabador.Ended(); len(s) != 1 || s[0].Status().Code.String() != "Error" {
		t.Error("un 5xx debe marcar el span como Error")
	}
}
