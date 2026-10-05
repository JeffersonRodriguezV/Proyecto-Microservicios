# Sistema de Onboarding/Offboarding de Empleados — Reto 4

Sistema de microservicios para gestionar empleados, con comunicación REST
síncrona y comunicación asincrónica por eventos a través de un message broker.
Un solo evento (por ejemplo, crear un empleado) dispara acciones automáticas e
independientes en otros servicios.

## Arquitectura

```
Cliente HTTP (curl, Postman, navegador)
        │   único punto de entrada: http://localhost:8080
        ▼
   api-gateway
        ├── /empleados/*       ──► empleados-service        ──► MySQL
        ├── /departamentos/*   ──► departamentos-service    ──► PostgreSQL
        ├── /perfiles/*        ──► perfiles-service         ──► SQLite
        ├── /notificaciones/*  ──► notificaciones-service   ──► SQLite
        └── /vacaciones/*      ──► vacaciones-service       ──► SQLite

   message-broker (RabbitMQ) — exchange "ecosistema.eventos"
     empleados-service  ── publica ──► empleado.creado / actualizado / retirado
     vacaciones-service ── publica ──► vacaciones.programadas
     perfiles-service, notificaciones-service ◄── consumen
```

Comunicación síncrona: `empleados-service` → `departamentos-service` (valida el
departamento) y `vacaciones-service` → `empleados-service` (valida el empleado).
Solo el Gateway (`8080`) y la interfaz del broker (`15672`) se publican al host;
el resto usa `expose:` y solo es alcanzable dentro de la red de Docker.

## Servicios y lenguajes

| Servicio | Lenguaje | Base de datos | Puerto interno | Rol |
|---|---|---|---|---|
| `api-gateway` | Node.js (Express) | — | 8080 (publicado) | Punto de entrada único |
| `empleados-service` | Java 21 (Spring Boot) | MySQL 8 | 8080 | CRUD, baja lógica, publica `empleado.*` |
| `departamentos-service` | Python (FastAPI) | PostgreSQL 16 | 8081 | CRUD de departamentos |
| `perfiles-service` | C# (.NET) | SQLite | 8083 | Consume `empleado.*`; REST de perfiles |
| `notificaciones-service` | Go | SQLite | 8084 | Consume eventos; historial de notificaciones |
| `vacaciones-service` | JavaScript (Node.js) | SQLite | 8085 | CRUD de vacaciones; publica `vacaciones.programadas` |
| `message-broker` | RabbitMQ 3 | — | 5672 (interno), 15672 (UI) | Mensajería asincrónica |

## Despliegue

Requisito: Docker Desktop corriendo.

```bash
git clone https://github.com/JeffersonRodriguezV/Proyecto-Microservicios.git
cd Proyecto-Microservicios
docker compose up --build
```

En otra terminal, `docker compose ps` debe mostrar los **9 contenedores**
`healthy`. URL base de todo el sistema: `http://localhost:8080`. Interfaz del
broker: `http://localhost:15672` (usuario `admin`, contraseña `admin`).

- Detener conservando los datos: `docker compose down`
- Reiniciar desde cero (borra todos los datos): `docker compose down -v`

## Rutas del Gateway y documentación OpenAPI

| Ruta externa | Servicio | Documentación Swagger |
|---|---|---|
| `/empleados`, `/empleados/{id}` | `empleados-service` | — |
| `/departamentos`, `/departamentos/{id}` | `departamentos-service` | — |
| `/perfiles`, `/perfiles/{empleadoId}` | `perfiles-service` | `http://localhost:8080/perfiles/swagger` |
| `/notificaciones`, `/notificaciones/{empleadoId}` | `notificaciones-service` | `http://localhost:8080/notificaciones/docs` |
| `/vacaciones`, `/vacaciones/{id}` | `vacaciones-service` | `http://localhost:8080/vacaciones/api-docs` |
| `/health` | El propio Gateway | — |

Cada servicio nuevo publica su documentación bajo su propio prefijo, así el
Gateway la reenvía sin reglas adicionales. El Swagger de `empleados-service` y
`departamentos-service` (Retos 2 y 3) no es accesible desde el host porque vive en
la raíz de cada servicio, fuera de los prefijos que reenvía el Gateway.

## Eventos

Siguen el **Catálogo de Eventos** del ecosistema (nombres, envelope y cargas
útiles sin cambios). Todos usan el envelope `{id, type, version, occurredAt,
producer, data}` y se publican en el exchange `ecosistema.eventos` (tipo
`topic`), con el nombre del evento como routing key. Cada consumidor tiene su
propia cola durable.

| Evento | Productor | Consumidores | Efecto | Catálogo |
|---|---|---|---|---|
| `empleado.creado` | `empleados-service` | `perfiles-service`, `notificaciones-service` | Crea el perfil por defecto; registra notificación `BIENVENIDA` | §3.1 |
| `empleado.actualizado` | `empleados-service` | `perfiles-service` | Sincroniza `nombre` y `email` en el perfil | §3.2 |
| `empleado.retirado` | `empleados-service` | `perfiles-service`, `notificaciones-service` | Archiva el perfil (no se borra); registra `DESVINCULACION` | §3.3 |
| `vacaciones.programadas` | `vacaciones-service` | `notificaciones-service` | Registra notificación `VACACIONES` | §3.8 |

- Los eventos se publican **después** de persistir. Si la publicación falla, se
  registra el error y la operación en base de datos **no** se revierte.
