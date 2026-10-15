/**
 * Datos de prueba unicos por ejecucion: evitan choques con datos de otras pruebas o de ejecuciones previas
 * (email y numeroEmpleado son unicos en empleados-service; el id de departamento tambien).
 */
function sufijoUnico() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
}

module.exports = { sufijoUnico };
