// Pruebas de integración del Gateway: enrutamiento real hacia servicios simulados.
process.env.JWT_SECRET = 'x'.repeat(40);

const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const jwt = require('jsonwebtoken');

/** Servicio simulado: responde con el método y la ruta que recibió. */
function servicioFalso() {
    return new Promise((resolve) => {
        const srv = http.createServer((req, res) => {
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ metodo: req.method, ruta: req.url }));
        });
        srv.listen(0, '127.0.0.1', () => resolve(srv));
    });
}
const url = (srv) => `http://127.0.0.1:${srv.address().port}`;
const token = (claims) => jwt.sign(claims, process.env.JWT_SECRET, { algorithm: 'HS256', expiresIn: '5m' });

let upstream, gateway, base;

test.before(async () => {
    upstream = await servicioFalso();
    for (const v of ['AUTH', 'EMPLEADOS', 'DEPARTAMENTOS', 'PERFILES', 'NOTIFICACIONES']) {
        process.env[`${v}_URL`] = url(upstream);
    }
    // vacaciones apunta a un puerto cerrado para probar el 503
    process.env.VACACIONES_URL = 'http://127.0.0.1:1';
    const { app } = require('../src/app');
    gateway = await new Promise((resolve) => { const s = app.listen(0, '127.0.0.1', () => resolve(s)); });
    base = url(gateway);
});

test.after(async () => {
    gateway.close();
    upstream.close();
});

test('GET /health responde 200 con el formato común', async () => {
    const r = await fetch(`${base}/health`);
    assert.equal(r.status, 200);
    assert.deepEqual(await r.json(), { status: 'UP', service: 'api-gateway', checks: {} });
});

test('una ruta protegida sin token responde 401', async () => {
    const r = await fetch(`${base}/empleados`);
    assert.equal(r.status, 401);
    const cuerpo = await r.json();
    assert.equal(cuerpo.status, 401);
});

test('ADMIN llega al servicio de empleados con la misma ruta', async () => {
    const r = await fetch(`${base}/empleados?estado=RETIRADO`, {
        headers: { Authorization: `Bearer ${token({ sub: '1', role: 'ADMIN' })}` },
    });
    assert.equal(r.status, 200);
    assert.deepEqual(await r.json(), { metodo: 'GET', ruta: '/empleados?estado=RETIRADO' });
});

test('el login es público y se enruta a auth-service', async () => {
    const r = await fetch(`${base}/auth/login`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}',
    });
    assert.equal(r.status, 200);
    assert.equal((await r.json()).ruta, '/auth/login');
});

test('enruta a departamentos y perfiles', async () => {
    const h = { Authorization: `Bearer ${token({ sub: '1', role: 'ADMIN' })}` };
    assert.equal((await (await fetch(`${base}/departamentos`, { headers: h })).json()).ruta, '/departamentos');
    assert.equal((await (await fetch(`${base}/perfiles/1`, { headers: h })).json()).ruta, '/perfiles/1');
    assert.equal((await (await fetch(`${base}/notificaciones`, { headers: h })).json()).ruta, '/notificaciones');
});

test('USER no puede crear empleados (403)', async () => {
    const r = await fetch(`${base}/empleados`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token({ sub: '2', role: 'USER' })}`, 'Content-Type': 'application/json' },
        body: '{}',
    });
    assert.equal(r.status, 403);
});

test('USER solo modifica su propio perfil', async () => {
    const h = { Authorization: `Bearer ${token({ sub: '2', role: 'USER' })}`, 'Content-Type': 'application/json' };
    assert.equal((await fetch(`${base}/perfiles/2`, { method: 'PUT', headers: h, body: '{}' })).status, 200);
    assert.equal((await fetch(`${base}/perfiles/3`, { method: 'PUT', headers: h, body: '{}' })).status, 403);
});

test('si el servicio destino no responde, el gateway devuelve 503 JSON', async () => {
    const r = await fetch(`${base}/vacaciones`, {
        headers: { Authorization: `Bearer ${token({ sub: '1', role: 'ADMIN' })}` },
    });
    assert.equal(r.status, 503);
    const cuerpo = await r.json();
    assert.equal(cuerpo.status, 503);
    assert.match(cuerpo.mensaje, /no está disponible/);
});

test('GET /swagger es público y sirve la documentación agregada de los 6 servicios', async () => {
    const pagina = await fetch(`${base}/swagger/`);
    assert.equal(pagina.status, 200);
    assert.match(pagina.headers.get('content-type'), /html/);

    const init = await (await fetch(`${base}/swagger/swagger-ui-init.js`)).text();
    for (const servicio of ['auth-service', 'empleados-service', 'departamentos-service',
        'perfiles-service', 'notificaciones-service', 'vacaciones-service']) {
        assert.ok(init.includes(servicio), `falta ${servicio} en el selector de Swagger`);
    }
});

test('un POST a /swagger no es público (pasa por la seguridad)', async () => {
    const r = await fetch(`${base}/swagger/`, { method: 'POST' });
    assert.equal(r.status, 401);
});
