# Notificaciones Service - Reto 4

Microservicio **puramente reactivo**: nadie lo llama por REST para crear
notificaciones. Consume eventos del broker, registra una notificación
simulada por cada uno (log estructurado en consola + historial en su base
de datos) y expone únicamente endpoints de consulta.

## Lenguaje y stack

- **Go** (librería estándar `net/http`, sin framework web)
- **Base de datos:** SQLite embebido, vía `modernc.org/sqlite` (Go puro, sin CGO)
- **Mensajería:** `github.com/rabbitmq/amqp091-go`
- **Documentación:** OpenAPI 3 escrita a mano y servida con Swagger UI

### Por qué Go

Es el lenguaje distinto a Java, Python y Node.js que mejor se ajusta a un
servicio pequeño: compila a un binario estático, la imagen final pesa poco y
solo necesita una dependencia externa de verdad (el cliente de RabbitMQ).

## Eventos que consume

Se enlaza al exchange compartido `ecosistema.eventos` (tipo `topic`) con su
propia cola durable `notificaciones-service.eventos`.

| Evento | Tipo de notificación | Mensaje simulado |
|---|---|---|
| `empleado.creado` | `BIENVENIDA` | "Bienvenido {nombre} {apellido} a la empresa" |
| `empleado.retirado` | `DESVINCULACION` | "Su cuenta ha sido desactivada, gracias por su trabajo" |
| `vacaciones.programadas` | `VACACIONES` | "Sus vacaciones del {inicio} al {fin} han sido confirmadas" |

Cada una se imprime así en la consola del servicio:

```
[NOTIFICACIÓN] Tipo: BIENVENIDA | Para: juan@empresa.com | Mensaje: "Bienvenido Juan Pérez a la empresa"
```

## Deduplicación

Un broker puede reentregar un mensaje si el consumidor se cae antes de
confirmarlo. Antes de procesar un evento, el servicio busca su `id` de
envelope en la tabla `eventos_procesados(id, procesado_en)`. Si ya está, lo
descarta y confirma el mensaje sin repetir el efecto.

**Evidencia:** el mismo evento `empleado.creado` (mismo `id`, `dedup-test-0001`)
se publicó dos veces a mano desde la interfaz de RabbitMQ. El log del servicio
muestra una sola notificación y el descarte del duplicado:

```
[NOTIFICACIÓN] Tipo: BIENVENIDA | Para: dedup.test@empresa.com | Mensaje: "Bienvenido Dedup Test a la empresa"
2026/10/03 02:42:30 Evento dedup-test-0001 ya procesado, se descarta (deduplicación)
```

`GET /notificaciones/999` devolvió una única notificación.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/notificaciones` | Lista todas las notificaciones, de la más reciente a la más antigua |
| `GET` | `/notificaciones/{empleadoId}` | Lista las de un empleado (lista vacía si no tiene) |
| `GET` | `/health` | Estado del servicio (lo usa el healthcheck de Docker) |

Estructura de una notificación:

```json
{
  "id": "923af782-39a8-4aba-938e-37e0cac3d433",
  "tipo": "BIENVENIDA",
  "destinatario": "juan@empresa.com",
  "mensaje": "Bienvenido Juan Pérez a la empresa",
  "fechaEnvio": "2026-10-03T02:42:30Z",
  "empleadoId": "1"
}
```

## Documentación OpenAPI (Swagger)

- Interfaz: `http://localhost:8080/notificaciones/docs` (por el Gateway)
- Especificación: `http://localhost:8080/notificaciones/openapi.json`

Las rutas de documentación viven bajo el prefijo `/notificaciones`, así el
Gateway las reenvía sin ninguna regla extra. La interfaz carga Swagger UI desde
un CDN, por lo que el navegador necesita internet.

## Variables de entorno

| Variable | Por defecto | Descripción |
|---|---|---|
| `RABBITMQ_HOST` | `localhost` | Host de RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Puerto AMQP |
| `RABBITMQ_USERNAME` | `admin` | Usuario de RabbitMQ |
| `RABBITMQ_PASSWORD` | `admin` | Contraseña de RabbitMQ |
| `EVENTOS_EXCHANGE` | `ecosistema.eventos` | Exchange compartido del ecosistema |
| `DB_PATH` | `./notificaciones.db` | Ruta del archivo SQLite. En Docker Compose apunta a `/data/notificaciones.db`, dentro de un volumen, para que los datos sobrevivan a `docker compose down` |

## Ejecución

Con Docker Compose, desde la raíz del repositorio:

```bash
docker compose up --build
```

Local (necesita un RabbitMQ en `localhost:5672`; el servicio termina si no puede conectarse):

```bash
go build .
./notificaciones-service
```
