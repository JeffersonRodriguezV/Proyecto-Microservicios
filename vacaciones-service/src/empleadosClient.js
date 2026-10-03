const EMPLEADOS_URL = process.env.EMPLEADOS_URL || 'http://localhost:8080';

/**
 * Consulta GET /empleados/{id}. Devuelve el objeto del empleado si
 * existe (200), o null en cualquier otro caso (404, timeout,
 * servicio caído). Timeout explícito de 5s con AbortController.
 */
async function obtenerEmpleado(empleadoId) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);

    try {
        const respuesta = await fetch(`${EMPLEADOS_URL}/empleados/${empleadoId}`, {
            signal: controller.signal,
        });
        if (respuesta.status !== 200) return null;
        return await respuesta.json();
    } catch (error) {
        console.error('No se pudo validar el empleado:', error.message);
        return null;
    } finally {
        clearTimeout(timeoutId);
    }
}

module.exports = { obtenerEmpleado };