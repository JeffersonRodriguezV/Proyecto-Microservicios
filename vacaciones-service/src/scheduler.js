const repo = require('./vacacionesRepo');
const { publicar } = require('./eventoPublisher');
const { obtenerEmpleado } = require('./empleadosClient');

// Configuración por variables de entorno (Reto 5, §3.1):
//   SCHEDULER_ENABLED           'true' (por defecto) o 'false' para apagarlo
//   SCHEDULER_INTERVAL_SECONDS  cada cuánto revisa; 60 por defecto (en desarrollo permite probar sin esperar un día)
//   TZ                          zona horaria que define cuándo cambia "hoy" (p. ej. America/Bogota)
const HABILITADO = (process.env.SCHEDULER_ENABLED || 'true') === 'true';
const INTERVALO_SEGUNDOS = Math.max(1, Number(process.env.SCHEDULER_INTERVAL_SECONDS) || 60);

let ejecutando = false;

/** Fecha de hoy como AAAA-MM-DD en la zona horaria del proceso. */
function hoyLocal() {
    const d = new Date();
    const mes = String(d.getMonth() + 1).padStart(2, '0');
    const dia = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${mes}-${dia}`;
}

// El email viaja en el evento (Catálogo 3.9/3.10). Se guarda al programar; si el período es anterior
// a esa columna, se consulta al empleados-service como respaldo.
async function emailDe(periodo) {
    if (periodo.email) return periodo.email;
    const empleado = await obtenerEmpleado(periodo.empleadoId);
    return empleado ? empleado.email : null;
}

/** PROGRAMADA -> EN_CURSO y publica vacaciones.iniciadas (Catálogo 3.9). Devuelve true si hizo la transición. */
async function iniciarPeriodo(periodo) {
    if (!repo.transicionar(periodo.id, 'PROGRAMADA', 'EN_CURSO')) return false;

    // La publicación ocurre después de persistir; si falla, se registra pero no se revierte (igual que en el Reto 4).
    await publicar('vacaciones.iniciadas', {
        vacacionesId: periodo.id,
        empleadoId: periodo.empleadoId,
        email: await emailDe(periodo),
        fechaInicio: periodo.fechaInicio,
        fechaFin: periodo.fechaFin,
    });
    console.log(`[scheduler] ${periodo.id} -> EN_CURSO (vacaciones.iniciadas, empleado ${periodo.empleadoId})`);
    return true;
}

/** EN_CURSO -> FINALIZADA y publica vacaciones.finalizadas (Catálogo 3.10). Devuelve true si hizo la transición. */
async function finalizarPeriodo(periodo) {
    if (!repo.transicionar(periodo.id, 'EN_CURSO', 'FINALIZADA')) return false;

    await publicar('vacaciones.finalizadas', {
        vacacionesId: periodo.id,
        empleadoId: periodo.empleadoId,
        email: await emailDe(periodo),
        fechaFin: periodo.fechaFin,
    });
    console.log(`[scheduler] ${periodo.id} -> FINALIZADA (vacaciones.finalizadas, empleado ${periodo.empleadoId})`);
    return true;
}

/**
 * Una pasada del scheduler. Primero cierra los períodos vencidos y después abre los que llegaron:
 * así un mismo período nunca inicia y termina en la misma pasada.
 */
async function ejecutarCiclo() {
    if (ejecutando) return;     // evita pasadas solapadas si una tarda más que el intervalo
    ejecutando = true;
    try {
        const hoy = hoyLocal();
        for (const periodo of repo.listarParaFinalizar(hoy)) {
            await finalizarPeriodo(periodo);
        }
        for (const periodo of repo.listarParaIniciar(hoy)) {
            await iniciarPeriodo(periodo);
        }
    } catch (error) {
        console.error('[scheduler] error en la pasada:', error.message);
    } finally {
        ejecutando = false;
    }
}

function arrancar() {
    if (!HABILITADO) {
        console.log('[scheduler] deshabilitado (SCHEDULER_ENABLED=false)');
        return;
    }
    setInterval(ejecutarCiclo, INTERVALO_SEGUNDOS * 1000);
    console.log(`[scheduler] activo: revisa cada ${INTERVALO_SEGUNDOS}s (zona horaria ${process.env.TZ || 'UTC'})`);
}

module.exports = { arrancar, ejecutarCiclo, iniciarPeriodo, finalizarPeriodo, hoyLocal };