- **Baja lógica:** `DELETE /empleados/{id}` no borra: cambia el estado a
  `RETIRADO`, guarda `fechaRetiro` y publica `empleado.retirado`. El campo
  `motivo` del evento se recibe como `?motivo=` (por defecto `RENUNCIA`).
- **Auditoría:** `GET /empleados?estado=RETIRADO`, con filtro opcional
  `&desde=AAAA-MM-DD&hasta=AAAA-MM-DD` sobre la fecha de retiro.
- **Deduplicación:** cada consumidor registra el `id` de cada mensaje en una tabla
  `eventos_procesados` y descarta los repetidos.

## Decisiones técnicas

### Message broker: RabbitMQ


| Broker | Decisión | Motivo |
|---|---|---|
| **RabbitMQ** | Elegido | Colas durables y confirmación de mensajes, adecuado para eventos de negocio; interfaz de administración incluida; amplia documentación |

### Validación del empleado en `vacaciones-service`

Había dos opciones para comprobar que el empleado existe: (a) consulta síncrona a
`GET /empleados/{id}`, o (b) mantener una réplica local alimentada por
`empleado.creado` y `empleado.retirado`. Se eligió **(a)**:

1. `empleados-service` ya expone ese endpoint, así que no requiere infraestructura nueva.
2. Vacaciones depende por naturaleza del dominio de que el empleado exista en el
   sistema central: si Empleados está caído, tampoco tiene sentido programar vacaciones.

**Costo asumido:** queda acoplado a la disponibilidad de `empleados-service`. Se
mitiga con un timeout explícito de 5 segundos. Se prioriza simplicidad y
consistencia inmediata sobre autonomía.

### Base de datos de los servicios nuevos

Cada servicio nuevo tiene su propia base **SQLite** en un volumen de Docker propio
(`perfiles-data`, `notificaciones-data`, `vacaciones-data`), montado en `/data` y
configurado con la variable `DB_PATH`. Es una base embebida, sin contenedor
aparte, suficiente para el volumen de este sistema.
### Resiliencia (Reto 3)

La llamada de `empleados-service` a `departamentos-service` usa timeout, reintentos
con espera creciente y Circuit Breaker (Resilience4j: ventana de 10 llamadas,
mínimo 3, umbral de 50 %, 30 s en estado abierto). Si no se puede validar el
departamento, el empleado se registra con `departamentoValidado: false`
(disponibilidad sobre consistencia). Cuando el circuito vuelve a cerrarse,
`empleados-service` revalida automáticamente esos registros pendientes.

## Cómo probar el flujo asincrónico

Todo por el Gateway (`http://localhost:8080`), con el sistema levantado:

1. `POST /departamentos` — crear un departamento.
2. `POST /empleados` — crear un empleado (usa ese `departamentoId`).
3. `GET /perfiles/{empleadoId}` — el perfil ya existe: lo creó el evento.
4. `GET /notificaciones/{empleadoId}` — aparece la notificación `BIENVENIDA`.
5. `PUT /empleados/{id}` — cambiar el nombre; `GET /perfiles/{empleadoId}` lo refleja.
6. `POST /vacaciones` — programar un período; `GET /notificaciones/{empleadoId}` muestra `VACACIONES`.
7. Las 4 validaciones de vacaciones responden `400`: fechas incoherentes, fecha pasada, solapamiento (con `periodoConflicto`) y empleado inexistente.
8. `DELETE /empleados/{id}` — el empleado pasa a `RETIRADO`; su perfil queda `archivado: true` y aparece la notificación `DESVINCULACION`.
9. `GET /empleados?estado=RETIRADO` — el empleado aparece en la auditoría.

Los pasos 1 a 4, 6 y 7 están automatizados en la colección
`Postman/Reto4-Flujo-Completo.postman_collection.json` (se ejecuta con el
Collection Runner y se puede repetir sin tocar nada). Los pasos 5, 8 y 9 se
prueban a mano con los endpoints indicados.

## Evidencia

### Deduplicación

Se publicó dos veces desde la interfaz de RabbitMQ el mismo evento
`empleado.creado` (mismo `id` de envelope). Resultado en el sistema integrado:
una sola notificación y un solo perfil, y los logs de `notificaciones-service` y
`perfiles-service` muestran el descarte del duplicado. Ejemplo del log:

```
[NOTIFICACIÓN] Tipo: BIENVENIDA | Para: dedup.test@empresa.com | Mensaje: "Bienvenido Dedup Test a la empresa"
Evento dedup-test-0001 ya procesado, se descarta (deduplicación)
```

### Persistencia

Con datos en `empleados`, `perfiles`, `notificaciones` y `vacaciones`:

```bash
docker compose down        # conserva los volúmenes
docker compose up -d
# los cuatro conteos son iguales que antes: los datos viven en los volúmenes

docker compose down -v     # destruye también los volúmenes
docker compose up -d
# los cuatro conteos quedan en 0
```

## Documentación por servicio

- [`api-gateway`](./api-gateway/README.md)
- [`empleados-service`](./empleados-service/README.md)
- [`departamentos-service`](./departamentos-service/README.md)
- [`perfiles-service`](./perfiles-service/README.md)
- [`notificaciones-service`](./notificaciones-service/README.md)
- [`vacaciones-service`](./vacaciones-service/README.md)

## Pruebas automatizadas (Postman)

- `Postman/Reto4-Flujo.postman_collection.json` — Reto 4: vacaciones y flujo asincrónico.
- `Postman/Gestion-departamentos_empleadosReto03.postman_collection.json` — empleados y departamentos, por el Gateway.