/**
 * Endpoint /health con el formato común del proyecto:
 *   { status: 'UP'|'DOWN', service, checks: { database, messageBroker } }
 * HTTP 200 si todo está UP y 503 si alguna dependencia está DOWN.
 *
 * Las verificaciones se inyectan para poder probar el handler sin base de datos ni broker reales.
 */
function crearHealthHandler({ servicio, verificarBaseDatos, verificarBroker, timeoutMs = 2000 }) {
    // Una dependencia que no responde a tiempo cuenta como DOWN (el healthcheck no debe colgarse).
    const conTimeout = (verificacion) =>
        Promise.race([
            Promise.resolve().then(verificacion).then(Boolean, () => false),
            new Promise((resolve) => setTimeout(() => resolve(false), timeoutMs).unref()),
        ]);

    return async (req, res) => {
        const [bd, broker] = await Promise.all([conTimeout(verificarBaseDatos), conTimeout(verificarBroker)]);
        const estado = (ok) => (ok ? 'UP' : 'DOWN');
        const arriba = bd && broker;
        res.status(arriba ? 200 : 503).json({
            status: estado(arriba),
            service: servicio,
            checks: { database: estado(bd), messageBroker: estado(broker) },
        });
    };
}

module.exports = { crearHealthHandler };
