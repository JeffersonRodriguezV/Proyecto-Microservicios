# Sistema de Onboarding/Offboarding de Empleados — Reto 3

Sistema basado en microservicios para la gestión de empleados y departamentos,
desarrollado como parte de una serie de retos progresivos. Este documento
cubre el sistema completo (los tres servicios + infraestructura); cada
servicio tiene además su propio README con detalles específicos.

## Tabla de servicios

| Servicio | Lenguaje / Framework | Base de datos | Acceso |
|---|---|---|---|
| `api-gateway` | Node.js / Express | — | `http://localhost:8080` (único punto de entrada) |
| `empleados-service` | Java 21 / Spring Boot | MySQL 8.0 | Solo interno (red de Docker) |
| `departamentos-service` | Python / FastAPI | PostgreSQL 16 | Solo interno (red de Docker) |

## Arquitectura

```
Cliente HTTP (curl, Postman, navegador)
        │
        │  único punto de entrada
        ▼
   api-gateway  :8080 (host) -> :3000 (interno)
        │
        ├── /empleados/*      ──► empleados-service (interno :8080) ──► database-empleados (MySQL)
        │                              │
        │                              │ HTTP + timeout/retry + Circuit Breaker
        │                              ▼
        └── /departamentos/*  ──► departamentos-service (interno :8081) ──► database-departamentos (PostgreSQL)
```

Desde el Reto 3, **ningún microservicio es accesible directamente desde
el host** — `empleados-service` y `departamentos-service` usan `expose:`
en vez de `ports:` en el `docker-compose.yml`. Todo el tráfico externo
pasa por `api-gateway`.

## Cómo levantar el sistema desde cero

Requisito: Docker Desktop instalado y corriendo.

```bash
git clone https://github.com/JeffersonRodriguezV/Proyecto-Microservicios.git
cd Proyecto-Microservicios
cp .env.example .env        # opcional: el sistema funciona con valores por defecto si se omite
docker compose up --build
```

En otra terminal, verificar que los 5 contenedores queden `healthy`:

```bash
docker compose ps
```

Salida esperada:

```
NAME                     STATUS
api-gateway              Up (healthy)
database-departamentos   Up (healthy)
database-empleados       Up (healthy)
departamentos-service    Up (healthy)
empleados-service        Up (healthy)
```

Para detener sin perder datos: `docker compose down`
Para reiniciar completamente desde cero (borra TODO): `docker compose down -v`

## URL base del sistema (Reto 3)

 **Toda petición al sistema pasa por el Gateway**:
```
http://localhost:8080
```

Las URLs directas a cada microservicio (`:8080` de empleados, `:8081` de
departamentos) ya **no** son alcanzables desde fuera de Docker.

### Tabla de rutas (Gateway → servicio interno)

| Ruta externa (a través del Gateway) | Servicio interno |
|---|---|
| `POST /empleados` | `empleados-service` |
| `GET /empleados/{id}` | `empleados-service` |
| `GET /empleados` | `empleados-service` |
| `POST /departamentos` | `departamentos-service` |
| `GET /departamentos/{id}` | `departamentos-service` |
| `GET /departamentos` | `departamentos-service` |
| `GET /health` | El propio Gateway |

## Decisiones técnicas

### 1. Motor de base de datos: distinto por servicio (MySQL / PostgreSQL)

Se aprovechó la independencia entre microservicios para usar motores
distintos: MySQL para `empleados-service`, PostgreSQL para
`departamentos-service`. **Se ganó**: independencia real de
infraestructura entre servicios, evidencia concreta de persistencia
políglota. **Su costO**: el equipo debe conocer dos motores
de base de datos distintos en vez de uno solo y dos comandos de
healthcheck diferentes (`mysqladmin ping` Y `pg_isready`).

### 2. Creación del esquema: script `init.sql` en ambos servicios

Se eligió sobre auto-DDL del ORM (rriesgoso en producción) y sobre
herramientas de migración (más complejas).

### 3. Garantía de unicidad: verificación previa + restricción en el esquema

Se combinan ambas estrategias: verificación previa en el service (da
un 400 legible) respaldada por una restricción real en el esquema
(`UNIQUE` en email/numero_empleado; `PRIMARY KEY` en el id de
departamento) — la única garantía real ante peticiones simultáneas.

### 4. Gateway de aplicación: Node.js + Express (Reto 3)

Se eligió un Gateway de **aplicación** (código propio) en vez de uno
declarativo (Traefik/Nginx), porque retos futuros del curso requieren
lógica propia en el Gateway (validación de JWT, composición de
respuestas) que uno declarativo no puede hacer sin plugins.
Node.js + Express suma un **tercer lenguaje** de programación al
proyecto (junto a Java y Python), acercando al requisito del proyecto
final de un mínimo de 4 lenguajes distintos. También se descartó Go por
introducir dos fricciones simultáneas para el equipo (manejo de
errores sin excepciones + documentación OpenAPI manual).

