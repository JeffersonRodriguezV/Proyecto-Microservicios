/**
 * Punto de entrada del API Gateway.
 *
 * Enruta:
 *   /empleados/*      -> empleados-service:8080
 *   /departamentos/*  -> departamentos-service:8081
 *   /perfiles/*       -> perfiles-service:8083
 *   /notificaciones/* -> notificaciones-service:8084
 *   /vacaciones/*     -> vacaciones-service:8085
 *
 * Si un destino no responde, devuelve 503 con un JSON descriptivo.
 */

const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');

const app = express();
const PORT = process.env.PORT || 8080;

// URLs leídas desde variables de entorno (definidas en docker-compose.yml)
const EMPLEADOS_URL      = process.env.EMPLEADOS_URL      || 'http://localhost:8080';
const DEPARTAMENTOS_URL  = process.env.DEPARTAMENTOS_URL  || 'http://localhost:8081';
const PERFILES_URL       = process.env.PERFILES_URL       || 'http://localhost:8083';
const NOTIFICACIONES_URL = process.env.NOTIFICACIONES_URL || 'http://localhost:8084';
const VACACIONES_URL     = process.env.VACACIONES_URL     || 'http://localhost:8085';

/**
 * Crea un proxy hacia `target`, reenviando bajo el mismo `prefix`
 * con el que se montó, y devolviendo 503 uniforme si el destino
 * no responde.
 */
function crearProxy(target, prefix) {
    return createProxyMiddleware({
        target,
        changeOrigin: true,
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

// Healthcheck del gateway
app.get('/health', (req, res) => {
    res.status(200).json({ servicio: 'api-gateway', estado: 'activo' });
});

app.use('/empleados',     crearProxy(EMPLEADOS_URL,     '/empleados'));
app.use('/departamentos', crearProxy(DEPARTAMENTOS_URL, '/departamentos'));
app.use('/perfiles',       crearProxy(PERFILES_URL,       '/perfiles'));
app.use('/notificaciones', crearProxy(NOTIFICACIONES_URL, '/notificaciones'));
app.use('/vacaciones',     crearProxy(VACACIONES_URL,     '/vacaciones'));

app.listen(PORT, () => {
    console.log(`API Gateway escuchando en el puerto ${PORT}`);
    console.log(`  /empleados/*      -> ${EMPLEADOS_URL}`);
    console.log(`  /departamentos/*  -> ${DEPARTAMENTOS_URL}`);
    console.log(`  /perfiles/*       -> ${PERFILES_URL}`);
    console.log(`  /notificaciones/* -> ${NOTIFICACIONES_URL}`);
    console.log(`  /vacaciones/*     -> ${VACACIONES_URL}`);
});