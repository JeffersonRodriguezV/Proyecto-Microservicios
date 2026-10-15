const assert = require('node:assert/strict');
const { Given, Then } = require('@cucumber/cucumber');
const { esperarSistemaListo } = require('../support/readiness');

// Pasos genericos reutilizables por todos los .feature (el texto es el mismo con Dado/Cuando/Entonces/Y).

Given('que el sistema está desplegado y operativo', async function () {
  await esperarSistemaListo(this.baseUrl);
  await this.solicitar('GET', '/health');
});

Given('que estoy autenticado como {word}', async function (rol) {
  await this.autenticarComo(rol);
});

Then('la respuesta debe tener código {int}', function (esperado) {
  const { status, cuerpo } = this.ultimaRespuesta;
  assert.equal(status, esperado, `Se esperaba HTTP ${esperado} pero llegó ${status}. Cuerpo: ${JSON.stringify(cuerpo)}`);
});
