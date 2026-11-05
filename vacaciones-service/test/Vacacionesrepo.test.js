// Pruebas del repositorio contra SQLite EN MEMORIA (node:sqlite): sin disco, red ni Docker.
// DB_PATH debe fijarse ANTES de cargar el repositorio, porque db.js abre la base al importarse.
process.env.DB_PATH = ':memory:';

const test = require('node:test');
const assert = require('node:assert/strict');
const repo = require('../src/vacacionesRepo');

const ANIO = new Date().getFullYear();

test('crear: persiste el período como PROGRAMADA y genera el id V-AAAA-NNNN', () => {
    const v = repo.crear({ empleadoId: 'E-1', email: 'ana@empresa.com', fechaInicio: '2030-01-01', fechaFin: '2030-01-10' });

    assert.match(v.id, new RegExp(`^V-${ANIO}-\\d{4}$`));
    assert.equal(v.estado, 'PROGRAMADA');
    assert.equal(v.empleadoId, 'E-1');
    assert.equal(v.fechaInicio, '2030-01-01');
    assert.equal(v.fechaFin, '2030-01-10');
    assert.ok(v.fechaCreacion);
});

test('crear: el email es opcional y se guarda como null si no llega', () => {
    const v = repo.crear({ empleadoId: 'E-2', fechaInicio: '2030-02-01', fechaFin: '2030-02-05' });
    const [conEmail] = repo.listarParaIniciar('2030-12-31').filter((p) => p.id === v.id);

    assert.equal(conEmail.email, null);
});

test('obtenerPorId: devuelve el período y null si no existe', () => {
    const v = repo.crear({ empleadoId: 'E-3', fechaInicio: '2030-03-01', fechaFin: '2030-03-05' });

    assert.deepEqual(repo.obtenerPorId(v.id), v);
    assert.equal(repo.obtenerPorId('V-0000-9999'), null);
});

test('listarTodas y listarPorEmpleado: filtran por empleado', () => {
    const a = repo.crear({ empleadoId: 'E-LISTA', fechaInicio: '2030-04-01', fechaFin: '2030-04-03' });
    repo.crear({ empleadoId: 'E-OTRO', fechaInicio: '2030-04-01', fechaFin: '2030-04-03' });

    const delEmpleado = repo.listarPorEmpleado('E-LISTA');
    assert.equal(delEmpleado.length, 1);
    assert.equal(delEmpleado[0].id, a.id);
    assert.ok(repo.listarTodas().length >= 2);
    assert.deepEqual(repo.listarPorEmpleado('NO-EXISTE'), []);
});

test('buscarSolapamiento: encuentra el período activo que se cruza y devuelve null si no hay cruce', () => {
    const existente = repo.crear({ empleadoId: 'E-SOL', fechaInicio: '2030-05-10', fechaFin: '2030-05-20' });

    assert.equal(repo.buscarSolapamiento('E-SOL', '2030-05-15', '2030-05-25').id, existente.id);
    assert.equal(repo.buscarSolapamiento('E-SOL', '2030-05-21', '2030-05-30'), null);
    assert.equal(repo.buscarSolapamiento('OTRO-EMPLEADO', '2030-05-15', '2030-05-25'), null);
});

test('buscarSolapamiento: ignora los períodos CANCELADA', () => {
    const v = repo.crear({ empleadoId: 'E-CANC', fechaInicio: '2030-06-01', fechaFin: '2030-06-10' });
    const cancelada = repo.cancelar(v.id);

    assert.equal(cancelada.estado, 'CANCELADA');
    assert.equal(repo.buscarSolapamiento('E-CANC', '2030-06-05', '2030-06-08'), null);
});

test('listarParaIniciar: PROGRAMADA cuya fechaInicio ya llegó (hoy o antes), con email', () => {
    const pasado = repo.crear({ empleadoId: 'E-INI', email: 'a@x.com', fechaInicio: '2000-01-01', fechaFin: '2000-01-05' });
    const hoy = repo.crear({ empleadoId: 'E-INI', email: 'a@x.com', fechaInicio: '2000-02-01', fechaFin: '2000-02-05' });
    const futuro = repo.crear({ empleadoId: 'E-INI', fechaInicio: '2000-03-01', fechaFin: '2000-03-05' });

    const ids = repo.listarParaIniciar('2000-02-01').map((p) => p.id);

    assert.ok(ids.includes(pasado.id), 'recupera lo pendiente de días anteriores');
    assert.ok(ids.includes(hoy.id), 'incluye el que empieza hoy');
    assert.ok(!ids.includes(futuro.id), 'excluye el que empieza en el futuro');
    assert.equal(repo.listarParaIniciar('2000-02-01').find((p) => p.id === pasado.id).email, 'a@x.com');
});

test('transicionar: cambia el estado una sola vez (condicionado al estado actual)', () => {
    const v = repo.crear({ empleadoId: 'E-TR', fechaInicio: '2001-01-01', fechaFin: '2001-01-05' });

    assert.equal(repo.transicionar(v.id, 'PROGRAMADA', 'EN_CURSO'), true);
    assert.equal(repo.obtenerPorId(v.id).estado, 'EN_CURSO');
    // segunda ejecución concurrente: el estado ya no es PROGRAMADA, no debe volver a cambiar
    assert.equal(repo.transicionar(v.id, 'PROGRAMADA', 'EN_CURSO'), false);
});

test('listarParaFinalizar: EN_CURSO cuya fechaFin es estrictamente anterior a hoy', () => {
    const vencido = repo.crear({ empleadoId: 'E-FIN', fechaInicio: '2002-01-01', fechaFin: '2002-01-05' });
    const terminaHoy = repo.crear({ empleadoId: 'E-FIN', fechaInicio: '2002-02-01', fechaFin: '2002-02-05' });
    const programado = repo.crear({ empleadoId: 'E-FIN', fechaInicio: '2002-03-01', fechaFin: '2002-03-05' });
    repo.transicionar(vencido.id, 'PROGRAMADA', 'EN_CURSO');
    repo.transicionar(terminaHoy.id, 'PROGRAMADA', 'EN_CURSO');

    const ids = repo.listarParaFinalizar('2002-02-05').map((p) => p.id);

    assert.ok(ids.includes(vencido.id));
    assert.ok(!ids.includes(terminaHoy.id), 'el último día aún cuenta como en curso');
    assert.ok(!ids.includes(programado.id), 'solo EN_CURSO se finaliza');
});