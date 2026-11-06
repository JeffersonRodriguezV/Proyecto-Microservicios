# Catálogo de eventos

Exchange `ecosistema.eventos` (tipo `topic`, durable). La **routing key es el nombre del evento**.
Cada consumidor tiene su propia cola durable. Envelope común:

```json
{ "id": "uuid", "type": "empleado.creado", "version": "1.0",
  "occurredAt": "2026-01-01T10:00:00Z", "producer": "empleados-service", "data": { } }
```

## Quién publica y quién consume

| Evento | Publica | Consumen | Efecto |
|---|---|---|---|
| `empleado.creado` | empleados-service | auth, perfiles | Crea la cuenta y el perfil |
| `empleado.actualizado` | empleados-service | perfiles | Actualiza el perfil |
| `empleado.retirado` | empleados-service | auth, perfiles, notificaciones | Desactiva la cuenta de forma permanente; cierra el perfil; notificación `DESVINCULACION` |
| `usuario.creado` | auth-service | notificaciones | Correo de bienvenida con el enlace para establecer la contraseña (`SEGURIDAD`) |
| `usuario.recuperacion` | auth-service | notificaciones | Enlace de recuperación de contraseña (`SEGURIDAD`) |
| `vacaciones.programadas` | vacaciones-service | notificaciones | Confirma las fechas (`VACACIONES`) |
| `vacaciones.iniciadas` | vacaciones-service | auth | Cuenta `ACTIVA` → `SUSPENDIDA_TEMPORAL` |
| `vacaciones.finalizadas` | vacaciones-service | auth | Cuenta `SUSPENDIDA_TEMPORAL` → `ACTIVA` |

La fuente normativa de nombres y cargas útiles es el *Catálogo de Eventos* del ecosistema; esta tabla
resume lo implementado en el código (`ConsumerConfig.java`, `consumer.go`, `RabbitMqConsumer.cs`).

## Deduplicación (idempotencia)

RabbitMQ garantiza *al menos una entrega*, no *exactamente una*. Por eso:

- **auth-service** guarda el `id` del evento en la tabla `eventos_procesados` (`EventoProcesado`).
- **notificaciones-service** y **perfiles-service** hacen lo mismo en su SQLite.
- Si el `id` ya existe, el evento se confirma (`ack`) sin repetir el efecto.
- Un mensaje malformado se descarta sin reintento infinito; un fallo transitorio se reencola.

## Cómo probar un evento a mano

1. Abra la consola de RabbitMQ (`http://localhost:15672`, usuario/clave `RABBITMQ_*` del `.env`).
2. *Exchanges → ecosistema.eventos → Publish message*, con la routing key del evento y un JSON del envelope.
3. Verifique el efecto en el servicio consumidor (por ejemplo `GET /notificaciones/{empleadoId}`).
Publicar dos veces el mismo `id` debe producir **un solo** efecto.
