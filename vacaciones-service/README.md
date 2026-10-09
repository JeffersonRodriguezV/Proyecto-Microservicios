# Vacaciones Service - Retos 4 y 5

Gestión de períodos de vacaciones de empleados, parte del sistema de onboarding/offboarding.
Es el servicio que **origina** la cadena de eventos de vacaciones:

- Al programar un período publica `vacaciones.programadas` (Reto 4).
- Un **scheduler** interno publica `vacaciones.iniciadas` y `vacaciones.finalizadas` al llegar las fechas (Reto 5).

Vacaciones no sabe nada de cuentas ni de autenticación: publica "este período comenzó" y quien reacciona
(`auth-service`, `notificaciones-service`) es asunto suyo.

## Lenguaje y stack

- **JavaScript (Node.js + Express)**
- **Base de datos:** SQLite, vía el módulo nativo `node:sqlite` (Node 22.5 o superior, sin dependencias externas)
- **Mensajería:** `amqplib` (cliente de RabbitMQ)
- **Scheduler:** `setInterval` nativo, sin librerías adicionales

### Por qué JavaScript

El enunciado del Reto 4 exige que `perfiles-service`, `notificaciones-service` y `vacaciones-service` usen
lenguajes diferentes entre sí y distintos de los de `empleados-service` y `departamentos-service`. JavaScript
ya se usó en `api-gateway` (Reto 3), pero no en ninguno de los dos servicios nombrados en esa restricción.
Se eligió por una razón práctica: el equipo ya tenía Node.js instalado y probado en varias máquinas.

## Modelo de datos

```json
{
  "id": "V-2026-0042",
  "empleadoId": "E001",
  "fechaInicio": "2026-03-15",
  "fechaFin": "2026-03-30",
  "estado": "PROGRAMADA",
  "fechaCreacion": "2026-03-01T10:00:00Z"
}
```

