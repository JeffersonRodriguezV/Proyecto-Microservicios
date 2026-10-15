const assert = require('node:assert/strict');
const { Given, When, Then } = require('@cucumber/cucumber');
const { sufijoUnico } = require('../support/datos');

// ---------- Consulta de empleados con distintos tokens ----------

When('consulto la lista de empleados sin token de autenticación', async function () {
  await this.solicitar('GET', '/empleados', { token: null });
});

When('consulto la lista de empleados con el token {string}', async function (token) {
  await this.solicitar('GET', '/empleados', { token });
});

When('consulto la lista de empleados', async function () {
  await this.solicitar('GET', '/empleados');
});

// ---------- Manipulacion del token ----------

/** Cambia el primer caracter de la firma (tercer segmento del JWT): el token deja de ser valido. */
Given('altero la firma de mi token', function () {
  const [cabecera, carga, firma] = this.token.split('.');
  const firmaAlterada = (firma[0] === 'A' ? 'B' : 'A') + firma.slice(1);
  this.token = [cabecera, carga, firmaAlterada].join('.');
});

// ---------- Solicitudes genericas ----------

/**
 * Envia METODO ruta con el token actual. Para POST/PUT manda un cuerpo vacio: en los escenarios de seguridad
 * lo que se verifica es la decision del gateway (401/403), no la validacion de negocio.
 */
When('envío una solicitud {word} a {string}', async function (metodo, ruta) {
  const conCuerpo = ['POST', 'PUT', 'PATCH'].includes(metodo.toUpperCase());
  await this.solicitar(metodo.toUpperCase(), ruta, conCuerpo ? { body: {} } : {});
});

Then('la solicitud no debe ser rechazada por seguridad', function () {
  const { status, cuerpo } = this.ultimaRespuesta;
  assert.ok(![401, 403].includes(status), `El gateway rechazó la solicitud con ${status}. Cuerpo: ${JSON.stringify(cuerpo)}`);
});

// ---------- Propiedad del recurso: perfiles ----------

/** El "sub" del token es el id del usuario autenticado. */
function idDelUsuario(token) {
  const carga = token.split('.')[1];
  return JSON.parse(Buffer.from(carga, 'base64url').toString('utf8')).sub;
}

When('intento actualizar el perfil de otro empleado', async function () {
  await this.solicitar('PUT', '/perfiles/NO-SOY-YO-999', { body: { ciudad: 'Armenia' } });
});

When('intento actualizar mi propio perfil', async function () {
  await this.solicitar('PUT', `/perfiles/${idDelUsuario(this.token)}`, { body: { ciudad: 'Armenia' } });
});

// ---------- Flujo ADMIN: crear departamento, crear empleado y retirarlo ----------

When('creo un departamento con datos únicos', async function () {
  const sufijo = sufijoUnico();
  this.departamentoId = `SEG-${sufijo}`.slice(0, 50);
  await this.solicitar('POST', '/departamentos', {
    body: { id: this.departamentoId, nombre: 'Departamento de seguridad', descripcion: 'Creado por las pruebas BDD de seguridad' }
  });
});

When('creo un empleado en ese departamento', async function () {
  const sufijo = sufijoUnico();
  const respuesta = await this.solicitar('POST', '/empleados', {
    body: {
      nombre: 'Prueba',
      apellido: 'Seguridad',
      email: `seguridad.${sufijo}@empresa.com`,
      numeroEmpleado: `SEG-${sufijo}`,
      cargo: 'QA',
      area: 'Calidad',
      departamentoId: this.departamentoId,
      fechaIngreso: new Date().toISOString().slice(0, 10)
    }
  });
  if (respuesta.status === 200) this.empleadoId = respuesta.cuerpo.id;
});

When('retiro a ese empleado', async function () {
  assert.ok(this.empleadoId !== undefined, 'No hay un empleado creado en este escenario para retirar');
  await this.solicitar('DELETE', `/empleados/${this.empleadoId}`);
});
