const assert = require('node:assert/strict');
const { Given, When, Then } = require('@cucumber/cucumber');
const { esperarHastaQue, verificarQueNoOcurre } = require('../support/polling');

/** Fecha AAAA-MM-DD a `dias` de hoy (negativo = pasado), en hora local, como la calcula el servicio. */
function fechaRelativa(dias) {
    const d = new Date();
    d.setDate(d.getDate() + dias);
    const mes = String(d.getMonth() + 1).padStart(2, '0');
    const dia = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${mes}-${dia}`;
}

async function programar(mundo, empleadoId, desde, hasta) {
    const r = await mundo.solicitar('POST', '/vacaciones', {
        body: { empleadoId, fechaInicio: fechaRelativa(desde), fechaFin: fechaRelativa(hasta) }
    });
    if (r.status === 201) mundo.vacacionesId = r.cuerpo.id;
    return r;
}

async function notificaciones(mundo) {
    const r = await mundo.solicitar('GET', `/notificaciones/${mundo.empleadoId}`);
    return r.status === 200 && Array.isArray(r.cuerpo) ? r.cuerpo : [];
}

// ---------- Cuando ----------

When('programo vacaciones del día {int} al día {int} contando desde hoy', async function (desde, hasta) {
    await programar(this, this.empleadoId, desde, hasta);
});

When('programo vacaciones para un empleado que no existe', async function () {
    await programar(this, '999999999', 5, 10);
});

When('se fuerza el inicio de las vacaciones', async function () {
    await this.solicitar('POST', `/vacaciones/${this.vacacionesId}/forzar-inicio`);
});

When('se fuerza el fin de las vacaciones', async function () {
    await this.solicitar('POST', `/vacaciones/${this.vacacionesId}/forzar-fin`);
});

// ---------- Dado ----------

Given('que el empleado tiene vacaciones programadas del día {int} al día {int} contando desde hoy', async function (desde, hasta) {
    const r = await programar(this, this.empleadoId, desde, hasta);
    assert.equal(r.status, 201, `No se pudieron programar las vacaciones: ${JSON.stringify(r.cuerpo)}`);
});

// ---------- Entonces ----------

Then('el período de vacaciones debe estar en estado {string}', async function (estado) {
    const r = await this.solicitar('GET', `/vacaciones/${this.vacacionesId}`);
    assert.equal(r.status, 200);
    assert.equal(r.cuerpo.estado, estado);
});

Then('el mensaje de error debe contener {string}', function (texto) {
    const { cuerpo } = this.ultimaRespuesta;
    assert.ok(cuerpo && String(cuerpo.mensaje).includes(texto),
        `Se esperaba un mensaje con "${texto}" pero llegó: ${JSON.stringify(cuerpo)}`);
});

Then('eventualmente el empleado no debe tener una notificación que contenga {string}', async function (texto) {
    await verificarQueNoOcurre(`apareció una notificación con "${texto}"`,
        async () => (await notificaciones(this)).some((n) => n.mensaje.includes(texto)));
});