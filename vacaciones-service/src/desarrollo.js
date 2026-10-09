const express = require('express');
const repo = require('./vacacionesRepo');
const scheduler = require('./scheduler');

// Endpoints SOLO PARA DESARROLLO (Reto 5, §3.3, estrategia 3): disparan manualmente lo que haría el scheduler,
// para demostrar el flujo completo sin esperar a la fecha. Quedan apagados salvo que DEV_ENDPOINTS=true.
// La restricción a ADMIN la aplica el API Gateway: son POST, y el rol USER solo puede leer.
const habilitado = process.env.DEV_ENDPOINTS === 'true';
const router = express.Router();

function error(res, status, mensaje) {
    res.status(status).json({ status, mensaje, timestamp: new Date().toISOString() });
}

/**
 * @openapi
 * /vacaciones/{id}/forzar-inicio:
 *   post:
 *     summary: "[SOLO DESARROLLO] Fuerza el inicio de un período (equivale a que el scheduler lo detecte)"
 *     description: Requiere DEV_ENDPOINTS=true y rol ADMIN. Pasa PROGRAMADA a EN_CURSO y publica vacaciones.iniciadas.
 *     security:
 *       - BearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: string
 *         example: V-2026-0042
 *     responses:
 *       200:
 *         description: Período iniciado
 *       400:
 *         description: El período no está en estado PROGRAMADA
 *       404:
 *         description: No existe un período con ese id
 */
router.post('/vacaciones/:id/forzar-inicio', async (req, res) => {
    const periodo = repo.listarTodas().find((p) => p.id === req.params.id);
    if (!periodo) return error(res, 404, `No existe un período con id ${req.params.id}`);
    if (periodo.estado !== 'PROGRAMADA') {
        return error(res, 400, `Solo se puede forzar el inicio de un período PROGRAMADA (estado actual: ${periodo.estado})`);
    }
    const completo = repo.listarParaIniciar('9999-12-31').find((p) => p.id === periodo.id);
    await scheduler.iniciarPeriodo(completo);
    res.status(200).json(repo.obtenerPorId(periodo.id));
});

/**
 * @openapi
 * /vacaciones/{id}/forzar-fin:
 *   post:
 *     summary: "[SOLO DESARROLLO] Fuerza el fin de un período (equivale a que el scheduler lo detecte)"
 *     description: Requiere DEV_ENDPOINTS=true y rol ADMIN. Pasa EN_CURSO a FINALIZADA y publica vacaciones.finalizadas.
 *     security:
 *       - BearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: string
 *         example: V-2026-0042
 *     responses:
 *       200:
 *         description: Período finalizado
 *       400:
 *         description: El período no está en estado EN_CURSO
 *       404:
 *         description: No existe un período con ese id
 */
router.post('/vacaciones/:id/forzar-fin', async (req, res) => {
    const periodo = repo.listarTodas().find((p) => p.id === req.params.id);
    if (!periodo) return error(res, 404, `No existe un período con id ${req.params.id}`);
    if (periodo.estado !== 'EN_CURSO') {
        return error(res, 400, `Solo se puede forzar el fin de un período EN_CURSO (estado actual: ${periodo.estado})`);
    }
    const completo = repo.listarParaFinalizar('9999-12-31').find((p) => p.id === periodo.id);
    await scheduler.finalizarPeriodo(completo);
    res.status(200).json(repo.obtenerPorId(periodo.id));
});

module.exports = { router, habilitado };