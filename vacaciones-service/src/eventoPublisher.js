const amqp = require('amqplib');
const crypto = require('crypto');

// Credenciales y ubicación de RabbitMQ, todas con valores por
// defecto para pruebas locales (admin/admin, localhost). En el
// docker-compose final, Persona 3 las sobreescribirá para apuntar
// al broker real dentro de la red de Docker.
const RABBITMQ_HOST = process.env.RABBITMQ_HOST || 'localhost';
const RABBITMQ_PORT = process.env.RABBITMQ_PORT || '5672';
const RABBITMQ_USERNAME = process.env.RABBITMQ_USERNAME || 'admin';
const RABBITMQ_PASSWORD = process.env.RABBITMQ_PASSWORD || 'admin';

// Mismo exchange compartido por todo el ecosistema
const EXCHANGE = process.env.EVENTOS_EXCHANGE || 'ecosistema.eventos';
const PRODUCER = 'vacaciones-service';

// Se reutiliza el mismo canal entre llamadas en vez de abrir una
// conexión nueva cada vez que se publica un evento -- más eficiente,
// y evita agotar conexiones si se programan muchas vacaciones seguidas.
let canalCache = null;

async function obtenerCanal() {
    if (canalCache) return canalCache;

    const url = `amqp://${RABBITMQ_USERNAME}:${RABBITMQ_PASSWORD}@${RABBITMQ_HOST}:${RABBITMQ_PORT}`;
    const conexion = await amqp.connect(url);
    const canal = await conexion.createChannel();
   //Lo crea si no existe, y lo marca como durable para que sobreviva reinicios del broker
    await canal.assertExchange(EXCHANGE, 'topic', { durable: true });

    canalCache = canal;
    return canal;
}

/**
 * Publica un evento con el envelope del Catálogo. Si falla, se
 * registra el error pero no se propaga (no debe revertir la
 * operación que ya se persistió).
 */
async function publicar(type, data) {
    try {
        const canal = await obtenerCanal();

        const envelope = {
            id: crypto.randomUUID(),
            type,
            version: 1,
            // Se trunca a segundos (sin milisegundos) para que
            // con el formato exacto del ejemplo del catálogo.
            occurredAt: new Date().toISOString().split('.')[0] + 'Z',
            producer: PRODUCER,
            data,
        };

        canal.publish(EXCHANGE, type, Buffer.from(JSON.stringify(envelope)));
    } catch (error) {
        console.error(`No se pudo publicar el evento '${type}':`, error.message);
    }
}

module.exports = { publicar };