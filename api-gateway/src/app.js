/**
 * Aplicación Express del API Gateway (sin arrancar el servidor, para poder probarla).
 *
 * Enruta:
 *   /auth/*           -> auth-service:8086
 *   /empleados/*      -> empleados-service:8080
 *   /departamentos/*  -> departamentos-service:8081
 *   /perfiles/*       -> perfiles-service:8083
 *   /notificaciones/* -> notificaciones-service:8084
 *   /vacaciones/*     -> vacaciones-service:8085
 *
 * Reto 5: valida el JWT (401) y aplica RBAC + propiedad del recurso (403)
 * antes de enrutar. Si un destino no responde, devuelve 503 con un JSON descriptivo.
 */

const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const { autenticar, autorizar } = require('./security');
const { rutaSwagger } = require('./swagger');
const { middlewareMetrics, registrarRutas } = require('./observabilidad');

const app = express();
const PORT = process.env.PORT || 8080;

// URLs leídas desde variables de entorno (definidas en docker-compose.yml)
const AUTH_URL           = process.env.AUTH_URL           || 'http://localhost:8086';
const EMPLEADOS_URL      = process.env.EMPLEADOS_URL      || 'http://localhost:8080';
const DEPARTAMENTOS_URL  = process.env.DEPARTAMENTOS_URL  || 'http://localhost:8081';
const PERFILES_URL       = process.env.PERFILES_URL       || 'http://localhost:8083';
const NOTIFICACIONES_URL = process.env.NOTIFICACIONES_URL || 'http://localhost:8084';
const VACACIONES_URL     = process.env.VACACIONES_URL     || 'http://localhost:8085';

// Tiempo máximo de espera a un servicio de destino (configurable por entorno)
const TIMEOUT_PROXY_MS = Number(process.env.PROXY_TIMEOUT_MS) || 15000;

const destinos = { AUTH_URL, EMPLEADOS_URL, DEPARTAMENTOS_URL, PERFILES_URL, NOTIFICACIONES_URL, VACACIONES_URL };

/**
 * Crea un proxy hacia `target`, reenviando bajo el mismo `prefix`
 * con el que se montó, y devolviendo 503 uniforme si el destino
 * no responde.
 */
function crearProxy(target, prefix) {
    return createProxyMiddleware({
        target,
        changeOrigin: true,
        // Timeouts: un servicio colgado no debe retener al cliente indefinidamente (respuesta 503 uniforme)
        timeout: TIMEOUT_PROXY_MS,
        proxyTimeout: TIMEOUT_PROXY_MS,
        pathRewrite: (path) => {
            if (path === '/') return prefix;
            if (path.startsWith('/?')) return prefix + path.substring(1);
            return prefix + path;
        },
        on: {
            error: (err, req, res) => {
                res.writeHead(503, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({
                    status: 503,
                    mensaje: `El servicio en ${prefix} no está disponible`,
                    timestamp: new Date().toISOString(),
                }));
            },
        },
    });
}

// Observabilidad (Reto 8): cuenta y mide cada petición, escribe un log JSON y publica /metrics
// (público, lo consulta Prometheus sin JWT). Va antes de la seguridad para medir también los 401/403.
app.use(middlewareMetrics);
registrarRutas(app);

// Healthcheck del gateway (público, se declara antes de la seguridad).
// Formato común del proyecto: {status, service, checks}. El gateway no tiene
// base de datos ni broker propios, por eso `checks` va vacío y siempre responde 200.
app.get('/health', (req, res) => {
    res.status(200).json({ status: 'UP', service: 'api-gateway', checks: {} });
});

// Documentación OpenAPI agregada de todos los servicios (pública, solo lectura)
app.use('/swagger', rutaSwagger);

// Seguridad: todo lo que sigue exige JWT válido (salvo rutas públicas) y pasa por RBAC
app.use(autenticar);
app.use(autorizar);

app.use('/auth',           crearProxy(AUTH_URL,           '/auth'));
app.use('/empleados',      crearProxy(EMPLEADOS_URL,      '/empleados'));
app.use('/departamentos',  crearProxy(DEPARTAMENTOS_URL,  '/departamentos'));
app.use('/perfiles',       crearProxy(PERFILES_URL,       '/perfiles'));
app.use('/notificaciones', crearProxy(NOTIFICACIONES_URL, '/notificaciones'));
app.use('/vacaciones',     crearProxy(VACACIONES_URL,     '/vacaciones'));

module.exports = { app, crearProxy, PORT, destinos };
