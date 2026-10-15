/**
 * Espera a que el sistema este operativo reintentando GET /health hasta recibir 200.
 * Sirve para el escenario de humo y evita que las pruebas fallen solo porque el sistema aun arranca.
 *
 * No es la funcion de polling general para eventos asincronos (esa vive en su propio modulo);
 * esta solo cubre la disponibilidad del sistema.
 */
async function esperarSistemaListo(baseUrl, { intentos = 20, esperaMs = 2000 } = {}) {
  let ultimoMotivo = 'sin respuesta';
  for (let intento = 1; intento <= intentos; intento++) {
    try {
      const respuesta = await fetch(`${baseUrl}/health`);
      if (respuesta.status === 200) return;
      ultimoMotivo = `HTTP ${respuesta.status}`;
    } catch (error) {
      ultimoMotivo = error.message;
    }
    if (intento < intentos) {
      await new Promise((resolver) => setTimeout(resolver, esperaMs));
    }
  }
  throw new Error(`El sistema no estuvo listo tras ${intentos} intentos (ultimo motivo: ${ultimoMotivo})`);
}

module.exports = { esperarSistemaListo };
