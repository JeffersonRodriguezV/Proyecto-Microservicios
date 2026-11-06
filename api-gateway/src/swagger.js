/**
 * Swagger UI agregado del Gateway: una sola página (/swagger) con selector para ver la
 * especificación OpenAPI de cada microservicio. Cada servicio publica su propio documento
 * bajo su prefijo, así que el Gateway solo los enlaza (no los duplica ni los copia).
 *
 * Las rutas de documentación (GET) son públicas en security.js; para probar endpoints
 * protegidos use el botón Authorize con el accessToken de POST /auth/login.
 */
const express = require('express');
const swaggerUi = require('swagger-ui-express');

const especificaciones = [
    { name: 'auth-service',           url: '/auth/api-docs' },
    { name: 'empleados-service',      url: '/empleados/api-docs' },
    { name: 'departamentos-service',  url: '/departamentos/openapi.json' },
    { name: 'perfiles-service',       url: '/perfiles/swagger/v1/swagger.json' },
    { name: 'notificaciones-service', url: '/notificaciones/openapi.json' },
    { name: 'vacaciones-service',     url: '/vacaciones/api-docs.json' },
];

const opciones = {
    customSiteTitle: 'API Gateway - Documentación',
    swaggerOptions: { urls: especificaciones, 'urls.primaryName': 'empleados-service' },
};

const router = express.Router();
// Solo lectura: cualquier otro método sale de este router y cae en la seguridad (401/403).
router.use((req, res, next) => (['GET', 'HEAD'].includes(req.method) ? next() : next('router')));
router.use(swaggerUi.serve);                       // archivos estáticos de Swagger UI
router.get('/', swaggerUi.setup(null, opciones)); // página sin spec propia: usa el selector de `urls`

module.exports = { rutaSwagger: router, especificaciones };
