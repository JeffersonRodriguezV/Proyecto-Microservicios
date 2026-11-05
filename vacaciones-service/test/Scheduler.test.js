// Pruebas del scheduler: base SQLite en memoria + TEST DOUBLES del publicador de eventos y del
// cliente HTTP a empleados-service (se inyectan en require.cache ANTES de cargar el scheduler).
process.env.DB_PATH = ':memory:';
process.env.SCHEDULER_ENABLED = 'true';
process.env.SCHEDULER_INTERVAL_SECONDS = '5';

const test = require('node:test');
const assert = require('node:assert/strict');

const eventos = [];
const empleados = { respuesta: null };
const publicador = { falla: false };

function inyectar(modulo, exportaciones) {
    const ruta = require.resolve(modulo);
    require.cache[ruta] = { id: ruta, filename: ruta, loaded: true, exports: exportaciones };
}
inyectar('../src/eventoPublisher', {
    publicar: async (tipo, payload) => {
        if (publicador.falla) throw new Error('broker caído');
        eventos.push({ tipo, payload });
    },
});
inyectar('../src/empleadosClient', { obtenerEmpleado: async () => empleados.respuesta });

const repo = require('../src/vacacionesRepo');
const scheduler = require('../src/scheduler');

test.beforeEach(() => {
    eventos.length = 0;
    empleados.respuesta = null;
    publicador.falla = false;
});

test('hoyLocal: devuelve la fecha de hoy con formato AAAA-MM-DD', () => {
    assert.match(scheduler.hoyLocal(), /^\d{4}-\d{2}-\d{2}$/);
});

test('iniciarPeriodo: PROGRAMADA -> EN_CURSO y publica vacaciones.iniciadas con el email guardado', async () => {
    const v = repo.crear({ empleadoId: 'S-1', email: 'ana@empresa.com', fechaInicio: '2000-01-01', fechaFin: '2000-01-05' });

    const hizo = await scheduler.iniciarPeriodo({ ...v, email: 'ana@empresa.com' });

    assert.equal(hizo, true);
    assert.equal(repo.obtenerPorId(v.id).estado, 'EN_CURSO');
    assert.equal(eventos.length, 1);
    assert.equal(eventos[0].tipo, 'vacaciones.iniciadas');
    assert.deepEqual(eventos[0].payload, {
        vacacionesId: v.id, empleadoId: 'S-1', email: 'ana@empresa.com',
        fechaInicio: '2000-01-01', fechaFin: '2000-01-05',
    });
});

test('iniciarPeriodo: si el período ya cambió de estado no publica de nuevo (idempotente)', async () => {
    const v = repo.crear({ empleadoId: 'S-2', email: 'a@x.com', fechaInicio: '2000-01-01', fechaFin: '2000-01-05' });
    repo.transicionar(v.id, 'PROGRAMADA', 'EN_CURSO');

    assert.equal(await scheduler.iniciarPeriodo({ ...v, email: 'a@x.com' }), false);
    assert.equal(eventos.length, 0);
});

test('email: si el período no lo trae, se consulta a empleados-service como respaldo', async () => {
    const v = repo.crear({ empleadoId: 'S-3', fechaInicio: '2000-01-01', fechaFin: '2000-01-05' });
    empleados.respuesta = { email: 'respaldo@empresa.com' };

    await scheduler.iniciarPeriodo({ ...v, email: null });

    assert.equal(eventos[0].payload.email, 'respaldo@empresa.com');
});

test('email: si el empleado tampoco existe, el evento lleva email null', async () => {
    const v = repo.crear({ empleadoId: 'S-4', fechaInicio: '2000-01-01', fechaFin: '2000-01-05' });
    empleados.respuesta = null;

    await scheduler.iniciarPeriodo({ ...v, email: null });

    assert.equal(eventos[0].payload.email, null);
});

