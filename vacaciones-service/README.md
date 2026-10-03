# Vacaciones Service - Reto 4

Gestión de períodos de vacaciones de empleados,
parte del sistema de onboarding/offboarding. Es el primer servicio del
ecosistema que **origina** una cadena de eventos: programa un período,
publica `vacaciones.programadas`.
nada sobre ellos)

## Lenguaje y stack

- **JavaScript (Node.js + Express)**
- **Base de datos:** SQLite, vía el módulo nativo `node:sqlite`
  (integrado en Node desde la versión 22.5, sin dependencias externas)
- **Mensajería:** `amqplib` (cliente de RabbitMQ)

### Por qué JavaScript

El enunciado del Reto 4 exige que `perfiles-service`, `notificaciones-service`
y `vacaciones-service` usen lenguajes "diferentes entre sí y distintos de
los usados en `empleados-service` y `departamentos-service`".  JavaScript ya se usó en
`api-gateway` (Reto 3), pero no en ninguno de los dos servicios nombrados
en esa restricción. Se eligió por una razón práctica: el equipo ya tenía
Node.js instalado y probado en varias máquinas, minimizando el riesgo de
problemas de entorno nuevos.

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

Estados posibles: `PROGRAMADA`, `CANCELADA` (en este reto). Las
transiciones a `EN_CURSO` y `FINALIZADA` se implementan luego.
## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/vacaciones` | Programa un período de vacaciones |
| `GET` | `/vacaciones/{id}` | Consulta un período por su id |
| `GET` | `/vacaciones?empleadoId={id}` | Lista los períodos de un empleado |
| `GET` | `/vacaciones` | Lista todos los períodos registrados |
| `DELETE` | `/vacaciones/{id}` | Cancela un período que aún no ha iniciado |
| `GET` | `/health` | Estado del servicio |

## Validaciones al programar un período

Todas responden `400 Bad Request` con mensaje descriptivo:

1. **Fechas incoherentes** — `fechaFin` no es posterior a `fechaInicio`.
2. **Fechas en el pasado** — `fechaInicio` es anterior a hoy.
3. **Solapamiento** — el empleado ya tiene un período `PROGRAMADA` o
   `EN_CURSO` que se cruza con el rango solicitado. La respuesta incluye
   el período en conflicto (`periodoConflicto`).
4. **Empleado inexistente** — ver decisión técnica abajo.

## Decisión técnica: validación de existencia del empleado

Se evaluaron dos estrategias:

**Opción (a) — Consulta síncrona REST** *(la elegida)*: `vacaciones-service`
llama a `GET /empleados/{id}` en `empleados-service` en el momento de
programar el período, con un timeout explícito de 5 segundos.

**Opción (b) — Réplica local por eventos**: `vacaciones-service` consumiría
`empleado.creado` y `empleado.retirado` para mantener su propia tabla
mínima de empleados válidos, sin depender de una llamada en vivo.

**Se eligió la opción (a)** por:

1. `empleados-service` ya expone `GET /empleados/{id}` desde el Reto 2 —
   cero infraestructura nueva.
2. Vacaciones ya depende, por naturaleza del dominio, de que el empleado
   exista en el sistema central. Si `empleados-service` está caído el
   tiempo suficiente como para no poder confirmar un empleado, es
   razonable que tampoco se puedan programar vacaciones para él.

**Costo asumido:** con esta estrategia, `vacaciones-service` queda
acoplado a la disponibilidad de `empleados-service` — si está caído,
la validación 4 tampoco puede resolverse. Se mitiga parcialmente con
un timeout explícito (5s) para no dejar la petición del cliente
colgada indefinidamente.

## Evento publicado

Al programar un período exitosamente, se publica `vacaciones.programadas`
en el exchange compartido `ecosistema.eventos` (tipo `topic`, routing key
= nombre del evento), siguiendo el envelope del Catálogo de Eventos:

```json
{
  "id": "uuid-del-mensaje",
  "type": "vacaciones.programadas",
  "version": 1,
  "occurredAt": "2026-...Z",
  "producer": "vacaciones-service",
  "data": {
    "vacacionesId": "V-2026-0042",
    "empleadoId": "E001",
    "email": "juan.perez@empresa.com",
    "fechaInicio": "2026-03-15",
    "fechaFin": "2026-03-30",
    "diasHabiles": 12
  }
}
```

`diasHabiles` se calcula contando días de lunes a viernes entre las dos
fechas (sin calendario de feriados).

## Variables de entorno

| Variable | Por defecto | Descripción |
|---|---|---|
| `PORT` | `8085` | Puerto del servicio |
| `EMPLEADOS_URL` | `http://localhost:8080` | URL de `empleados-service` (en Docker Compose, apunta al hostname interno real) |
| `RABBITMQ_HOST` | `localhost` | Host de RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Puerto AMQP |
| `RABBITMQ_USERNAME` | `admin` | Usuario de RabbitMQ |
| `RABBITMQ_PASSWORD` | `admin` | Contraseña de RabbitMQ |
| `EVENTOS_EXCHANGE` | `ecosistema.eventos` | Exchange compartido del ecosistema |

## Swagger / OpenAPI
PORT: http://localhost:8085/api-docs/

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


