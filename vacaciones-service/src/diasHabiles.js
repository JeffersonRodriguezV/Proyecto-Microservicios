//Auxiliar para calcular los días hábiles entre dos fechas, excluyendo fines de semana y feriados.
/**
 * Cuenta los días hábiles (lunes a viernes) entre dos fechas,
 * ambas inclusive. No contempla festivos -- solo fines de semana.
 */
function contarDiasHabiles(fechaInicioStr, fechaFinStr) {
    let contador = 0;
    let actual = new Date(`${fechaInicioStr}T00:00:00Z`);
    const fin = new Date(`${fechaFinStr}T00:00:00Z`);

    while (actual <= fin) {
        const diaSemana = actual.getUTCDay(); // 0 = domingo, 6 = sábado
        if (diaSemana !== 0 && diaSemana !== 6) {
            contador++;
        }
        actual.setUTCDate(actual.getUTCDate() + 1);
    }

    return contador;
}

module.exports = { contarDiasHabiles };
