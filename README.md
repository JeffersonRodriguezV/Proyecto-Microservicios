# Sistema de Onboarding/Offboarding de Empleados — Reto 2

Sistema basado en microservicios que permite la gestión de empleados y departamentos,
desarrollado como parte de una serie de retos progresivos. Este documento
cubre el sistema completo del Reto 02 (ambos servicios + infraestructura). Cabe mencionar que cada servicio
tiene además su propio README con detalles específicos.

## Tabla de servicios

| Servicio | Lenguaje / Framework | Base de datos | Puerto (host) |
|---|---|---|---|
| `empleados-service` | Java 21 / Spring Boot | MySQL 8.0 | 8080 |
| `departamentos-service` | Python 3.14 / FastAPI | PostgreSQL 16 | 8081 |

## Arquitectura

```
Cliente HTTP (curl, Postman, Bruno)
        │
        ├── POST/GET :8080 ──► empleados-service ──► database-empleados (MySQL)
        │                            │
        │                            │ HTTP + timeout/retry
        │                            ▼
        └── POST/GET :8081 ──► departamentos-service ──► database-departamentos (PostgreSQL)
```

`empleados-service` valida la existencia de `departamentoId` consultando a
`departamentos-service` por HTTP, dentro de la red interna de Docker
(`http://departamentos-service:8081`).

## Levantar el sistema desde cero, paso a paso

Requisito indispensable: Docker Desktop instalado y corriendo.

```bash
git clone https://github.com/JeffersonRodriguezV/Proyecto-Microservicios.git
cd Proyecto-Microservicios
cp .env.example .env        # opcional: el sistema funciona con valores por defecto si se omite
docker compose up --build
```

En otra terminal, verificar que los 4 contenedores queden `healthy`:

```bash
docker compose ps
```

Salida esperada (evidencia de arranque ordenado — cada servicio espera a
que su base de datos esté realmente lista, no solo arrancada):

```
NAME                     STATUS
database-departamentos   Up (healthy)
database-empleados       Up (healthy)
departamentos-service    Up (healthy)
empleados-service        Up (healthy)
```

Para DETENER sin PERDER DATOS:

```bash
docker compose down
```

Para reiniciar completamente desde CERO (BORRA LOS DATOS):

```bash
docker compose down -v
```

## Decisiones técnicas

### 1. Motor de base de datos: distinto por servicio (MySQL / PostgreSQL)

Se aprovechó la independencia entre microservicios para usar motores
distintos: MySQL para `empleados-service`, PostgreSQL para
`departamentos-service`. **Se ganó**: independencia real de infraestructura
entre servicios. **Costó**:
El equipo debe operar y conocer dos motores de base de datos distintos en
vez de uno solo, y dos comandos de healthcheck diferentes
(`mysqladmin ping` vs `pg_isready`).

### 2. Creación del esquema: script `init.sql` en ambos servicios

Se eligió la estrategia de un script de inicialización ya que es simple y suficiente
para el alcance de este reto. 
Cada servicio monta su propia carpeta
`database/` hacia `/docker-entrypoint-initdb.d/` de su base de datos, que
se ejecuta solo la primera vez que el volumen está vacío.

**Nota de portabilidad (Windows):** se descubrió durante el desarrollo que
Docker Desktop en Windows puede llegar a fallar al montar un **único archivo** con
`-v archivo:destino` (crea una carpeta vacía en el destino en lugar de
mapear el archivo). La solución fue montar la **carpeta `database/`
completa** en los dos servicios, en vez de apuntar a `init.sql`
específicamente — así se evita el problema sin perder funcionalidad.

Ambos servicios evitan el auto-DDL en producción: Java usa
`ddl-auto=validate` (Hibernate solo valida que el esquema coincida, nunca
lo modifica) y Python no llama a ningún método de creación automática de
tablas — evitando el riesgo de que el ORM (mapeo, objeto relacional) borre columnas con datos reales.

### 3. Garantía de unicidad: verificación previa + restricción en el esquema

En ambos servicios se combinan dos mecanismos: 1. verificación previa en
el service (da un mensaje de error 400 descriptivo) respaldada
por una restricción real en el esquema (`UNIQUE` en `email` y
`numero_empleado` para empleados; `PRIMARY KEY` en `id` para
departamentos). 2. La verificación previa por sí sola deja una ventana entre
consultar y guardar; la restricción de base de datos es la única garantía
real ante dos peticiones simultáneas con el mismo dato — por eso
`empleados-service` también captura el error de integridad como respaldo,
además de la comprobación previa.

## Decisión adicional añadida: comportamiento ante fallo de `departamentos-service`

Al registrar un empleado, `empleados-service` consulta a
`departamentos-service` con un timeout de 3 segundos y  4 intentos con espera creciente en cada intento. Si aun así
no logra respuesta, se decidió **aceptar el registro del empleado como
pendiente de validación** (campo `departamentoValidado: false`), en vez de
rechazarlo. Se priorizó que el sistema siga funcionando aunque
`departamentos-service` esté caído, sobre la garantía estricta de que todo
`departamentoId` esté siempre confirmado en el momento del registro.

## Evidencia de pruebas

Las 5 combinaciones de validación al registrar un empleado fueron
probadas manualmente contra el sistema completo corriendo en Docker:

| Escenario | Resultado obtenido |
|---|---|
| Departamento existente | `200 OK`, `departamentoValidado: true` |
| Departamento inexistente | `400 Bad Request` |
| `departamentos-service` caído (tras agotar reintentos) | `200 OK`, `departamentoValidado: false` |
| Email duplicado | `400 Bad Request` |
| numeroEmpleado duplicado | `400 Bad Request` |

### Persistencia: contraste `down` vs `down -v`
### Pruebas automatizadas (Postman)

Colección con pruebas  (`pm.test`) para las 10 validaciones
clave del sistema completo:

```
/postman/Gestion-departamentos_empleados.postman_collection.json
```

**Cómo ejecutarla:**
1. Reiniciar el sistema desde cero: `docker compose down -v && docker compose up --build`
2. Importar el archivo en Postman (File → Import)
3. Ejecutar con el **Collection Runner**

```bash
docker compose down          # destruye los contenedores y conserva volúmenes
docker compose up -d        
curl http://localhost:8080/empleados/1
# -> El empleado sigue existiendo: los datos viven en el volumen

docker compose down -v       # destruye contenedores Y volúmenes
docker compose up -d --build
curl http://localhost:8080/empleados/1
# -> 404: el volumen se borró con los datos
```

Ambos comportamientos fueron verificados manualmente sobre el sistema real.

## Documentación OpenAPI (Swagger)

- `empleados-service`: http://localhost:8080/swagger-ui/index.html
- `departamentos-service`: http://localhost:8081/docs

## Documentación por servicio

- [`empleados-service/README.md`](./empleados-service/README.md)
- [`departamentos-service/README.md`](./departamentos-service/README.md)