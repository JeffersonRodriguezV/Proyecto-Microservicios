// Pruebas unitarias de la seguridad del Gateway (JWT + RBAC + propiedad del recurso).
process.env.JWT_SECRET = 'x'.repeat(40);

const test = require('node:test');
const assert = require('node:assert/strict');
const jwt = require('jsonwebtoken');
const { autenticar, autorizar } = require('../src/security');

const token = (claims, opts = {}) =>
    jwt.sign(claims, process.env.JWT_SECRET, { algorithm: 'HS256', expiresIn: '5m', ...opts });

/** Simula req/res de Express y devuelve el resultado de pasar por un middleware. */
function ejecutar(middleware, req) {
    const resultado = { status: null, cuerpo: null, siguio: false };
    const res = {
        status(s) { resultado.status = s; return this; },
        json(c) { resultado.cuerpo = c; return this; },
    };
    middleware({ headers: {}, ...req }, res, () => { resultado.siguio = true; });
    return resultado;
}

test('rutas públicas pasan sin token', () => {
    for (const ruta of ['/auth/login', '/auth/recover-password', '/auth/reset-password']) {
        assert.equal(ejecutar(autenticar, { method: 'POST', path: ruta }).siguio, true, ruta);
    }
});

test('la documentación Swagger (GET) es pública', () => {
    assert.equal(ejecutar(autenticar, { method: 'GET', path: '/perfiles/swagger/index.html' }).siguio, true);
    assert.equal(ejecutar(autenticar, { method: 'GET', path: '/departamentos/docs' }).siguio, true);
});

test('sin cabecera Authorization responde 401', () => {
    const r = ejecutar(autenticar, { method: 'GET', path: '/empleados' });
    assert.equal(r.status, 401);
    assert.equal(r.siguio, false);
});

test('token alterado o firmado con otro secreto responde 401', () => {
    const malo = jwt.sign({ sub: '1', role: 'ADMIN' }, 'otro-secreto-distinto-de-32-caracteres!!');
    const r = ejecutar(autenticar, { method: 'GET', path: '/empleados', headers: { authorization: `Bearer ${malo}` } });
    assert.equal(r.status, 401);
});

test('token vencido responde 401', () => {
    const vencido = token({ sub: '1', role: 'ADMIN' }, { expiresIn: -10 });
    const r = ejecutar(autenticar, { method: 'GET', path: '/empleados', headers: { authorization: `Bearer ${vencido}` } });
    assert.equal(r.status, 401);
});

test('un token de activación/recuperación (con type) no abre la API', () => {
    const t = token({ sub: '1', role: 'USER', type: 'ACTIVACION' });
    const r = ejecutar(autenticar, { method: 'GET', path: '/empleados', headers: { authorization: `Bearer ${t}` } });
    assert.equal(r.status, 401);
});

test('un token sin rol responde 401', () => {
    const t = token({ sub: '1' });
    const r = ejecutar(autenticar, { method: 'GET', path: '/empleados', headers: { authorization: `Bearer ${t}` } });
    assert.equal(r.status, 401);
});

test('un token de acceso válido pasa y deja el usuario en req.usuario', () => {
    const req = { method: 'GET', path: '/empleados', headers: { authorization: `Bearer ${token({ sub: 42, role: 'USER' })}` } };
    const r = ejecutar(autenticar, req);
    assert.equal(r.siguio, true);
});

test('ADMIN puede escribir en cualquier ruta', () => {
    const r = ejecutar(autorizar, { method: 'DELETE', path: '/empleados/7', usuario: { id: '1', rol: 'ADMIN' } });
    assert.equal(r.siguio, true);
});

test('USER puede leer', () => {
    const r = ejecutar(autorizar, { method: 'GET', path: '/empleados', usuario: { id: '5', rol: 'USER' } });
    assert.equal(r.siguio, true);
});

test('USER no puede crear ni borrar (403)', () => {
    for (const [m, p] of [['POST', '/empleados'], ['DELETE', '/empleados/1'], ['POST', '/vacaciones']]) {
        const r = ejecutar(autorizar, { method: m, path: p, usuario: { id: '5', rol: 'USER' } });
        assert.equal(r.status, 403, `${m} ${p}`);
    }
});

test('USER puede cambiar su contraseña', () => {
    const r = ejecutar(autorizar, { method: 'POST', path: '/auth/change-password', usuario: { id: '5', rol: 'USER' } });
    assert.equal(r.siguio, true);
});

test('USER edita solo su propio perfil (propiedad del recurso)', () => {
    const propio = ejecutar(autorizar, { method: 'PUT', path: '/perfiles/5', usuario: { id: '5', rol: 'USER' } });
    assert.equal(propio.siguio, true);
    const ajeno = ejecutar(autorizar, { method: 'PUT', path: '/perfiles/9', usuario: { id: '5', rol: 'USER' } });
    assert.equal(ajeno.status, 403);
});

test('un rol desconocido recibe 403', () => {
    const r = ejecutar(autorizar, { method: 'GET', path: '/empleados', usuario: { id: '5', rol: 'INVITADO' } });
    assert.equal(r.status, 403);
});

test('las rutas públicas saltan la autorización', () => {
    const r = ejecutar(autorizar, { method: 'POST', path: '/auth/login' });
    assert.equal(r.siguio, true);
});
