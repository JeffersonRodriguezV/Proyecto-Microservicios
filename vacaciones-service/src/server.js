const express = require('express');
const repo = require('./vacacionesRepo');
const { obtenerEmpleado } = require('./empleadosClient');
const { publicar } = require('./eventoPublisher');
const { contarDiasHabiles } = require('./diasHabiles');
const swaggerUi = require('swagger-ui-express');
const swaggerSpec = require('./swagger');
const app = express();
app.use(express.json());
app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(swaggerSpec));

const PORT = process.env.PORT || 8085;

function error(res, status, mensaje, extra = {}) {
    res.status(status).json({ status, mensaje, timestamp: new Date().toISOString(), ...extra });
}
/**
 * @openapi
 * /health:
 *   get:
 *     summary: Verifica que el servicio esté activo
 *     responses:
 *       200:
 *         description: El servicio está funcionando
 */
app.get('/health', (req, res) => {
    res.status(200).json({ servicio: 'vacaciones-service', estado: 'activo' });
});
/**
 * @openapi
 * /vacaciones:
 *   post:
 *     summary: Programa un período de vacaciones para un empleado
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [empleadoId, fechaInicio, fechaFin]
 *             properties:
 *               empleadoId:
 *                 type: string
 *               fechaInicio:
 *                 type: string
 *                 format: date
 *               fechaFin:
 *                 type: string
 *                 format: date
 *     responses:
 *       201:
 *         description: Período creado exitosamente
 *       400:
 *         description: Alguna de las 4 validaciones falló
 */
app.post('/vacaciones', async (req, res) => {
    const { empleadoId, fechaInicio, fechaFin } = req.body;

    if (!empleadoId || !fechaInicio || !fechaFin) {
        return error(res, 400, 'empleadoId, fechaInicio y fechaFin son obligatorios');
    }

    // Validación 1: fechas incoherentes
    if (new Date(fechaFin) <= new Date(fechaInicio)) {
        return error(res, 400, 'fechaFin debe ser posterior a fechaInicio');
    }

    // Validación 2: fechas en el pasado
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    if (new Date(fechaInicio) < hoy) {
        return error(res, 400, 'fechaInicio no puede ser una fecha pasada');
    }

    // Validación 3: solapamiento
    const conflicto = repo.buscarSolapamiento(empleadoId, fechaInicio, fechaFin);
    if (conflicto) {
        return error(res, 400, 'El empleado ya tiene un período que se cruza con el solicitado', {
            periodoConflicto: conflicto,
        });
    }

    // Validación 4: empleado inexistente
    const empleado = await obtenerEmpleado(empleadoId);
    if (!empleado) {
        return error(res, 400, `El empleado con id '${empleadoId}' no existe`);
    }

    const creado = repo.crear({ empleadoId, fechaInicio, fechaFin });

    publicar('vacaciones.programadas', {
        vacacionesId: creado.id,
        empleadoId,
        email: empleado.email,
        fechaInicio,
        fechaFin,
        diasHabiles: contarDiasHabiles(fechaInicio, fechaFin),
    });

    res.status(201).json(creado);
});
/**
 * @openapi
 * /vacaciones/{id}:
 *   get:
 *     summary: Consulta un período de vacaciones por su id
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: string
 *         example: V-2026-0042
 *     responses:
 *       200:
 *         description: Período encontrado
 *       404:
 *         description: No existe un período con ese id
 */
app.get('/vacaciones/:id', (req, res) => {
    const periodo = repo.obtenerPorId(req.params.id);
    if (!periodo) {
        return error(res, 404, `No existe un período con id ${req.params.id}`);
    }
    res.status(200).json(periodo);
});
/**
 * @openapi
 * /vacaciones:
 *   get:
 *     summary: Lista todos los períodos, o los de un empleado específico
 *     parameters:
 *       - in: query
 *         name: empleadoId
 *         required: false
 *         schema:
 *           type: string
 *         description: Si se incluye, filtra solo los períodos de ese empleado
 *     responses:
 *       200:
 *         description: Lista de períodos
 */
app.get('/vacaciones', (req, res) => {
    const { empleadoId } = req.query;
    const resultado = empleadoId ? repo.listarPorEmpleado(empleadoId) : repo.listarTodas();
    res.status(200).json(resultado);
});
/**
 * @openapi
 * /vacaciones/{id}:
 *   delete:
 *     summary: Cancela un período que aún no ha iniciado
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: string
 *         example: V-2026-0042
 *     responses:
 *       200:
 *         description: Período cancelado
 *       400:
 *         description: El período ya inició, finalizó, o ya estaba cancelado
 *       404:
 *         description: No existe un período con ese id
 */
app.delete('/vacaciones/:id', (req, res) => {
    const periodo = repo.obtenerPorId(req.params.id);
    if (!periodo) {
        return error(res, 404, `No existe un período con id ${req.params.id}`);
    }
    if (periodo.estado !== 'PROGRAMADA') {
        return error(res, 400, 'Solo se puede cancelar un período que aún no ha iniciado');
    }
    const cancelado = repo.cancelar(req.params.id);
    res.status(200).json(cancelado);
});

app.listen(PORT, () => {
    console.log(`vacaciones-service escuchando en el puerto ${PORT}`);
});