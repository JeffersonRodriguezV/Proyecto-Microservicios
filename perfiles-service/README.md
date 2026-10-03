# Perfiles Service - Reto 4

Microservicio híbrido para la gestión de perfiles de empleados: combina
comunicación **reactiva** (consume eventos para crear/sincronizar/archivar
perfiles automáticamente) y **síncrona** (expone REST para consultarlos y
actualizarlos).

## Lenguaje y stack

- **C# / .NET** (ASP.NET Core Minimal API)
- **Base de datos:** SQLite vía `Microsoft.Data.Sqlite` (sin Entity
  Framework, acceso directo por SQL)
- **Mensajería:** `RabbitMQ.Client`
- **Documentación:** Swashbuckle (OpenAPI/Swagger)

### Por qué C#/.NET

Buena combinación de bajo esfuerzo para "consumir eventos + exponer REST":
tipado fuerte similar al estilo ya usado en `empleados-service` (Java),
con ORM/ADO.NET maduro y Swagger prácticamente automático.

## Modelo de datos

```json
{
  "id": "uuid",
  "empleadoId": "E001",
  "nombre": "Juan",
  "email": "juan.perez@empresa.com",
  "telefono": "",
  "direccion": "",
  "ciudad": "",
  "biografia": "",
  "fechaCreacion": "2026-...",
  "archivado": false
}
```

## Eventos consumidos

| Evento | Acción |
|---|---|
| `empleado.creado` | Crea un perfil por defecto con los datos básicos |
| `empleado.actualizado` | Sincroniza `nombre` y `email` |
| `empleado.retirado` | Archiva el perfil (`archivado: true`, nunca se borra) |

Deduplicación por `id` del mensaje, usando una tabla `eventos_procesados`
-- mismo mecanismo mínimo exigido desde el Catálogo de Eventos.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/perfiles/{empleadoId}` | Consulta el perfil de un empleado |
| `PUT` | `/perfiles/{empleadoId}` | Actualiza teléfono, dirección, ciudad y/o biografía |
| `GET` | `/perfiles` | Lista todos los perfiles |
| `GET` | `/health` | Estado del servicio |

Documentación interactiva: `http://localhost:8083/swagger`

## Variables de entorno

| Variable | Por defecto | Descripción |
|---|---|---|
| `RABBITMQ_HOST` | `localhost` | Host de RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Puerto AMQP |
| `RABBITMQ_USERNAME` | `admin` | Usuario de RabbitMQ |
| `RABBITMQ_PASSWORD` | `admin` | Contraseña de RabbitMQ |
| `EVENTOS_EXCHANGE` | `ecosistema.eventos` | Exchange compartido del ecosistema |

## Ejecución local

```bash
dotnet run
```

## Ejecución con Docker

```bash
docker build -t perfiles-service .
docker run -p 8083:8083 perfiles-service
```