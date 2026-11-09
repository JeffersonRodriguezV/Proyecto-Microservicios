# Sistema de Onboarding/Offboarding de Empleados — Retos 4, 5 y 7

Sistema de microservicios para gestionar empleados, con comunicación REST
síncrona, comunicación asincrónica por eventos a través de un message broker y,
desde el Reto 5, **autenticación con JWT**, control de acceso por rol y ciclo de
vida automático de las cuentas. Un solo evento (por ejemplo, crear un empleado)
dispara acciones automáticas e independientes en otros servicios.

## Arquitectura

```
Cliente HTTP (curl, Postman, navegador)
        │   único punto de entrada: http://localhost:8080
        ▼
   api-gateway  (valida el JWT y aplica RBAC antes de reenviar)
        ├── /auth/*            ──► auth-service             ──► MySQL (db-auth)
        ├── /empleados/*       ──► empleados-service        ──► MySQL
        ├── /departamentos/*   ──► departamentos-service    ──► PostgreSQL
        ├── /perfiles/*        ──► perfiles-service         ──► SQLite
        ├── /notificaciones/*  ──► notificaciones-service   ──► SQLite
        └── /vacaciones/*      ──► vacaciones-service       ──► SQLite

   message-broker (RabbitMQ) — exchange "ecosistema.eventos"
     empleados-service  ── publica ──► empleado.creado / actualizado / retirado
     vacaciones-service ── publica ──► vacaciones.programadas / iniciadas / finalizadas
     auth-service       ── publica ──► usuario.creado / usuario.recuperacion
                                       cuenta.activada / cuenta.desactivada
     perfiles, notificaciones, auth-service ◄── consumen
```

Comunicación síncrona: `empleados-service` → `departamentos-service` (valida el
departamento) y `vacaciones-service` → `empleados-service` (valida el empleado).
Solo el Gateway (`8080`) y la interfaz del broker (`15672`) se publican al host;
el resto usa `expose:` y solo es alcanzable dentro de la red de Docker.

## Servicios y lenguajes

| Servicio | Lenguaje | Base de datos | Puerto interno | Rol |
|---|---|---|---|---|
| `api-gateway` | Node.js (Express) | — | 8080 (publicado) | Punto de entrada único; valida JWT y aplica RBAC |
| `auth-service` | Java (Spring Boot) | MySQL 8 | 8086 | Login, recuperación y cambio de contraseña, ciclo de vida de la cuenta |
| `empleados-service` | Java 21 (Spring Boot) | MySQL 8 | 8080 | CRUD, baja lógica, publica `empleado.*` |
| `departamentos-service` | Python (FastAPI) | PostgreSQL 16 | 8081 | CRUD de departamentos |
| `perfiles-service` | C# (.NET) | SQLite | 8083 | Consume `empleado.*`; REST de perfiles |
| `notificaciones-service` | Go | SQLite | 8084 | Consume eventos; historial de notificaciones |
| `vacaciones-service` | JavaScript (Node.js) | SQLite | 8085 | CRUD de vacaciones; scheduler de inicio y fin de períodos |
| `message-broker` | RabbitMQ 3 | — | 5672 (interno), 15672 (UI) | Mensajería asincrónica |

## Despliegue

Requisito: Docker Desktop corriendo.

```bash
git clone https://github.com/JeffersonRodriguezV/Proyecto-Microservicios.git
cd Proyecto-Microservicios
bash scripts/setup-secrets.sh   # genera .env con secretos aleatorios
                                # Windows: powershell -ExecutionPolicy Bypass -File scripts\setup-secrets.ps1
docker compose up --build
```

### Secretos y rotación

Ninguna contraseña ni clave está en el repositorio. `.env.example` solo trae marcadores
(`CHANGE_ME`) y `scripts/setup-secrets.sh` (o `.ps1`) crea `.env` generando un valor
aleatorio por cada marcador: JWT, RabbitMQ, bases de datos, usuarios semilla y CI.
`.env` y `secrets/` están en `.gitignore`. Sin esas variables `docker compose` se detiene
con un mensaje claro: los secretos no tienen valor por defecto.

- El script es idempotente: no pisa valores que ya existen y solo agrega los que falten.
- **Rotar**: `bash scripts/setup-secrets.sh --rotate` (`-Rotar` en PowerShell). Las bases de
  datos y RabbitMQ guardan la clave anterior en sus volúmenes, así que después hay que
  recrearlos: `docker compose down -v && docker compose up --build` (borra los datos locales).
