# Convención de CI del proyecto (Reto 7)

Reglas comunes para que los pipelines de **todos** los microservicios se lean igual, se comporten igual
y se puedan operar sin conocer el stack de cada uno. Cada servicio tiene su propio `Jenkinsfile`
versionado junto a su código y un job definido como código en `jenkins/casc/jobs.yaml`.

## 1. Nombres compartidos

| Elemento | Valor | Dónde se usa |
|---|---|---|
| Servidor SonarQube (Jenkins) | `sonarqube` | `withSonarQubeEnv('sonarqube')` |
| Credencial del token de Sonar | `sonar-token` | cargada por JCasC desde `SONAR_TOKEN` del `.env` |
| Credenciales del E2E | `env-sistema`, `env-e2e` | *Secret file* con `.env` y `e2e-tests/.env` |
| Imagen Docker | `localhost:5000/microservicios/<servicio>:<BUILD_NUMBER>` y `:latest` | etapa Package & Publish |
| Red Docker compartida | `proyecto-microservicios_microservices-network` | agentes que hablan con Sonar o con el sistema |
| Quality Gate | `cobertura-70` | asignado a cada proyecto en Sonar |
| Clave de proyecto Sonar | igual al nombre del servicio (`vacaciones-service`, `empleados-service`) | `sonar-project.properties` / `pom.xml` |
| Puertos | Jenkins `9090`, SonarQube `9000`, Registry `5000` | `docker-compose.ci.yml` |

## 2. Etapas y orden

Todos los pipelines usan sintaxis **declarativa**, `agent none` a nivel de pipeline y un agente por etapa.

1. **Checkout** — obtiene el código y lo guarda con `stash` para las etapas siguientes.
2. **Build & Test** — compila y ejecuta pruebas en un contenedor efímero con la herramienta del stack (Maven, Node...). Publica `junit` y `recordCoverage` en `post { always { } }`.
3. **SonarQube** — análisis y envío de la cobertura generada en la etapa anterior.
4. **Quality Gate** — `waitForQualityGate abortPipeline: true`. Si el gate no pasa, el pipeline se detiene.
5. **Package & Publish** — construye la imagen, la etiqueta y la sube al registro local.
6. **E2E Tests** — levanta el sistema completo, ejecuta la suite BDD y publica sus reportes.

Las etapas 5 y 6 solo corren si el build va en verde (`currentBuild.currentResult == 'SUCCESS'`):
un servicio con pruebas fallidas **no** se empaqueta ni se publica.

## 3. FAILURE frente a UNSTABLE

| Estado | Color | Cuándo |
|---|---|---|
| `SUCCESS` | verde | todo pasó |
| `UNSTABLE` | amarillo | compiló, pero hay pruebas unitarias o escenarios BDD fallidos |
| `FAILURE` | rojo | no compila, falla el Quality Gate, el empaquetado o la infraestructura |

Las pruebas se ejecutan con `|| true` (o `-Dmaven.test.failure.ignore=true` en Maven) para que el pipeline llegue
al bloque `post` y publique el reporte **antes** de decidir el estado. Quien marca el build como `UNSTABLE` es el paso `junit`.

## 4. Reportes (rutas estándar)

| Stack | JUnit | Cobertura | Parser `recordCoverage` |
|---|---|---|---|
| Java / Maven | `target/surefire-reports/*.xml`, `target/failsafe-reports/*.xml` | `target/site/jacoco/jacoco.xml` | `JACOCO` |
| Node.js | `reports/junit.xml` | `reports/lcov.info` | `LCOV` |
| E2E (BDD) | `e2e-tests/reports/junit.xml` | — | `cucumber(...)` con `cucumber.json` |

`recordCoverage` necesita `sourceDirectories` apuntando a la carpeta desde la que se resuelven las rutas del reporte
(Java: `src/main/java`; Node: la carpeta del servicio, porque lcov usa rutas `src/archivo.js`).

## 5. Parámetro `RUN_SONAR`

Cada pipeline tiene `booleanParam RUN_SONAR` (por defecto `true`). Desmarcarlo salta las etapas SonarQube y Quality Gate.
Sirve en equipos de 8 GB de RAM, donde SonarQube y el sistema completo no caben a la vez. **Los builds de entrega
se ejecutan siempre con `RUN_SONAR` marcado.**

## 6. Secretos

- Ningún secreto vive en un `Jenkinsfile` ni en el repositorio. `.env` y `e2e-tests/.env` están en `.gitignore`.
- El token de Sonar entra por la variable `SONAR_TOKEN` del `.env`, que JCasC convierte en la credencial `sonar-token`.
- `env-sistema` y `env-e2e` se crean en **Manage Jenkins → Credentials → System → Global**.
  JCasC borra las credenciales creadas a mano cuando Jenkins se recrea; hay que volver a crearlas.

## 7. Cachés y limpieza

- Dependencias en volúmenes con nombre (`maven_cache`, `npm_cache`) para no descargar todo en cada build.
- Tras `Package & Publish` se borra la imagen local (`docker rmi`); queda solo la del registro.
- Tras el E2E: `down -v`, borrado de los volúmenes `e2e_init_*` y de los `.env` copiados al workspace.
- **Nunca** usar `--remove-orphans` ni `down -v` contra el stack local de desarrollo.

## 8. Cobertura: qué cuenta y qué no

Sonar cuenta **todos** los archivos de `src`, y Node solo informa de los que los tests cargaron. Por eso el Quality Gate
puede dar un valor más bajo que Jenkins. Se excluyen de la cobertura únicamente archivos sin lógica de negocio propia
(arranque, documentación OpenAPI, utilidades de desarrollo) y los adaptadores de infraestructura que se validan en el E2E
(publicador de eventos, cliente HTTP). Las exclusiones viven en `sonar.coverage.exclusions` y se justifican en el README de cada servicio.
