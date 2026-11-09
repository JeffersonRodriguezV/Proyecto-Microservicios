const client = require('prom-client');
const winston = require('winston');
const { trace } = require('@opentelemetry/api');

client.collectDefaultMetrics(); // CPU y memoria del proceso

const peticiones = new client.Counter({
    name: 'http_requests_total',
    help: 'Peticiones HTTP',
    labelNames: ['method', 'status']
});

const duracion = new client.Histogram({
    name: 'http_request_duration_seconds',
    help: 'Latencia HTTP',
    labelNames: ['method', 'status']
});

const conTrace = winston.format((info) => {
    const span = trace.getActiveSpan();
    if (span) info.traceId = span.spanContext().traceId;
    return info;
});

const logger = winston.createLogger({
    level: 'info',
    format: winston.format.combine(
        conTrace(),
        winston.format.timestamp(),
        winston.format((i) => { i.service = 'api-gateway'; return i; })(),
        winston.format.json()
    ),
    transports: [new winston.transports.Console()],
});

// Rutas que Prometheus consulta cada pocos segundos: no se registran en el log para no inundarlo.
const RUTAS_SILENCIOSAS = new Set(['/metrics', '/health']);

function middlewareMetrics(req, res, next) {
    const inicio = process.hrtime.bigint();
    const fin = duracion.startTimer();
    res.on('finish', () => {
        const l = { method: req.method, status: String(res.statusCode) };
        peticiones.inc(l);
        fin(l);
        if (!RUTAS_SILENCIOSAS.has(req.path)) {
            logger.info('peticion', {
                method: req.method,
                path: req.originalUrl.split('?')[0],
                status: res.statusCode,
                duracionMs: Number(process.hrtime.bigint() - inicio) / 1e6,
            });
        }
    });
    next();
}

// Solo /metrics: el /health del gateway ya vive en app.js (formato común del proyecto).
function registrarRutas(app) {
    app.get('/metrics', async (_req, res) => {
        res.set('Content-Type', client.register.contentType);
        res.end(await client.register.metrics());
    });
}

module.exports = { logger, middlewareMetrics, registrarRutas };