- `SONAR_TOKEN` se genera en SonarQube (Mi cuenta > Seguridad) y se pega a mano en `.env`.
- **CI**: la credencial `env-sistema` de Jenkins (archivo secreto) debe ser el `.env` generado.
  Tras actualizar `.env.example`, vuelva a subirla o los pipelines fallarán por variables faltantes.

En otra terminal, `docker compose ps` debe mostrar los **11 contenedores**
`healthy`. URL base de todo el sistema: `http://localhost:8080`. Interfaz del
broker: `http://localhost:15672` (usuario `admin`, contraseña `admin`).

- Detener conservando los datos: `docker compose down`
- Reiniciar desde cero (borra todos los datos): `docker compose down -v`

### Administrador semilla

Al arrancar, `auth-service` crea el usuario administrador:
`admin@empresa.com` con la contraseña de la variable `ADMIN_PASSWORD`
(por defecto `Admin123!`, solo para uso académico).

## Seguridad (Reto 5)

### Flujo de autenticación

1. `POST /auth/login` con `{email, password}` devuelve un JWT firmado (HS256).
2. El cliente envía `Authorization: Bearer <token>` en cada petición.
3. El Gateway valida firma y vigencia **antes de reenviar** y aplica las reglas
   de acceso. `auth-service` y el Gateway comparten el secreto vía `JWT_SECRET`.
4. El claim `sub` del token es el `empleadoId`; el claim `role` es `ADMIN` o `USER`.

### Reglas de acceso (Gateway)

| Situación | Respuesta |
|---|---|
| Sin token, token inválido, alterado, vencido o que no es de acceso | `401 Unauthorized` |
| `ADMIN` | Permitido en todo |
| `USER`, métodos de lectura (`GET`, `HEAD`, `OPTIONS`) | Permitido |
| `USER`, `POST /auth/change-password` | Permitido (cambia su propia contraseña) |
| `USER`, `PUT /perfiles/{id}` con `id == sub` | Permitido (propiedad del recurso) |
| Cualquier otro caso de `USER` | `403 Forbidden` |

Rutas **públicas** (sin token): `POST /auth/login`, `POST /auth/recover-password`,
`POST /auth/reset-password`, `GET /health` y la documentación Swagger.
Si el servicio destino está caído, el Gateway responde `503` en JSON.

### Endpoints de `auth-service`

| Endpoint | Descripción |
|---|---|
| `POST /auth/login` | Devuelve el JWT. Una cuenta que no está activa responde `401`, igual que una credencial incorrecta |
| `POST /auth/recover-password` | Genera un token de recuperación y publica `usuario.recuperacion`. Responde igual exista o no el correo |
| `POST /auth/reset-password` | Establece la contraseña con el token (activación inicial o recuperación) |
| `POST /auth/change-password` | Cambia la contraseña del usuario autenticado |

## Ciclo de vida de la cuenta

`auth-service` modela el estado de la cuenta (no un booleano):
`PENDIENTE_ACTIVACION → ACTIVA ⇄ SUSPENDIDA_TEMPORAL`, y `DESACTIVADA_PERMANENTE`
cuando el empleado es retirado.

| Evento consumido | Acción en `auth-service` | Evento publicado |
|---|---|---|
| `empleado.creado` | Crea la cuenta inactiva con rol `USER` y un token de activación | `usuario.creado` |
| `vacaciones.iniciadas` | Suspende la cuenta de forma temporal | `cuenta.desactivada` (`VACACIONES`, `permanente: false`) |
| `vacaciones.finalizadas` | Reactiva la cuenta **solo si** estaba suspendida | `cuenta.activada` (`FIN_VACACIONES`) |
| `empleado.retirado` | Desactiva la cuenta de forma permanente | `cuenta.desactivada` (`RETIRO`, `permanente: true`) |

**Caso borde:** si un empleado es retirado durante sus vacaciones, al llegar
`vacaciones.finalizadas` la cuenta **no se reactiva**; el servicio registra la
advertencia y el login sigue fallando.

### Diagrama de secuencia

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant G as api-gateway
    participant A as auth-service
    participant V as vacaciones-service (scheduler)
    participant B as RabbitMQ
    participant N as notificaciones-service

    C->>G: POST /auth/login (empleado)
    G->>A: reenvía
    A-->>C: 200 + JWT

    Note over V: cada 60 s revisa los períodos
    V->>B: vacaciones.iniciadas
    B->>A: vacaciones.iniciadas
    A->>A: ACTIVA → SUSPENDIDA_TEMPORAL
    A->>B: cuenta.desactivada (VACACIONES, permanente=false)
    B->>N: cuenta.desactivada
    N->>N: log [NOTIFICACIÓN] Tipo: CUENTA

    C->>G: POST /auth/login
    A-->>C: 401 (cuenta no activa)

    V->>B: vacaciones.finalizadas
    B->>A: vacaciones.finalizadas
    alt la cuenta estaba SUSPENDIDA_TEMPORAL
        A->>A: SUSPENDIDA_TEMPORAL → ACTIVA
        A->>B: cuenta.activada (FIN_VACACIONES)
        B->>N: cuenta.activada
        C->>G: POST /auth/login
        A-->>C: 200 + JWT
    else fue retirado durante las vacaciones (DESACTIVADA_PERMANENTE)
        A->>A: ignora el evento (no reactiva)
        C->>G: POST /auth/login
        A-->>C: 401
    end
