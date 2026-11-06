const test = require('node:test');
const assert = require('node:assert/strict');
const { crearHealthHandler } = require('../src/health');

/** Ejecuta el handler con un res falso y devuelve {status, cuerpo}. */
async function llamar(opciones) {
    const out = {};
    const res = { status(s) { out.status = s; return this; }, json(c) { out.cuerpo = c; return this; } };
    await crearHealthHandler({ servicio: 'vacaciones-service', ...opciones })({}, res);
    return out;
}

test('todo UP responde 200 con el formato común', async () => {
    const r = await llamar({ verificarBaseDatos: () => ({ ok: 1 }), verificarBroker: async () => true });
    assert.equal(r.status, 200);
    assert.deepEqual(r.cuerpo, {
        status: 'UP', service: 'vacaciones-service', checks: { database: 'UP', messageBroker: 'UP' },
    });
});

test('broker caído responde 503 y marca solo el broker como DOWN', async () => {
    const r = await llamar({ verificarBaseDatos: () => true, verificarBroker: async () => false });
    assert.equal(r.status, 503);
    assert.equal(r.cuerpo.status, 'DOWN');
    assert.deepEqual(r.cuerpo.checks, { database: 'UP', messageBroker: 'DOWN' });
});

test('base de datos que lanza error responde 503', async () => {
    const r = await llamar({
        verificarBaseDatos: () => { throw new Error('sin bd'); },
        verificarBroker: async () => true,
    });
    assert.equal(r.status, 503);
    assert.equal(r.cuerpo.checks.database, 'DOWN');
});

test('una dependencia que no responde a tiempo cuenta como DOWN', async () => {
    const r = await llamar({
        verificarBaseDatos: () => true,
        verificarBroker: () => new Promise(() => {}),
        timeoutMs: 20,
    });
    assert.equal(r.status, 503);
    assert.equal(r.cuerpo.checks.messageBroker, 'DOWN');
});
