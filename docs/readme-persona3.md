## Etapa E2E y reportes BDD (Persona 3)

La última etapa del pipeline levanta el sistema completo con Docker Compose, espera a que
esté sano, ejecuta la suite BDD del Reto 6 y publica sus reportes en Jenkins. Al terminar,
apaga todo con `docker compose down -v` para que las ejecuciones no se contaminen entre sí.

Definición: `e2e-tests/Jenkinsfile`. Scripts de apoyo: `scripts/wait-for-healthy.sh`
(espera con reintentos y tiempo máximo) y `docker-compose.e2e.yml` (entrega los scripts SQL
a las bases de datos mediante volúmenes, porque Jenkins corre dentro de un contenedor).

### Reportes publicados

| Etapa | Reporte | Paso de Jenkins | Dónde se consulta |
|---|---|---|---|
| E2E Tests | Resultados de los 33 escenarios (JUnit) | `junit` | Pestaña **Resultado de los tests** y gráfica de tendencia |
| E2E Tests | Escenarios BDD en español | `cucumber(...)` | Menú **Cucumber reports** |
| E2E Tests | Reporte HTML de la suite | `publishHTML(...)` | Menú **Reporte BDD** (Jenkins bloquea parte de su contenido; el reporte navegable es Cucumber reports) |
| E2E Tests | Logs de todos los contenedores | `archiveArtifacts` | **Last Successful Artifacts**: `docker-logs.txt` |

### Pipeline exitoso
![Stage View con builds en verde](docs/img/e2e-stage-view.png)

### Resultados de las pruebas
![Resultado de los tests: 33 pasan](docs/img/e2e-test-result.png)

### Reporte BDD navegable
![Características en Cucumber reports](docs/img/e2e-cucumber-features.png)
![Escenario con pasos en español](docs/img/e2e-cucumber-escenario.png)

### Fallo simulado
Se cambió a propósito el último paso del escenario *Programar vacaciones válidas* para que
esperara una notificación con el texto "falso". El pipeline terminó en **amarillo (UNSTABLE)**
en la etapa E2E Tests, y el reporte identifica el escenario, el paso exacto y el error
(*Tras 30s no se cumplió*), sin abrir el log de consola. Al revertir, el build volvió a verde.
![Build fallido con el escenario identificado](docs/img/e2e-build-fallido.png)

### Decisiones técnicas
- **Reportes:** la suite corre en un contenedor `node:22` que escribe directo en el workspace
  de Jenkins, así no se necesita `docker cp`.
- **`|| true` en cucumber:** deja llegar al bloque `post`; es `junit` quien marca el build
  como inestable si falla un escenario.
- **`allowEmptyResults`:** si el sistema no arranca, el build ya queda en FAILURE y no se
  añade un segundo error por falta de reporte.
- **Aislamiento de datos:** `down -v` al inicio y al final de cada ejecución.
- **Fallo intermitente encontrado:** el build #3 falló en 4 s porque se ejecutaba a la vez
  que otro (mismos puertos y nombres de contenedor). Se corrigió con
  `disableConcurrentBuilds()` y `lock('e2e-sistema')`.