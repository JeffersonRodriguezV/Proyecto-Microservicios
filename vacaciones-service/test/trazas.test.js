// Caso difícil del Reto 8: el scheduler abre un span RAÍZ `scheduler.vacaciones.iniciar` y el mensaje que sale
// al broker lleva `traceparent` con ESE mismo traceId. Se usa el publicador real con un amqplib falso y el SDK de
// OpenTelemetry con un procesador en memoria (sin red, sin Zipkin).
process.env.DB_PATH = ':memory:';
process.env.RABBITMQ_PASSWORD = 'test';

const test = require('node:test');
const assert = require('node:assert/strict');

const publicados = [];
const rutaAmqp = require.resolve('amqplib');
require.cache[rutaAmqp] = {
    id: rutaAmqp, filename: rutaAmqp, loaded: true,
    exports: {
        connect: async () => ({
            on() {},
            createChannel: async () => ({
                assertExchange: async () => {},
                publish: (exchange, clave, contenido, opciones) => publicados.push({ exchange, clave, contenido, opciones }),
            }),
        }),
    },
};

const { NodeSDK } = require('@opentelemetry/sdk-node');
const terminados = [];
const sdk = new NodeSDK({
    serviceName: 'vacaciones-service',
    spanProcessors: [{
        onStart() {}, onEnd: (span) => terminados.push(span),
        forceFlush: async () => {}, shutdown: async () => {},
    }],
});
sdk.start();

const repo = require('../src/vacacionesRepo');
const scheduler = require('../src/scheduler');

test.after(() => sdk.shutdown());

test('iniciarPeriodo crea el span raíz y el mensaje lleva el mismo traceparent', async () => {
    const v = repo.crear({ empleadoId: 'T-1', email: 't@x.com', fechaInicio: '2000-01-01', fechaFin: '2000-01-05' });
    assert.equal(await scheduler.iniciarPeriodo(repo.obtenerPorId(v.id)), true);

    const span = terminados.find((s) => s.name === 'scheduler.vacaciones.iniciar');
    assert.ok(span, 'debe existir el span manual');
    assert.equal(span.parentSpanContext, undefined, 'es un span raíz');

    const mensaje = publicados.find((p) => p.clave === 'vacaciones.iniciadas');
    assert.ok(mensaje, 'se publicó vacaciones.iniciadas');
    const traceparent = mensaje.opciones.headers.traceparent;
    assert.match(traceparent, /^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$/);
    assert.equal(traceparent.split('-')[1], span.spanContext().traceId);
    assert.equal(traceparent.split('-')[2], span.spanContext().spanId);
});
