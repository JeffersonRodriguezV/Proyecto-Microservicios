// Pruebas de /metrics (prom-client) y del middleware de métricas.
const test = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const { middlewareMetrics, registrarRutas } = require('../src/observabilidad');

async function conServidor(fn) {
    const app = express();
    app.use(middlewareMetrics);
    registrarRutas(app);
    app.get('/ok', (_req, res) => res.json({ ok: true }));
    app.get('/falla', (_req, res) => res.status(500).json({ error: 'x' }));
    const srv = app.listen(0);
    try { await fn(`http://127.0.0.1:${srv.address().port}`); } finally { srv.close(); }
}

test('/metrics es público y expone métricas del proceso (CPU/memoria)', async () => {
    await conServidor(async (base) => {
        const r = await fetch(`${base}/metrics`);
        assert.equal(r.status, 200);
        const texto = await r.text();
        assert.match(texto, /process_cpu_seconds_total/);
        assert.match(texto, /process_resident_memory_bytes/);
    });
});

test('cuenta las peticiones por código HTTP y registra la latencia', async () => {
    await conServidor(async (base) => {
        await fetch(`${base}/ok`);
        await fetch(`${base}/falla`);
        const texto = await (await fetch(`${base}/metrics`)).text();
        assert.match(texto, /http_requests_total\{method="GET",status="200"\} \d+/);
        assert.match(texto, /http_requests_total\{method="GET",status="500"\} \d+/);
        assert.match(texto, /http_request_duration_seconds_bucket\{.*status="200"/);
    });
});
