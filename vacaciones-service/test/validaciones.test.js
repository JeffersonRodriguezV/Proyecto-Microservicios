const test = require('node:test');
const assert = require('node:assert/strict');
const {
    validarCamposObligatorios, validarOrdenFechas, validarNoPasada, hayCruce,
} = require('../src/validaciones');
const { contarDiasHabiles } = require('../src/diasHabiles');

// ---------- Campos obligatorios ----------
test('campos obligatorios: acepta una solicitud completa', () => {
    assert.equal(validarCamposObligatorios({ empleadoId: 'E001', fechaInicio: '2026-11-02', fechaFin: '2026-11-06' }), null);
});

for (const faltante of ['empleadoId', 'fechaInicio', 'fechaFin']) {
    test(`campos obligatorios: rechaza si falta ${faltante}`, () => {
        const body = { empleadoId: 'E001', fechaInicio: '2026-11-02', fechaFin: '2026-11-06' };
        delete body[faltante];
        assert.match(validarCamposObligatorios(body), /obligatorios/);
    });
}

// ---------- Validación 1: orden de fechas ----------
test('orden de fechas: fechaFin posterior es válida', () => {
    assert.equal(validarOrdenFechas('2026-11-02', '2026-11-06'), null);
});
test('orden de fechas: un solo día (fin == inicio) es válido', () => {
    assert.equal(validarOrdenFechas('2026-11-02', '2026-11-02'), null);
});
test('orden de fechas: fechaFin anterior a fechaInicio se rechaza (caso de error)', () => {
    assert.match(validarOrdenFechas('2026-11-06', '2026-11-02'), /anterior a fechaInicio/);
});

// ---------- Validación 2: fecha pasada ----------
const HOY = '2026-10-13';
test('fecha pasada: inicio de ayer se rechaza (caso de error)', () => {
    assert.match(validarNoPasada('2026-10-12', HOY), /fecha pasada/);
});
test('fecha pasada: inicio igual a hoy es válido (borde)', () => {
    assert.equal(validarNoPasada('2026-10-13', HOY), null);
});
test('fecha pasada: inicio futuro es válido', () => {
    assert.equal(validarNoPasada('2026-10-14', HOY), null);
});
test('fecha pasada: compara solo por día aunque llegue con hora', () => {
    assert.equal(validarNoPasada('2026-10-13T00:00:00Z', HOY), null);
});

// ---------- Validación 3: solapamiento (tabla de casos borde) ----------
// Período existente: 10 al 20 de noviembre.
const EXISTENTE = ['2026-11-10', '2026-11-20'];
const casosSolapamiento = [
    { caso: 'idéntico',                         nuevo: ['2026-11-10', '2026-11-20'], cruza: true  },
    { caso: 'empieza dentro del existente',     nuevo: ['2026-11-15', '2026-11-25'], cruza: true  },
    { caso: 'termina dentro del existente',     nuevo: ['2026-11-05', '2026-11-15'], cruza: true  },
    { caso: 'está contenido en el existente',   nuevo: ['2026-11-12', '2026-11-18'], cruza: true  },
    { caso: 'contiene al existente',            nuevo: ['2026-11-05', '2026-11-25'], cruza: true  },
    { caso: 'lo toca por un día (al final)',    nuevo: ['2026-11-20', '2026-11-25'], cruza: true  },
    { caso: 'lo toca por un día (al inicio)',   nuevo: ['2026-11-05', '2026-11-10'], cruza: true  },
    { caso: 'termina el día anterior',          nuevo: ['2026-11-01', '2026-11-09'], cruza: false },
    { caso: 'empieza el día siguiente',         nuevo: ['2026-11-21', '2026-11-30'], cruza: false },
    { caso: 'muy anterior',                     nuevo: ['2026-10-01', '2026-10-05'], cruza: false },
    { caso: 'muy posterior',                    nuevo: ['2026-12-01', '2026-12-05'], cruza: false },
];
for (const { caso, nuevo, cruza } of casosSolapamiento) {
    test(`solapamiento: ${caso} -> ${cruza ? 'se cruza' : 'no se cruza'}`, () => {
        assert.equal(hayCruce(...EXISTENTE, ...nuevo), cruza);
    });
}
test('solapamiento: es simétrico (el orden de los períodos no importa)', () => {
    for (const { nuevo } of casosSolapamiento) {
        assert.equal(hayCruce(...EXISTENTE, ...nuevo), hayCruce(...nuevo, ...EXISTENTE));
    }
});

// ---------- Validación 4: cálculo de días hábiles ----------
test('días hábiles: semana completa lunes-viernes = 5', () => {
    assert.equal(contarDiasHabiles('2026-11-02', '2026-11-06'), 5);
});
test('días hábiles: un solo día hábil = 1', () => {
    assert.equal(contarDiasHabiles('2026-11-04', '2026-11-04'), 1);
});
test('días hábiles: un solo sábado = 0', () => {
    assert.equal(contarDiasHabiles('2026-11-07', '2026-11-07'), 0);
});
test('días hábiles: cruzar un fin de semana excluye sábado y domingo', () => {
    // viernes 6 al lunes 9 -> viernes + lunes = 2
    assert.equal(contarDiasHabiles('2026-11-06', '2026-11-09'), 2);
});
test('días hábiles: dos semanas completas = 10', () => {
    assert.equal(contarDiasHabiles('2026-11-02', '2026-11-13'), 10);
});
