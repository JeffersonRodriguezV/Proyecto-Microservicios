const swaggerJsdoc = require('swagger-jsdoc');

const opciones = {
    definition: {
        openapi: '3.0.0',
        info: {
            title: 'Vacaciones Service API',
            version: '1.0.0',
            description: 'Gestión de períodos de vacaciones de empleados',
        },
        components: {
            securitySchemes: {
                // Reto 5: el JWT lo emite auth-service (POST /auth/login) y lo valida el API Gateway.
                BearerAuth: {
                    type: 'http',
                    scheme: 'bearer',
                    bearerFormat: 'JWT',
                    description: 'Pegue el accessToken devuelto por POST /auth/login (sin el prefijo "Bearer ").',
                },
            },
        },
        // Todos los endpoints requieren JWT, salvo los que declaran "security: []" (p. ej. /health).
        security: [{ BearerAuth: [] }],
    },
    apis: ['./src/server.js', './src/desarrollo.js'],
};

module.exports = swaggerJsdoc(opciones);