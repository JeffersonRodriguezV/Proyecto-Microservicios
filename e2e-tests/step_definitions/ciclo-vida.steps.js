const assert = require('node:assert/strict');
const { Given, When, Then } = require('@cucumber/cucumber');
const { sufijoUnico } = require('../support/datos');
const { esperarHastaQue, verificarQueNoOcurre } = require('../support/polling');

const PASSWORD_VALIDA = 'Segura123';

// ---------- Utilidades internas (no son pasos) ----------

/** Crea un departamento unico y un empleado ACTIVO en el. Guarda sus datos en el World. */
async function crearEmpleadoNuevo(mundo, sobreescribir = {}) {
    const sufijo = sufijoUnico();
    mundo.departamentoId = `CV-${sufijo}`.slice(0, 50);
    const dep = await mundo.solicitar('POST', '/departamentos', {
        body: { id: mundo.departamentoId, nombre: 'Departamento BDD', descripcion: 'Creado por pruebas BDD' }
    });
    assert.equal(dep.status, 201, `No se pudo crear el departamento: ${JSON.stringify(dep.cuerpo)}`);

    mundo.email = `bdd.${sufijo}@empresa.com`;
    mundo.password = PASSWORD_VALIDA;
    const cuerpo = {
        nombre: 'Prueba', apellido: 'Ciclo', email: mundo.email, numeroEmpleado: `CV-${sufijo}`,
        cargo: 'QA', area: 'Calidad', departamentoId: mundo.departamentoId,
        fechaIngreso: new Date().toISOString().slice(0, 10),
        ...sobreescribir
    };
    const r = await mundo.solicitar('POST', '/empleados', { body: cuerpo });
    if (r.status === 200 || r.status === 201) mundo.empleadoId = String(r.cuerpo.id);
    return r;
}

/** Lee el token de activacion del mensaje de la notificacion de bienvenida. */
function extraerToken(notificaciones) {
    const bienvenida = notificaciones.find((n) => /token=/.test(n.mensaje));
    if (!bienvenida) return null;
    return bienvenida.mensaje.match(/token=([^\s)]+)/)[1];
}

async function notificacionesDe(mundo) {
    const r = await mundo.solicitar('GET', `/notificaciones/${mundo.empleadoId}`);
    return r.status === 200 && Array.isArray(r.cuerpo) ? r.cuerpo : [];
}

/** Intenta iniciar sesion SIN pisar this.ultimaRespuesta ni this.token del escenario. */
async function intentarLogin(mundo) {
    const resp = await fetch(`${mundo.baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: mundo.email, password: mundo.password })
    });
    return resp.status;
}

// ---------- Dado ----------

Given('que se registra un empleado nuevo con datos únicos', async function () {
    const r = await crearEmpleadoNuevo(this);
    assert.ok([200, 201].includes(r.status), `No se pudo registrar el empleado: ${r.status} ${JSON.stringify(r.cuerpo)}`);
});

Given('que existe un empleado con la cuenta activada', async function () {
    const r = await crearEmpleadoNuevo(this);
    assert.ok([200, 201].includes(r.status), `No se pudo registrar el empleado: ${JSON.stringify(r.cuerpo)}`);
    const token = await esperarHastaQue('llega el token de activación',
        async () => extraerToken(await notificacionesDe(this)));
    const act = await this.solicitar('POST', '/auth/reset-password',
        { body: { token, newPassword: this.password }, token: null });
    assert.equal(act.status, 200, `Falló la activación: ${JSON.stringify(act.cuerpo)}`);
});

// ---------- Cuando ----------

When('activo la cuenta con el token recibido por notificación', async function () {
    const token = await esperarHastaQue('llega el token de activación',
        async () => extraerToken(await notificacionesDe(this)));
    await this.solicitar('POST', '/auth/reset-password',
        { body: { token, newPassword: this.password }, token: null });
});

When('el empleado inicia sesión con su contraseña', async function () {
    await this.solicitar('POST', '/auth/login',
        { body: { email: this.email, password: this.password }, token: null });
});

When('intento registrar un empleado con {word} {string}', async function (campo, valor) {
    const r = await crearEmpleadoNuevo(this, { [campo]: valor });
    this.ultimaRespuesta = r;
});

When('retiro al empleado', async function () {
    await this.solicitar('DELETE', `/empleados/${this.empleadoId}`);
});

// ---------- Entonces (con polling) ----------

Then('el empleado debe quedar registrado con estado {string}', async function (estado) {
    const r = await this.solicitar('GET', `/empleados/${this.empleadoId}`);
    assert.equal(r.status, 200);
    assert.equal(r.cuerpo.estado, estado);
});

Then('eventualmente debe existir el perfil del empleado', async function () {
    await esperarHastaQue('existe el perfil del empleado', async () => {
        const r = await this.solicitar('GET', `/perfiles/${this.empleadoId}`);
        return r.status === 200;
    });
});

Then('eventualmente el empleado debe tener una notificación que contenga {string}', async function (texto) {
    await esperarHastaQue(`hay una notificación que contiene "${texto}"`,
        async () => (await notificacionesDe(this)).some((n) => n.mensaje.includes(texto)));
});

Then('eventualmente el empleado debe tener una notificación de tipo {string}', async function (tipo) {
    await esperarHastaQue(`hay una notificación de tipo ${tipo}`,
        async () => (await notificacionesDe(this)).some((n) => n.tipo === tipo));
});

Then('el empleado no debe poder iniciar sesión', async function () {
    // La cuenta se crea/cambia de forma asíncrona: se espera a que el rechazo sea el estado estable.
    await esperarHastaQue('el login es rechazado', async () => [401, 403].includes(await intentarLogin(this)));
});

Then('eventualmente el empleado debe poder iniciar sesión', async function () {
    await esperarHastaQue('el login funciona', async () => (await intentarLogin(this)) === 200);
});

Then('el empleado no debe poder iniciar sesión durante unos segundos', async function () {
    await verificarQueNoOcurre('el login funcionó', async () => (await intentarLogin(this)) === 200);
});