const test = require('node:test');
const assert = require('node:assert/strict');
const { crearServicioVacaciones } = require('../src/vacacionesService');

const HOY = '2026-10-13';

/**
 * Arma el servicio con TEST DOUBLES en memoria (ningún Docker, red ni base de datos):
 *  - repo: fake del repositorio (stub de buscarSolapamiento + spy de crear)
 *  - obtenerEmpleado: fake del cliente HTTP a empleados-service
 *  - publicar: spy del publicador de eventos
 * `llamadas` registra qué se invocó y `orden` en qué secuencia.
 */
function armar({ conflicto = null, empleado = { email: 'ana@empresa.com' } } = {}) {
    const llamadas = { buscarSolapamiento: [], crear: [], obtenerEmpleado: [], publicar: [] };
    const orden = [];

    const repo = {
        buscarSolapamiento: (...args) => {
            llamadas.buscarSolapamiento.push(args);
            return conflicto;
        },
        crear: (datos) => {
            llamadas.crear.push(datos);
            orden.push('crear');
            return { id: 'V-2026-0001', empleadoId: datos.empleadoId, fechaInicio: datos.fechaInicio,
                     fechaFin: datos.fechaFin, estado: 'PROGRAMADA' };
        },
    };
    const obtenerEmpleado = async (id) => {
        llamadas.obtenerEmpleado.push(id);
        return empleado;
    };
    const publicar = (tipo, datos) => {
        llamadas.publicar.push({ tipo, datos });
        orden.push('publicar');
    };

    const servicio = crearServicioVacaciones({ repo, obtenerEmpleado, publicar, hoy: () => HOY });
    return { servicio, llamadas, orden };
}

/** Un rechazo no debe dejar efectos: ni período creado ni evento publicado. */
function assertSinEfectos(llamadas) {
    assert.equal(llamadas.crear.length, 0, 'no debe crear el período');
    assert.equal(llamadas.publicar.length, 0, 'no debe publicar ningún evento');
}

const SOLICITUD = { empleadoId: '1', fechaInicio: '2026-11-02', fechaFin: '2026-11-06' };

// ---------- Camino feliz ----------
test('programar: crea el período, publica vacaciones.programadas y responde 201', async () => {
    const { servicio, llamadas } = armar();

    const r = await servicio.programar(SOLICITUD);

    assert.equal(r.ok, true);
    assert.equal(r.status, 201);
    assert.equal(r.periodo.id, 'V-2026-0001');
    assert.equal(llamadas.crear.length, 1);
    assert.equal(llamadas.crear[0].email, 'ana@empresa.com', 'guarda el email del empleado');
    assert.equal(llamadas.publicar.length, 1);
    assert.deepEqual(llamadas.publicar[0], {
        tipo: 'vacaciones.programadas',
        datos: {
            vacacionesId: 'V-2026-0001',
            empleadoId: '1',
            email: 'ana@empresa.com',
            fechaInicio: '2026-11-02',
            fechaFin: '2026-11-06',
            diasHabiles: 5,
        },
    });
});

test('programar: un período de un solo día es válido y cuenta 1 día hábil', async () => {
    const { servicio, llamadas } = armar();
    const r = await servicio.programar({ empleadoId: '1', fechaInicio: '2026-11-04', fechaFin: '2026-11-04' });
    assert.equal(r.status, 201);
    assert.equal(llamadas.publicar[0].datos.diasHabiles, 1);
});

test('programar: publica solo DESPUÉS de persistir', async () => {
    const { servicio, orden } = armar();
    await servicio.programar(SOLICITUD);
    assert.deepEqual(orden, ['crear', 'publicar']);
});

// ---------- Rechazos (casos de error) ----------
test('rechaza con 400 si faltan campos obligatorios', async () => {
    const { servicio, llamadas } = armar();
    const r = await servicio.programar({ empleadoId: '1' });
    assert.equal(r.ok, false);
    assert.equal(r.status, 400);
    assert.match(r.mensaje, /obligatorios/);
    assertSinEfectos(llamadas);
});

test('rechaza con 400 si el body llega vacío o indefinido', async () => {
    const { servicio, llamadas } = armar();
    const r = await servicio.programar(undefined);
    assert.equal(r.status, 400);
    assertSinEfectos(llamadas);
});

test('rechaza fechas incoherentes SIN consultar solapamiento ni al empleado', async () => {
    const { servicio, llamadas } = armar({ empleado: null }); // aunque el empleado no exista, falla antes por fechas
    const r = await servicio.programar({ empleadoId: '1', fechaInicio: '2026-11-06', fechaFin: '2026-11-02' });
    assert.equal(r.status, 400);
    assert.match(r.mensaje, /anterior a fechaInicio/);
    assert.equal(llamadas.buscarSolapamiento.length, 0);
    assert.equal(llamadas.obtenerEmpleado.length, 0);
    assertSinEfectos(llamadas);
});

test('rechaza una fecha de inicio pasada, usando el "hoy" inyectado', async () => {
    const { servicio, llamadas } = armar();
    const r = await servicio.programar({ empleadoId: '1', fechaInicio: '2026-10-12', fechaFin: '2026-10-20' });
    assert.equal(r.status, 400);
    assert.match(r.mensaje, /fecha pasada/);
    assertSinEfectos(llamadas);
});

test('rechaza el solapamiento e informa el período en conflicto, sin consultar al empleado', async () => {
    const conflicto = { id: 'V-2026-0009', empleadoId: '1', fechaInicio: '2026-11-01', fechaFin: '2026-11-05', estado: 'PROGRAMADA' };
    const { servicio, llamadas } = armar({ conflicto });

    const r = await servicio.programar(SOLICITUD);

    assert.equal(r.status, 400);
    assert.match(r.mensaje, /se cruza/);
    assert.deepEqual(r.extra.periodoConflicto, conflicto);
    assert.deepEqual(llamadas.buscarSolapamiento[0], ['1', '2026-11-02', '2026-11-06']);
    assert.equal(llamadas.obtenerEmpleado.length, 0);
    assertSinEfectos(llamadas);
});

test('rechaza si el empleado no existe (el cliente HTTP devuelve null)', async () => {
    const { servicio, llamadas } = armar({ empleado: null });
    const r = await servicio.programar({ ...SOLICITUD, empleadoId: '99' });
    assert.equal(r.status, 400);
    assert.equal(r.mensaje, "El empleado con id '99' no existe");
    assert.deepEqual(llamadas.obtenerEmpleado, ['99']);
    assertSinEfectos(llamadas);
});

test('normaliza empleadoId a texto al hablar con el repositorio (el JSON puede traerlo como número)', async () => {
    // Regresión: la columna empleado_id es TEXT y node:sqlite guarda el número 1 como "1.0",
    // lo que rompía la detección de solapamientos entre solicitudes con id numérico y con id texto.
    const { servicio, llamadas } = armar();
    const r = await servicio.programar({ empleadoId: 1, fechaInicio: '2026-11-02', fechaFin: '2026-11-06' });

    assert.equal(r.status, 201);
    assert.deepEqual(llamadas.buscarSolapamiento[0], ['1', '2026-11-02', '2026-11-06']);
    assert.strictEqual(llamadas.crear[0].empleadoId, '1');
});
