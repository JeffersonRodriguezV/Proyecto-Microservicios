const db = require('./db');

function crear({ empleadoId, fechaInicio, fechaFin }) {
    const fechaCreacion = new Date().toISOString();

    const insertar = db.prepare(`
    INSERT INTO vacaciones (empleado_id, fecha_inicio, fecha_fin, estado, fecha_creacion)
    VALUES (?, ?, ?, 'PROGRAMADA', ?)
  `);
    const resultado = insertar.run(empleadoId, fechaInicio, fechaFin, fechaCreacion);

    const numero = resultado.lastInsertRowid;
    const anio = new Date().getFullYear();
    const id = `V-${anio}-${String(numero).padStart(4, '0')}`;

    db.prepare('UPDATE vacaciones SET id = ? WHERE numero = ?').run(id, numero);

    return obtenerPorId(id);
}

function obtenerPorId(id) {
    const fila = db.prepare('SELECT * FROM vacaciones WHERE id = ?').get(id);
    return fila ? mapear(fila) : null;
}

function listarTodas() {
    const filas = db.prepare('SELECT * FROM vacaciones ORDER BY fecha_creacion DESC').all();
    return filas.map(mapear);
}

function listarPorEmpleado(empleadoId) {
    const filas = db.prepare('SELECT * FROM vacaciones WHERE empleado_id = ? ORDER BY fecha_creacion DESC').all(empleadoId);
    return filas.map(mapear);
}

// Busca un período PROGRAMADA o EN_CURSO del mismo empleado que se
// cruce con el rango solicitado. Dos rangos [a,b] y [c,d] se cruzan
// si a <= d y c <= b.
function buscarSolapamiento(empleadoId, fechaInicio, fechaFin) {
    const fila = db.prepare(`
    SELECT * FROM vacaciones
    WHERE empleado_id = ?
      AND estado IN ('PROGRAMADA', 'EN_CURSO')
      AND fecha_inicio <= ?
      AND fecha_fin >= ?
  `).get(empleadoId, fechaFin, fechaInicio);

    return fila ? mapear(fila) : null;
}

function cancelar(id) {
    db.prepare("UPDATE vacaciones SET estado = 'CANCELADA' WHERE id = ?").run(id);
    return obtenerPorId(id);
}

function mapear(fila) {
    return {
        id: fila.id,
        empleadoId: fila.empleado_id,
        fechaInicio: fila.fecha_inicio,
        fechaFin: fila.fecha_fin,
        estado: fila.estado,
        fechaCreacion: fila.fecha_creacion,
    };
}

module.exports = { crear, obtenerPorId, listarTodas, listarPorEmpleado, buscarSolapamiento, cancelar };