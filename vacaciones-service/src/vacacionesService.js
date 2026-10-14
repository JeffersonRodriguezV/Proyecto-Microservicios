/**
 * Caso de uso "programar vacaciones" con DEPENDENCIAS INYECTADAS (Inversión de Dependencias).
 *
 * Este módulo no importa la base de datos, ni el cliente HTTP a empleados, ni el broker:
 * recibe todo por parámetro. En producción server.js le pasa los reales; en las pruebas
 * unitarias se le pasan dobles (fakes) en memoria, así no hace falta Docker ni red.
 *
 * No conoce Express: devuelve un resultado { ok, status, ... } y es server.js quien lo
 * traduce a HTTP. Así la regla de negocio queda separada del transporte.
 */
const { validarCamposObligatorios, validarOrdenFechas, validarNoPasada } = require('./validaciones');
const { contarDiasHabiles } = require('./diasHabiles');

/** Resultado de rechazo (todas las validaciones del servicio responden 400). */
function rechazo(mensaje, extra = {}) {
    return { ok: false, status: 400, mensaje, extra };
}

/**
 * @param {object}   deps
 * @param {object}   deps.repo             { buscarSolapamiento(empleadoId, inicio, fin), crear(datos) }
 * @param {Function} deps.obtenerEmpleado  async (empleadoId) => empleado | null
 * @param {Function} deps.publicar         (tipo, datos) => void  (publica en el broker; no se espera)
 * @param {Function} deps.hoy              () => 'AAAA-MM-DD' (el "hoy" de la zona del servicio)
 */
function crearServicioVacaciones({ repo, obtenerEmpleado, publicar, hoy }) {
    async function programar(body = {}) {
        const { empleadoId, fechaInicio, fechaFin } = body;

        // Campos obligatorios + validaciones 1 y 2 (reglas puras). El || corta en la primera que falla.
        const errorValidacion =
            validarCamposObligatorios(body) ||
            validarOrdenFechas(fechaInicio, fechaFin) ||
            validarNoPasada(fechaInicio, hoy());
        if (errorValidacion) {
            return rechazo(errorValidacion);
        }

        // El JSON puede traer el id como número (1) o como texto ("1"). La columna es TEXT y
        // node:sqlite guarda el número 1 como "1.0", así que se normaliza a texto antes de
        // tocar el repositorio; de lo contrario "1" y 1 se verían como empleados distintos.
        const idEmpleado = String(empleadoId);

        // Validación 3: solapamiento con un período activo del mismo empleado.
        const conflicto = repo.buscarSolapamiento(idEmpleado, fechaInicio, fechaFin);
        if (conflicto) {
            return rechazo('El empleado ya tiene un período que se cruza con el solicitado', {
                periodoConflicto: conflicto,
            });
        }

        // Validación 4: el empleado debe existir (se consulta a empleados-service).
        const empleado = await obtenerEmpleado(empleadoId);
        if (!empleado) {
            return rechazo(`El empleado con id '${empleadoId}' no existe`);
        }

        const creado = repo.crear({ empleadoId: idEmpleado, email: empleado.email, fechaInicio, fechaFin });

        // Se publica DESPUÉS de persistir; si el broker falla no se revierte lo guardado.
        publicar('vacaciones.programadas', {
            vacacionesId: creado.id,
            empleadoId,
            email: empleado.email,
            fechaInicio,
            fechaFin,
            diasHabiles: contarDiasHabiles(fechaInicio, fechaFin),
        });

        return { ok: true, status: 201, periodo: creado };
    }

    return { programar };
}

module.exports = { crearServicioVacaciones };
