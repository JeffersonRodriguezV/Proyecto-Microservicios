# Reto 8 — `departamentos-service` (Python) y `perfiles-service` (.NET)

Parte de la Persona 3 (instrumentación de los dos servicios restantes).

## Librerías

| Pilar | `departamentos-service` (FastAPI) | `perfiles-service` (.NET 10) |
|---|---|---|
| Métricas `GET /metrics` | `prometheus-fastapi-instrumentator` (`should_group_status_codes=False`) | `prometheus-net.AspNetCore` (`UseHttpMetrics` + `MapMetrics`) |
| Trazas | `opentelemetry-sdk`, `opentelemetry-exporter-zipkin-json`, `opentelemetry-instrumentation-fastapi`, `opentelemetry-instrumentation-sqlalchemy` | `OpenTelemetry.Extensions.Hosting`, `OpenTelemetry.Instrumentation.AspNetCore`, `OpenTelemetry.Exporter.Zipkin` |
| Logs JSON con `traceId` | `structlog` | `Serilog.AspNetCore` + `Serilog.Expressions` (plantilla JSON plana) |
| Propagación | W3C `traceparent` (HTTP entrante) | W3C `traceparent` (HTTP y mensajes del broker) |

## Decisiones

- **`should_group_status_codes=False`:** por defecto el instrumentador agrupa el código ("2xx", "4xx"). Las reglas de Prometheus buscan `status=~"5.."` y el dashboard separa 4xx/5xx, así que se deja el código exacto (`status="404"`).
- **`/health`:** ambos servicios ya tenían `/health` en el formato común (`{status, service, checks}`, 503 si algo está DOWN): departamentos comprueba la base de datos (no usa broker) y perfiles la base de datos y el broker. No se tocaron.
- **`/health` y `/metrics` no se trazan ni se registran** (healthcheck de Docker y Prometheus cada pocos segundos).
- **departamentos, trazas hacia la base de datos:** se instrumenta SQLAlchemy, así que la traza muestra también los `select departamentos`.
- **perfiles, consumidor del broker:** `RabbitMqConsumer` extrae el `traceparent` de las cabeceras del mensaje (llegan como `byte[]`) y abre un span `perfiles.procesar <evento>` de tipo CONSUMER, hijo del span del productor. Se usa el propagador W3C explícito (`TraceContextPropagator`) para que no dependa de que el SDK global ya esté arrancado.
- **Zipkin en .NET:** el paquete `OpenTelemetry.Exporter.Zipkin` está marcado obsoleto a favor de OTLP; se mantiene porque el Reto exige Zipkin (el aviso de compilación se silencia con un `#pragma` explicado en `Program.cs`).
- **Pruebas:** `OTEL_SDK_DISABLED=true` en `tests/conftest.py` (departamentos) para que no se cree un exportador en las pruebas.

## Verificación

Prueba en Docker: al crear un empleado, Zipkin muestra **una sola traza** que ya incluye los dos servicios de esta parte:

```
empleados-service        http post /empleados                      (SERVER)
empleados-service        http get  -> departamentos                (CLIENT)
departamentos-service    get /departamentos/{id}                   (SERVER, hijo del CLIENT)
departamentos-service    select departamentos                      (CLIENT, base de datos)
empleados-service        ecosistema.eventos/empleado.creado send   (PRODUCER)
perfiles-service         perfiles.procesar empleado.creado         (CONSUMER, hijo del PRODUCER)
auth-service             auth-service.eventos receive              (CONSUMER)
auth-service             ecosistema.eventos/usuario.creado send    (PRODUCER)
notificaciones-service   notificaciones.procesar usuario.creado    (CONSUMER)
```

- Los logs de ambos servicios salen en JSON y llevan el mismo `traceId` que la traza en Zipkin.
- En Prometheus, `departamentos-service` y `perfiles-service` aparecen en `up`.
- Pruebas: departamentos (pytest, 97 % de cobertura) y perfiles (xUnit, 24 pruebas), todas en verde.

> Falta lo que depende del sistema completo y de otras personas: la traza que empieza en el gateway, la simulación del caos y las capturas finales.