## Circuit Breaker (Reto 3)

`empleados-service` protege su llamada hacia `departamentos-service`
con **Resilience4j**, envolviendo el timeout + retry que ya existía
desde el Reto 2.

### Parámetros elegidos

| Parámetro | Valor | Por qué |
|---|---|---|
| Ventana deslizante | 10 llamadas | Sugerido por el reto |
| Mínimo de llamadas para evaluar | 3 | Umbral bajo del rango sugerido (3-5) |
| Umbral de fallos | 50% | Sugerido por el reto |
| Espera en estado OPEN | 30 segundos | Mínimo del rango sugerido (30-60s) |
| Llamadas de prueba en HALF_OPEN | 1 | Mínimo necesario para probar recuperación |

### Estrategia de fallback

Ante circuito abierto o reintentos agotados, el empleado se registra
igual, marcado como pendiente de validación (`departamentoValidado: false`)
— Esta misma decisión de negocio tomada en el Reto 2 ya que se prioriza
disponibilidad sobre consistencia estricta. La reconciliación de estos
registros pendientes queda como trabajo futuro, fuera del alcance de
este reto.


## Evidencia de las 3 pruebas del Reto 3

### Prueba 1 — Punto de entrada único

```bash
# A través del Gateway: funciona
curl http://localhost:8080/empleados
curl http://localhost:8080/departamentos

# Acceso directo a un microservicio: DEBE FALLAR
curl http://localhost:8081/departamentos  #FALLA
```

**Resultado real obtenido:** el acceso vía Gateway respondió `200` con
datos reales; el acceso directo al puerto `8081` fue rechazado
, confirmando que `expose:` funciona correctamente.

### Prueba 2 — Salto en el tiempo de respuesta del Circuit Breaker

Con `departamentos-service` detenido, se enviaron 5 peticiones de
registro de empleado consecutivas:

| Petición | Tiempo de respuesta | Estado del circuito |
|---|---|---|
| 1 | 12710 ms | CLOSED |
| 2 | 9733 ms | CLOSED |
| 3 | 9793 ms | **OPEN** (recién se abrió) |
| 4 | 80 ms | OPEN |
| 5 | 70 ms | OPEN |

Las primeras 3 peticiones agotaron el retry completo (1s+2s+4s) antes
de fallar, luego al acumular 3 fallos consecutivos (100% de tasa de fallo,
supera el umbral de 50%), por ende el circuito se abrió. Las peticiones 4 y 5
respondieron **~150 veces más rápido**, sin tocar la red.

### Prueba 3 — Recuperación automática

Tras esperar 35 segundos en estado `OPEN` (sin ninguna petición de por
medio), el endpoint de observabilidad mostró el estado `HALF_OPEN` —
confirmando que Resilience4j programa la transición automáticamente al
vencer el tiempo configurado.

Al restaurar `departamentos-service` y enviar una petición con un
`departamentoId` **inexistente** (prueba deliberada: si el sistema
solo estuviera "fingiendo" recuperación, habría respondido `200` con
el fallback de siempre), la respuesta real fue **`400 Bad Request`**
— confirmando que volvió a consultar de verdad. El circuito quedó en
`CLOSED`.

## Evidencia de persistencia (Reto 2, sigue vigente)

```bash
docker compose down          # destruye contenedores, conserva volúmenes
docker compose up -d
curl http://localhost:8080/empleados/1
# -> El empleado sigue existiendo: los datos viven en el volumen

docker compose down -v       # destruye contenedores Y volúmenes
docker compose up -d --build
curl http://localhost:8080/empleados/1
# -> 404: el volumen se borró y con él los datos
```

## Documentación OpenAPI (Swagger)

**Limitación conocida introducida en el Reto 3:** el Gateway actual
solo reenvía rutas bajo `/empleados/*` y `/departamentos/*`. Swagger UI
vive en la raíz de cada servicio (`/swagger-ui/index.html` y `/docs`
respectivamente, fuera de esos prefijos), y como los microservicios ya
no publican puertos al host, **Swagger no es accesible desde el
navegador con la configuración actual**. 
Decisión en un futuro: Agregar la ruta de Swagger al Gateway para que sea accesible desde el host.

## Pruebas automatizadas (Postman)

Colección con pruebas automáticas (`pm.test`):

```
/Postman/Gestion-departamentos_empleadosReto02.postman_collection.json
```



## Documentación por servicio

- [`api-gateway/README.md`](./api-gateway/README.md)
- [`empleados-service/README.md`](./empleados-service/README.md)
- [`departamentos-service/README.md`](./departamentos-service/README.md)