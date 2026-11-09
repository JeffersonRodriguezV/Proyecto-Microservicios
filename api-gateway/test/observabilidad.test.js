// Pruebas de observabilidad del Gateway (Reto 8): /metrics público y métricas por petición.
process.env.JWT_SECRET = 'x'.repeat(40);

const test = require('node:test');
const assert = require('node:assert/strict');

let gateway, base;

test.before(async () => {
    const { app } = require('../src/app');
    gateway = await new Promise((resolve) => { const s = app.listen(0, '127.0.0.1', () => resolve(s)); });
    base = `http://127.0.0.1:${gateway.address().port}`;
});

test.after(() => gateway.close());

test('GET /metrics es público y usa el formato de Prometheus', async () => {
    const r = await fetch(`${base}/metrics`); // sin JWT
    assert.equal(r.status, 200);
    assert.match(r.headers.get('content-type'), /text\/plain/);
    assert.match(await r.text(), /process_cpu_user_seconds_total/);
});

test('cada petición se cuenta por método y código HTTP, también los 401', async () => {
    await fetch(`${base}/empleados`); // sin token: 401 antes de llegar al proxy
    const texto = await (await fetch(`${base}/metrics`)).text();
    assert.match(texto, /http_requests_total\{method="GET",status="401"\} [1-9]/);
    assert.match(texto, /http_request_duration_seconds_bucket\{.*status="401"/);
});

test('/health y /metrics no se escriben en el log (Prometheus los consulta cada 15 s)', async () => {
    const lineas = [];
    const original = process.stdout.write.bind(process.stdout);
    process.stdout.write = (chunk, ...r) => { lineas.push(String(chunk)); return original(chunk, ...r); };
    try {
        await fetch(`${base}/health`);
        await fetch(`${base}/metrics`);
        await fetch(`${base}/vacaciones`); // sí debe registrarse (401)
        await new Promise((r) => setTimeout(r, 50));
    } finally {
        process.stdout.write = original;
    }
    const jsons = lineas.filter((l) => l.startsWith('{') && l.includes('"peticion"')).map((l) => JSON.parse(l));
    assert.ok(jsons.some((j) => j.path === '/vacaciones' && j.status === 401 && j.service === 'api-gateway'));
    assert.ok(!jsons.some((j) => j.path === '/health' || j.path === '/metrics'));
});