Estados: `PROGRAMADA` → `EN_CURSO` → `FINALIZADA`, o `CANCELADA`. El email del empleado se guarda aparte
(columna interna `email`, no expuesta por la API) para publicar los eventos del Catálogo sin consultar a Empleados.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/vacaciones` | Programa un período de vacaciones |
| `GET` | `/vacaciones/{id}` | Consulta un período por su id |
| `GET` | `/vacaciones?empleadoId={id}` | Lista los períodos de un empleado |
| `GET` | `/vacaciones` | Lista todos los períodos registrados |
| `DELETE` | `/vacaciones/{id}` | Cancela un período que aún no ha iniciado |
| `POST` | `/vacaciones/{id}/forzar-inicio` | **Solo desarrollo.** Fuerza el inicio de un período |
| `POST` | `/vacaciones/{id}/forzar-fin` | **Solo desarrollo.** Fuerza el fin de un período |
| `GET` | `/health` | Estado del servicio |

## Validaciones al programar un período

Todas responden `400 Bad Request` con mensaje descriptivo:

1. **Fechas incoherentes**: `fechaFin` no es posterior a `fechaInicio`.
2. **Fechas en el pasado**: `fechaInicio` es anterior a hoy. Se compara por fecha, en la zona horaria del servicio (`TZ`).
3. **Solapamiento**: el empleado ya tiene un período `PROGRAMADA` o `EN_CURSO` que se cruza con el solicitado.
   La respuesta incluye el período en conflicto (`periodoConflicto`).
4. **Empleado inexistente**: consulta síncrona a `empleados-service` (ver decisión técnica abajo).

## Scheduler (Reto 5)

Una tarea periódica interna recorre los períodos y dispara los eventos cuando llega la fecha:

| Condición | Transición | Evento publicado |
|---|---|---|
| `PROGRAMADA` con `fechaInicio <= hoy` | `EN_CURSO` | `vacaciones.iniciadas` (Catálogo 3.9) |
| `EN_CURSO` con `fechaFin < hoy` | `FINALIZADA` | `vacaciones.finalizadas` (Catálogo 3.10) |

Decisiones de diseño:

- **Mecanismo:** `setInterval` nativo. El enunciado permite `node-cron` o `setInterval`; se evita una dependencia nueva.
- **Frecuencia:** configurable con `SCHEDULER_INTERVAL_SECONDS` (60 por defecto, es decir, cada minuto, como sugiere el enunciado).
- **"Hoy":** se calcula en la zona horaria del proceso (`TZ`, en Docker `America/Bogota`), para que el cambio de día ocurra a la medianoche local y no a la UTC.
- **Recuperación tras una caída:** el inicio usa `<=` y el fin usa `<`. Si el servicio estuvo apagado, en la siguiente pasada procesa lo pendiente.
- **Orden:** cada pasada primero cierra los períodos vencidos y después abre los que llegaron.
  Un mismo período nunca inicia y termina en la misma pasada.
- **Una sola transición:** el cambio de estado es un `UPDATE ... WHERE estado = <anterior>`. Si dos ejecuciones coinciden
  (el scheduler y un endpoint de desarrollo, por ejemplo), solo una gana y solo esa publica el evento.
- **Publicación después de persistir:** si la publicación falla se registra el error y no se revierte la base de datos,
  igual que en el Reto 4.
- **Pasadas sin solaparse:** una bandera impide que una pasada empiece mientras la anterior sigue en curso.

### Cómo probarlo sin esperar (estrategia elegida)

De las tres estrategias del §3.3 del enunciado se eligió la **tercera**: endpoints de prueba
`POST /vacaciones/{id}/forzar-inicio` y `POST /vacaciones/{id}/forzar-fin`.

- Están marcados como **solo desarrollo** y solo existen si `DEV_ENDPOINTS=true`; sin esa variable no se registran.
- Ejecutan exactamente la misma lógica del scheduler (`iniciarPeriodo` y `finalizarPeriodo`), así que los eventos son idénticos.
- La restricción al rol `ADMIN` la aplica el API Gateway: son peticiones `POST` y el rol `USER` solo puede leer.
- Se descartó relajar la validación de fechas del Reto 4 (que `fechaFin` sea posterior a `fechaInicio`) para permitir
  `fechaInicio = fechaFin = hoy`. En su lugar, la demo programa `fechaInicio = hoy` y `fechaFin = mañana`;
  el scheduler inicia el período en menos de un minuto y `forzar-fin` lo cierra al instante.

Además, cualquier período con `fechaInicio = hoy` se activa solo en la siguiente pasada, sin tocar ningún endpoint.

### Limitaciones conocidas

1. **Varias instancias.** Un scheduler corre dentro de *cada* instancia del servicio. Con una sola funciona; con N instancias
   cada una intentaría disparar los mismos períodos. Dos defensas, complementarias:
   - *Deduplicación en el consumidor:* `auth-service` descarta eventos con un `id` ya procesado. Mitiga el daño, pero no evita el trabajo duplicado.
   - *Coordinación en el productor:* garantizar que solo una instancia ejecute el job (un candado distribuido, como ShedLock en el Reto 31).
     Hoy la transición condicionada `WHERE estado = ...` evita el doble disparo solo entre procesos que comparten la misma base de datos;
     con SQLite en un archivo por contenedor no hay base compartida. En este reto se acepta la versión de **instancia única**
     y queda como caso de estudio para el Reto 11.
2. **Evento perdido si el broker falla.** El estado se guarda antes de publicar; si el broker está caído en ese instante,
   el período queda `EN_CURSO` sin que `auth-service` se entere. La solución correcta es un patrón *outbox* (Reto 28).
3. **Precisión de fecha.** Los períodos tienen granularidad de día, no de hora.

## Decisión técnica: validación de existencia del empleado

Se evaluaron dos estrategias:

**Opción (a), consulta síncrona REST** *(la elegida)*: `vacaciones-service` llama a `GET /empleados/{id}` en `empleados-service`
al programar el período, con un timeout explícito de 5 segundos.

**Opción (b), réplica local por eventos**: consumir `empleado.creado` y `empleado.retirado` para mantener una tabla mínima de empleados válidos.

Se eligió la (a) porque `empleados-service` ya expone ese endpoint desde el Reto 2 (cero infraestructura nueva) y Vacaciones ya depende,
por el dominio, de que el empleado exista en el sistema central. **Costo asumido:** queda acoplado a la disponibilidad de
`empleados-service`, mitigado con el timeout de 5 segundos.

## Eventos publicados

Todos van al exchange compartido `ecosistema.eventos` (tipo `topic`, routing key = nombre del evento), con el envelope del Catálogo.

| Evento | Cuándo | Payload (`data`) |
|---|---|---|
| `vacaciones.programadas` | Al programar un período | `vacacionesId`, `empleadoId`, `email`, `fechaInicio`, `fechaFin`, `diasHabiles` |
| `vacaciones.iniciadas` | Scheduler: llega la `fechaInicio` | `vacacionesId`, `empleadoId`, `email`, `fechaInicio`, `fechaFin` |
| `vacaciones.finalizadas` | Scheduler: pasa la `fechaFin` | `vacacionesId`, `empleadoId`, `email`, `fechaFin` |

`diasHabiles` cuenta los días de lunes a viernes entre las dos fechas (sin calendario de festivos).
El ciclo completo de la cuenta, de punta a punta, está en [`docs/ciclo-de-vida-cuenta.md`](../docs/ciclo-de-vida-cuenta.md).

## Variables de entorno

| Variable | Por defecto | Descripción |
|---|---|---|
| `PORT` | `8085` | Puerto del servicio |
| `DB_PATH` | `./vacaciones.db` | Ruta del archivo SQLite (en Docker, el volumen `/data`) |
| `EMPLEADOS_URL` | `http://localhost:8080` | URL de `empleados-service` |
| `RABBITMQ_HOST` | `localhost` | Host de RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Puerto AMQP |
| `RABBITMQ_USERNAME` | `admin` | Usuario de RabbitMQ |
| `RABBITMQ_PASSWORD` | `admin` | Contraseña de RabbitMQ |
| `EVENTOS_EXCHANGE` | `ecosistema.eventos` | Exchange compartido del ecosistema |
| `SCHEDULER_ENABLED` | `true` | `false` apaga el scheduler |
| `SCHEDULER_INTERVAL_SECONDS` | `60` | Cada cuántos segundos revisa los períodos |
| `TZ` | UTC (en Docker: `America/Bogota`) | Zona horaria que define cuándo cambia "hoy" |
| `DEV_ENDPOINTS` | `false` (en Docker: `true`) | Habilita `forzar-inicio` y `forzar-fin`. **No usar en producción** |

## Swagger / OpenAPI

Disponible en `/vacaciones/api-docs` (por el Gateway: `http://localhost:8080/vacaciones/api-docs/`).
El contrato declara el esquema `BearerAuth` (JWT): se obtiene el token con `POST /auth/login` y se pega en **Authorize**.

## Ejecución local

```bash
npm install
npm start
```

## Ejecución con Docker

```bash
docker build -t vacaciones-service .
docker run -p 8085:8085 vacaciones-service
```