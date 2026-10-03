const swaggerJsdoc = require('swagger-jsdoc');

const opciones = {
    definition: {
        openapi: '3.0.0',
        info: {
            title: 'Vacaciones Service API',
            version: '1.0.0',
            description: 'Gestión de períodos de vacaciones de empleados',
        },
    },
    apis: ['./src/server.js'],
};

module.exports = swaggerJsdoc(opciones);