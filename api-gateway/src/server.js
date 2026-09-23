/**
 * Punto de entrada del API Gateway.
 *
 * Enruta /empleados/* y /departamentos/* hacia sus microservicios,
 * y responde 503 con un cuerpo JSON descriptivo si el destino no
 * responde (en vez de dejar que se propague un error crudo de Node
 * o que el request se quede colgado indefinidamente).
 */

const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');

const app = express();
const PORT = process.env.PORT || 3000;

const EMPLEADOS_URL = process.env.EMPLEADOS_URL || 'http://localhost:8080';
const DEPARTAMENTOS_URL = process.env.DEPARTAMENTOS_URL || 'http://localhost:8081';

/**
 * Crea un proxy hacia `target`, reenviando bajo el mismo `prefix`
 * con el que se montó (ver nota sobre pathRewrite y barras finales
 * en el historial del Paso 2), y respondiendo 503 uniforme si el
 * destino no responde.
 */
function crearProxy(target, prefix) {
    return createProxyMiddleware({
        target,
        changeOrigin: true,
        pathRewrite: (path) => `${prefix}${path === '/' ? '' : path}`,
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

app.get('/health', (req, res) => {
    res.status(200).json({ servicio: 'api-gateway', estado: 'activo' });
});

app.use('/empleados', crearProxy(EMPLEADOS_URL, '/empleados'));
app.use('/departamentos', crearProxy(DEPARTAMENTOS_URL, '/departamentos'));

app.listen(PORT, () => {
    console.log(`API Gateway escuchando en el puerto ${PORT}`);
    console.log(`  /empleados/*      -> ${EMPLEADOS_URL}`);
    console.log(`  /departamentos/*  -> ${DEPARTAMENTOS_URL}`);
});