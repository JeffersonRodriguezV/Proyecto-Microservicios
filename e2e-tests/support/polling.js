/**
 * Reintenta `consulta` hasta que devuelva un valor "verdadero" o se acabe el tiempo.
 * - consulta: async () => valor (falsy = aun no se cumple). Las excepciones de red cuentan como "aun no".
 * - Parametros por defecto: 20 intentos x 1,5 s = 30 s maximo.
 *   Justificacion: un evento por RabbitMQ tarda del orden de cientos de ms; 30 s cubre un consumidor
 *   lento o reconectando sin hacer lenta la suite cuando todo va bien (en el caso normal sale al 1.er o 2.o intento).
 * - Al agotarse lanza un Error que dice QUE se esperaba y cual fue el ultimo valor visto.
 */
async function esperarHastaQue(descripcion, consulta, { intentos = 20, intervaloMs = 1500 } = {}) {
    let ultimo;
    for (let i = 1; i <= intentos; i++) {
        try {
            ultimo = await consulta();
            if (ultimo) return ultimo;
        } catch (e) {
            ultimo = `error: ${e.message}`;
        }
        if (i < intentos) await new Promise((r) => setTimeout(r, intervaloMs));
    }
    throw new Error(`Tras ${intentos * intervaloMs / 1000}s no se cumplio: ${descripcion}. Ultimo valor: ${JSON.stringify(ultimo)}`);
}

/** Para condiciones NEGATIVAS ("no debe ocurrir"): exige que la condicion se mantenga todo el tiempo. */
async function verificarQueNoOcurre(descripcion, ocurrio, { duracionMs = 6000, intervaloMs = 1000 } = {}) {
    const fin = Date.now() + duracionMs;
    while (Date.now() < fin) {
        if (await ocurrio()) throw new Error(`Ocurrio algo que no debia: ${descripcion}`);
        await new Promise((r) => setTimeout(r, intervaloMs));
    }
}

module.exports = { esperarHastaQue, verificarQueNoOcurre };