```

### Scheduler de vacaciones

`vacaciones-service` revisa cada `SCHEDULER_INTERVAL_SECONDS` (60 por defecto) y,
en la zona horaria `TZ` (`America/Bogota`): primero pasa a `FINALIZADA` los
períodos `EN_CURSO` cuya `fechaFin` es anterior a hoy y después pasa a `EN_CURSO`
los `PROGRAMADA` cuya `fechaInicio` ya llegó. Un período de un solo día
(`fechaFin == fechaInicio`) es válido: inicia ese día y termina al siguiente.

- **Limitación (instancia única):** el scheduler corre dentro de cada instancia
  del servicio. Con varias instancias, el mismo evento se publicaría varias veces.
  La deduplicación en el consumidor mitiga el daño, pero no evita el trabajo
  duplicado; la coordinación en el productor (ShedLock) queda para un reto
  posterior. Detalle en [`vacaciones-service/README.md`](./vacaciones-service/README.md).
- **Solo desarrollo:** `POST /vacaciones/{id}/forzar-inicio` y `forzar-fin`
  existen solo si `DEV_ENDPOINTS=true` (variable `VACACIONES_DEV_ENDPOINTS`, activa
  por defecto para poder demostrar el flujo sin esperar días). Desactívela en
  cualquier entorno que no sea de pruebas.

## Rutas del Gateway y documentación OpenAPI

Todos los Swagger se ven **a través del Gateway** y soportan el esquema
`BearerAuth`: haga login, copie el token y péguelo en el botón **Authorize**.

| Ruta externa | Servicio | Documentación Swagger |
|---|---|---|
| `/auth/login`, `/auth/recover-password`, `/auth/reset-password`, `/auth/change-password` | `auth-service` | `http://localhost:8080/auth/swagger-ui.html` |
| `/empleados`, `/empleados/{id}` | `empleados-service` | `http://localhost:8080/empleados/swagger-ui.html` |
| `/departamentos`, `/departamentos/{id}` | `departamentos-service` | `http://localhost:8080/departamentos/docs` |
| `/perfiles`, `/perfiles/{empleadoId}` | `perfiles-service` | `http://localhost:8080/perfiles/swagger` |
| `/notificaciones`, `/notificaciones/{empleadoId}` | `notificaciones-service` | `http://localhost:8080/notificaciones/docs` |
| `/vacaciones`, `/vacaciones/{id}` | `vacaciones-service` | `http://localhost:8080/vacaciones/api-docs/` |
| `/health` | El propio Gateway | — |

Cada servicio publica su documentación bajo su propio prefijo, así el Gateway la
reenvía sin reglas adicionales, y declara el servidor `/` para que *Try it out*
use el host del Gateway.

## Eventos

Siguen el **Catálogo de Eventos** del ecosistema (nombres, envelope y cargas
útiles sin cambios). Todos usan el envelope `{id, type, version, occurredAt,
producer, data}` y se publican en el exchange `ecosistema.eventos` (tipo
`topic`), con el nombre del evento como routing key. Cada consumidor tiene su
propia cola durable.

## Integración continua (Reto 7)

Cada servicio tiene un pipeline declarativo en Jenkins que compila, prueba,
mide cobertura, valida la calidad en SonarQube, publica la imagen Docker y
ejecuta la suite E2E (BDD). El CI vive en su propio compose
(`docker-compose.ci.yml`), separado del sistema (`docker-compose.yml`): el CI
es infraestructura de desarrollo y no debe arrancar con el sistema. Detalle de
convenciones en [`docs/convencion-ci.md`](docs/convencion-ci.md).

### Acceso
- Jenkins: http://localhost:9090 (usuario y clave: `JENKINS_ADMIN_ID` / `JENKINS_ADMIN_PASSWORD` del `.env`).
  No hay contraseña inicial aleatoria que buscar: el asistente de configuración está desactivado
  (`runSetupWizard=false`) y el administrador lo crea JCasC con esas dos variables. Los valores los
  genera `scripts/setup-secrets` en su `.env`.
