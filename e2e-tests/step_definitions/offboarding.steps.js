const assert = require('node:assert/strict');
const { When, Then } = require('@cucumber/cucumber');
const { verificarQueNoOcurre } = require('../support/polling');

When('solicito recuperar la contraseña del empleado', async function () {
    await this.solicitar('POST', '/auth/recover-password', { body: { email: this.email }, token: null });
});

When('consulto la auditoría de empleados retirados', async function () {
    await this.solicitar('GET', '/empleados?estado=RETIRADO');
});

Then('la auditoría debe incluir al empleado con su fecha de retiro', function () {
    const { cuerpo } = this.ultimaRespuesta;
    assert.ok(Array.isArray(cuerpo), `Se esperaba una lista: ${JSON.stringify(cuerpo)}`);
    const encontrado = cuerpo.find((e) => String(e.id) === String(this.empleadoId));
    assert.ok(encontrado, `El empleado ${this.empleadoId} no aparece en la auditoría de retirados`);
    assert.equal(encontrado.estado, 'RETIRADO');
    assert.ok(encontrado.fechaRetiro, 'El empleado retirado debe tener fechaRetiro');
});

// La recuperación siempre responde 200 (para no revelar si el correo existe);
// lo que importa es que NO se emita ningún enlace de restablecimiento.
Then('no debe llegar ninguna notificación con enlace de restablecimiento', async function () {
    await verificarQueNoOcurre('llegó un enlace de restablecimiento a un empleado retirado', async () => {
        const r = await this.solicitar('GET', `/notificaciones/${this.empleadoId}`);
        const lista = Array.isArray(r.cuerpo) ? r.cuerpo : [];
        return lista.some((n) => n.mensaje.includes('restablecer su contraseña'));
    });
});