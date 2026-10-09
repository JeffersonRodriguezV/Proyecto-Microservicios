const db = require('./db');

function crear({ empleadoId, email, fechaInicio, fechaFin }) {
    const fechaCreacion = new Date().toISOString();

    const insertar = db.prepare(`
        INSERT INTO vacaciones (empleado_id, fecha_inicio, fecha_fin, estado, fecha_creacion, email)
        VALUES (?, ?, ?, 'PROGRAMADA', ?, ?)
    `);
    const resultado = insertar.run(empleadoId, fechaInicio, fechaFin, fechaCreacion, email ?? null);

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

// ---- Soporte del scheduler (Reto 5) ----

// Períodos PROGRAMADA cuya fechaInicio ya llegó (hoy o antes: así se recupera lo pendiente si el servicio estuvo caído).
function listarParaIniciar(hoy) {
    const filas = db.prepare("SELECT * FROM vacaciones WHERE estado = 'PROGRAMADA'").all();
    return filas.filter((f) => f.fecha_inicio.slice(0, 10) <= hoy).map(mapearConEmail);
}

// Períodos EN_CURSO cuya fechaFin ya pasó (estrictamente anterior a hoy).
function listarParaFinalizar(hoy) {
    const filas = db.prepare("SELECT * FROM vacaciones WHERE estado = 'EN_CURSO'").all();
    return filas.filter((f) => f.fecha_fin.slice(0, 10) < hoy).map(mapearConEmail);
}

// Cambio de estado condicionado al estado actual: la transición ocurre una sola vez
// aunque dos ejecuciones (scheduler y endpoint de desarrollo) coincidan. Devuelve true si cambió.
function transicionar(id, desde, hasta) {
    const resultado = db.prepare('UPDATE vacaciones SET estado = ? WHERE id = ? AND estado = ?').run(hasta, id, desde);
    return resultado.changes > 0;
}

function mapearConEmail(fila) {
    return { ...mapear(fila), email: fila.email };
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

module.exports = {
    crear, obtenerPorId, listarTodas, listarPorEmpleado, buscarSolapamiento, cancelar,
    listarParaIniciar, listarParaFinalizar, transicionar,
};