const { NodeSDK } = require('@opentelemetry/sdk-node');
const { ZipkinExporter } = require('@opentelemetry/exporter-zipkin');
const { HttpInstrumentation } = require('@opentelemetry/instrumentation-http');
const { ExpressInstrumentation } = require('@opentelemetry/instrumentation-express');

// El gateway solo recibe y reenvía HTTP: con estas dos instrumentaciones basta para abrir la traza
// y propagar el encabezado W3C `traceparent` hacia los servicios. (auto-instrumentations-node
// instalaría más de cien paquetes que aquí no se usan.)
const sdk = new NodeSDK({
    serviceName: process.env.OTEL_SERVICE_NAME || 'api-gateway',
    traceExporter: new ZipkinExporter({
        url: process.env.OTEL_EXPORTER_ZIPKIN_ENDPOINT || 'http://zipkin:9411/api/v2/spans',
    }),
    // La propagación W3C (tracecontext + baggage) viene por defecto.
    instrumentations: [new HttpInstrumentation(), new ExpressInstrumentation()],
});

sdk.start();
process.on('SIGTERM', () => sdk.shutdown());
