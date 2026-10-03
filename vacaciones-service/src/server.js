const express = require('express');
const repo = require('./vacacionesRepo');
const { obtenerEmpleado } = require('./empleadosClient');
const { publicar } = require('./eventoPublisher');
const { contarDiasHabiles } = require('./diasHabiles');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 8085;

function error(res, status, mensaje, extra = {}) {
    res.status(status).json({ status, mensaje, timestamp: new Date().toISOString(), ...extra });
}

app.get('/health', (req, res) => {
    res.status(200).json({ servicio: 'vacaciones-service', estado: 'activo' });
});

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

app.get('/vacaciones/:id', (req, res) => {
    const periodo = repo.obtenerPorId(req.params.id);
    if (!periodo) {
        return error(res, 404, `No existe un período con id ${req.params.id}`);
    }
    res.status(200).json(periodo);
});

app.get('/vacaciones', (req, res) => {
    const { empleadoId } = req.query;
    const resultado = empleadoId ? repo.listarPorEmpleado(empleadoId) : repo.listarTodas();
    res.status(200).json(resultado);
});

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