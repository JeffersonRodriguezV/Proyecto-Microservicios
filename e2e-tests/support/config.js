/**
 * Configuracion de las pruebas: todo sale de variables de entorno (o de un archivo .env local),
 * nada de URLs ni credenciales escritas en el codigo de los pasos.
 */
require('dotenv').config({ quiet: true });

/** Lee una variable obligatoria y falla con un mensaje claro si falta. */
function requerida(nombre) {
  const valor = process.env[nombre];
  if (!valor) {
    throw new Error(
      `Falta la variable de entorno ${nombre}. Copie e2e-tests/.env.example como e2e-tests/.env y complete los valores.`
    );
  }
  return valor;
}

module.exports = {
  baseUrl: (process.env.BASE_URL || 'http://localhost:8080').replace(/\/+$/, ''),
  usuarios: {
    ADMIN: { email: requerida('ADMIN_EMAIL'), password: requerida('ADMIN_PASSWORD') },
    USER: { email: requerida('USER_EMAIL'), password: requerida('USER_PASSWORD') }
  }
};