test('finalizarPeriodo: EN_CURSO -> FINALIZADA y publica vacaciones.finalizadas', async () => {
    const v = repo.crear({ empleadoId: 'S-5', email: 'b@x.com', fechaInicio: '2000-01-01', fechaFin: '2000-01-05' });
    repo.transicionar(v.id, 'PROGRAMADA', 'EN_CURSO');

    assert.equal(await scheduler.finalizarPeriodo({ ...v, email: 'b@x.com' }), true);
    assert.equal(repo.obtenerPorId(v.id).estado, 'FINALIZADA');
    assert.equal(eventos[0].tipo, 'vacaciones.finalizadas');
    assert.equal(eventos[0].payload.fechaFin, '2000-01-05');
});

test('finalizarPeriodo: si no está EN_CURSO no hace nada', async () => {
    const v = repo.crear({ empleadoId: 'S-6', email: 'c@x.com', fechaInicio: '2000-01-01', fechaFin: '2000-01-05' });

    assert.equal(await scheduler.finalizarPeriodo({ ...v, email: 'c@x.com' }), false);
    assert.equal(eventos.length, 0);
});

test('ejecutarCiclo: inicia lo que llegó y, en la pasada siguiente, finaliza lo vencido', async () => {
    const v = repo.crear({ empleadoId: 'S-7', email: 'd@x.com', fechaInicio: '2000-01-01', fechaFin: '2000-01-05' });

    await scheduler.ejecutarCiclo();
    assert.equal(repo.obtenerPorId(v.id).estado, 'EN_CURSO', 'primera pasada: inicia');
    assert.ok(eventos.some((e) => e.tipo === 'vacaciones.iniciadas' && e.payload.vacacionesId === v.id));

    await scheduler.ejecutarCiclo();
    assert.equal(repo.obtenerPorId(v.id).estado, 'FINALIZADA', 'segunda pasada: finaliza');
    assert.ok(eventos.some((e) => e.tipo === 'vacaciones.finalizadas' && e.payload.vacacionesId === v.id));
});

test('ejecutarCiclo: un error al publicar se registra y no tumba el scheduler', async (t) => {
    const consola = t.mock.method(console, 'error', () => {});
    repo.crear({ empleadoId: 'S-8', email: 'e@x.com', fechaInicio: '2000-01-01', fechaFin: '2000-01-05' });
    publicador.falla = true;

    await assert.doesNotReject(scheduler.ejecutarCiclo());
    assert.equal(consola.mock.callCount(), 1);
    assert.match(consola.mock.calls[0].arguments[1], /broker caído/);

    // el candado se libera: la siguiente pasada puede ejecutarse
    publicador.falla = false;
    await scheduler.ejecutarCiclo();
});

test('ejecutarCiclo: una pasada en curso bloquea a la que llega solapada', async () => {
    repo.crear({ empleadoId: 'S-9', email: 'f@x.com', fechaInicio: '2000-01-01', fechaFin: '2000-01-05' });

    const primera = scheduler.ejecutarCiclo();
    const segunda = scheduler.ejecutarCiclo();   // entra mientras la primera sigue ejecutando
    await Promise.all([primera, segunda]);

    const iniciadas = eventos.filter((e) => e.tipo === 'vacaciones.iniciadas' && e.payload.empleadoId === 'S-9');
    assert.equal(iniciadas.length, 1, 'el período se inicia una sola vez');
});

test('arrancar: programa la revisión periódica cuando está habilitado', (t) => {
    const intervalo = t.mock.method(globalThis, 'setInterval', () => 0);
    t.mock.method(console, 'log', () => {});

    scheduler.arrancar();

    assert.equal(intervalo.mock.callCount(), 1);
    assert.equal(intervalo.mock.calls[0].arguments[1], 5000);
});

test('arrancar: no programa nada si SCHEDULER_ENABLED=false', (t) => {
    process.env.SCHEDULER_ENABLED = 'false';
    delete require.cache[require.resolve('../src/scheduler')];
    const apagado = require('../src/scheduler');
    const intervalo = t.mock.method(globalThis, 'setInterval', () => 0);
    t.mock.method(console, 'log', () => {});

    apagado.arrancar();

    assert.equal(intervalo.mock.callCount(), 0);
});