- SonarQube: http://localhost:9000 (usuario `admin`, clave `SONAR_ADMIN_PASSWORD` del `.env`).
- Registry local de imágenes: `localhost:5000`.

### Puesta en marcha
a. Crear los `.env` (`cp .env.example .env`, `cp e2e-tests/.env.example e2e-tests/.env`) y ajustar claves.
b. `docker compose -f docker-compose.ci.yml up -d`.
c. En SonarQube: Quality Gate `cobertura-70` por defecto, token en `SONAR_TOKEN` y webhook `http://jenkins:8080/sonarqube-webhook/`.
d. Recrear Jenkins (`up -d --force-recreate jenkins`). Los jobs ya existen (JCasC los importa al arrancar);
   para ejecutar uno a mano: Jenkins → job (`empleados-service`, `vacaciones-service` o `e2e-bdd`) →
   *Build Now* (en los servicios, *Build with Parameters* permite desmarcar `RUN_SONAR`).

Jenkins se configura solo (JCasC + Job DSL en `jenkins/casc/`): credenciales
`sonar-token`, `env-sistema` y `env-e2e`, servidor Sonar y los tres jobs. Guía
completa y problemas conocidos: [`docs/guia-reproducibilidad-ci.md`](docs/guia-reproducibilidad-ci.md).

### Etapas del pipeline
| Etapa | Qué hace | Si falla |
|---|---|---|
| Checkout | Clona el repo | FAILURE |
| Build & Test | Compila, ejecuta pruebas con cobertura (JaCoCo / c8) y análisis Sonar (`RUN_SONAR`) | Compilación: FAILURE; pruebas rotas: **UNSTABLE** |
| Quality Gate | Espera el veredicto de Sonar por webhook (`cobertura-70`: cobertura < 70 % falla) | FAILURE |
| Package & Publish | `docker build` y `push` al registry (solo si todo es SUCCESS) | FAILURE |
| E2E | Levanta el sistema completo y corre Cucumber en español (solo si todo es SUCCESS) | Escenarios rotos: **UNSTABLE** |

Color del build: azul/verde = SUCCESS, amarillo = UNSTABLE (algo se probó y falló),
rojo = FAILURE (el pipeline se detiene en esa etapa).

### Reportes
| Reporte | Dónde |
|---|---|
| Resultados de pruebas | Build → *Test Result* |
| Cobertura | Build → *Coverage Report*, y dashboard en SonarQube |
| BDD navegable | Build → *Cucumber Reports* y *Reporte BDD* (HTML) |
| Logs del sistema en E2E | Build → artefacto `docker-logs.txt` |

### Capturas
| Qué muestra | Imagen |
|---|---|
| Stage View, build verde | ![](docs/img/p1-stage-view.png) |
| Resultados de pruebas | ![](docs/img/p1-test-result.png) |
| Cobertura (Jenkins) | ![](docs/img/p2-coverage.png) |
| Cobertura y Quality Gate (Sonar) | ![](docs/img/p1-coverage.png) |
| Reporte BDD navegable | ![](docs/img/e2e-cucumber-features.png) |

### Fallos simulados
| Fallo | Etapa donde se detiene | Evidencia |
|---|---|---|
| Prueba unitaria rota | Build & Test → UNSTABLE | `docs/img/p1-fallo.png` |
| Cobertura bajo 70 % | Quality Gate → FAILURE | `docs/img/p2-sonar-fallo.png` |
| Escenario BDD roto | E2E → UNSTABLE | `docs/img/e2e-build-fallido.png` |
| Dockerfile roto (`COPY` a una ruta inexistente) | Package & Publish → FAILURE; E2E se omite | `docs/img/p4-fallo-dockerfile.png` |

El fallo de Dockerfile se simula con *Replay* de Jenkins: se modifica solo la
copia del workspace, el repositorio no se toca.

## Documentación adicional

- [`docs/arquitectura.md`](docs/arquitectura.md): servicios, decisiones y `/health`.
- [`docs/eventos.md`](docs/eventos.md): catálogo de eventos y deduplicación.
- [`docs/observabilidad-vacaciones-notificaciones.md`](docs/observabilidad-vacaciones-notificaciones.md): Reto 8, métricas, logs y trazas de vacaciones y notificaciones (traza del scheduler).
- [`docs/guias/`](docs/guias): despliegue local y ejecución de pruebas.
- [`scripts/init-db.sql`](scripts/init-db.sql) y [`scripts/seed-data.sql`](scripts/seed-data.sql): esquema de referencia y datos de ejemplo.
- Swagger agregado de todos los servicios: `http://localhost:8080/swagger`.
