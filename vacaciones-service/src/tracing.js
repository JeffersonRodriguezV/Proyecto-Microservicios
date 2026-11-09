const { NodeSDK } = require('@opentelemetry/sdk-node');
const { ZipkinExporter } = require('@opentelemetry/exporter-zipkin');
const { HttpInstrumentation } = require('@opentelemetry/instrumentation-http');
const { ExpressInstrumentation } = require('@opentelemetry/instrumentation-express');

// Express + HTTP abren la traza de cada petición entrante y propagan el W3C `traceparent` en las salientes
// (p. ej. la consulta a empleados-service). La publicación al broker se instrumenta a mano (eventoPublisher).
// (auto-instrumentations-node instalaría más de cien paquetes que aquí no se usan.)
const sdk = new NodeSDK({
    serviceName: process.env.OTEL_SERVICE_NAME || 'vacaciones-service',
    traceExporter: new ZipkinExporter({
        url: process.env.OTEL_EXPORTER_ZIPKIN_ENDPOINT || 'http://zipkin:9411/api/v2/spans',
    }),
    // La propagación W3C (tracecontext + baggage) viene por defecto.
    instrumentations: [new HttpInstrumentation({
            // El healthcheck de Docker y Prometheus consultan cada pocos segundos: no generan trazas.
            ignoreIncomingRequestHook: (req) => ['/health', '/metrics'].includes((req.url || '').split('?')[0]),
        }), new ExpressInstrumentation()],
});

sdk.start();
process.on('SIGTERM', () => sdk.shutdown());
