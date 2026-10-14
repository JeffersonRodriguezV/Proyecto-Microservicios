/**
 * Reglas de validación de vacaciones como FUNCIONES PURAS: no tocan la base de datos,
 * ni el reloj, ni la red. El "hoy" llega por parámetro para que sean deterministas
 * y se puedan probar en milisegundos, sin Docker.
 */

/** Toma solo AAAA-MM-DD, igual que hace el resto del servicio para comparar por día. */
function soloFecha(valor) {
    return String(valor).slice(0, 10);
}

/** Campos obligatorios. Devuelve el mensaje de error o null si todo está bien. */
function validarCamposObligatorios({ empleadoId, fechaInicio, fechaFin } = {}) {
    if (!empleadoId || !fechaInicio || !fechaFin) {
        return 'empleadoId, fechaInicio y fechaFin son obligatorios';
    }
    return null;
}

/** Validación 1: fechaFin no puede ser anterior a fechaInicio (un solo día SÍ se permite). */
function validarOrdenFechas(fechaInicio, fechaFin) {
    if (new Date(fechaFin) < new Date(fechaInicio)) {
        return 'fechaFin no puede ser anterior a fechaInicio';
    }
    return null;
}

/** Validación 2: fechaInicio no puede estar en el pasado. `hoy` viene como AAAA-MM-DD. */
function validarNoPasada(fechaInicio, hoy) {
    if (soloFecha(fechaInicio) < hoy) {
        return 'fechaInicio no puede ser una fecha pasada';
    }
    return null;
}

/**
 * Validación 3: dos rangos [a,b] y [c,d] se cruzan si a <= d y c <= b
 * (los extremos cuentan: tocarse por un día ya es solapamiento).
 */
function hayCruce(inicioA, finA, inicioB, finB) {
    return soloFecha(inicioA) <= soloFecha(finB) && soloFecha(inicioB) <= soloFecha(finA);
}

module.exports = { validarCamposObligatorios, validarOrdenFechas, validarNoPasada, hayCruce };
