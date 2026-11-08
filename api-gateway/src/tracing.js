const { NodeSDK } = require('@opentelemetry/sdk-node');
const { ZipkinExporter } = require('@opentelemetry/exporter-zipkin');
const { getNodeAutoInstrumentations } = require('@opentelemetry/auto-instrumentations-node');

const sdk = new NodeSDK({
    serviceName: process.env.OTEL_SERVICE_NAME || 'api-gateway',
    traceExporter: new ZipkinExporter({
        url: process.env.OTEL_EXPORTER_ZIPKIN_ENDPOINT || 'http://zipkin:9411/api/v2/spans',
    }),
    // La propagación W3C (tracecontext + baggage) viene por defecto.
    instrumentations: [getNodeAutoInstrumentations()],
});

sdk.start();
process.on('SIGTERM', () => sdk.shutdown());