# Gestión de Departamentos - Reto 2

Microservicio de gestión de departamentos, parte del sistema de
onboarding/offboarding de empleados basado en microservicios. Construido
en un lenguaje distinto al del servicio de Empleados (Java/Spring Boot),
cumpliendo con el requisito de diversidad tecnológica del Reto 2.

## Tecnologías

- Python 3.14
- FastAPI + Uvicorn
- SQLAlchemy 2.0 (modo síncrono)
- PostgreSQL
- Docker

## Decisiones de diseño

### Motor de base de datos: PostgreSQL (distinto a MySQL de empleados-service)

Se aprovechó la independencia entre microservicios para usar un motor
distinto al de `empleados-service`. Gana: independencia real de
infraestructura entre servicios, y evidencia concreta de persistencia
políglota. Cuesta: el equipo debe operar dos motores de base de datos
distintos en vez de uno solo.

### El `id` del departamento NO se autogenera

A diferencia de `Empleado` en el Reto 1 (donde el id se decidió autogenerar
porque nada más lo referenciaba), aquí el `id` es un código de negocio
provisto por el cliente (ej. `"IT"`), porque `empleados-service` lo usa
como clave externa vía HTTP (`departamentoId`). Autogenerarlo rompería el
flujo de pruebas oficial del Reto 2, que crea `"IT"` y luego lo referencia
desde empleados con ese mismo valor.

### Garantía de unicidad del id: verificación previa + restricción PRIMARY KEY

Se combinan ambas estrategias que plantea el Reto 2 como decisión abierta:
una verificación previa en el service (da un mensaje de error 400 claro),
respaldada por la restricción `PRIMARY KEY` de la base de datos (la
única garantía real ante dos requests simultáneos con el mismo id — se
captura el `IntegrityError` resultante y se traduce a un 400 legible en
vez de dejarlo propagar como 500).

### Esquema de base de datos: script `init.sql`

Mismo mecanismo que usa `empleados-service`, por consistencia entre
servicios: un script montado en `/docker-entrypoint-initdb.d/` que
PostgreSQL ejecuta solo la primera vez que su volumen de datos está vacío.

**Nota para quien integre el Compose (Docker Desktop en Windows):** montar
un único archivo con `-v archivo:destino` puede fallar silenciosamente
(Docker crea una carpeta vacía en el destino en vez de mapear el archivo).
La solución robusta es montar la carpeta `database/` completa hacia
`/docker-entrypoint-initdb.d/` en vez de un archivo específico.

## Modelo de Departamento

```json
{
  "id": "IT",
  "nombre": "Tecnología",
  "descripcion": "Departamento de TI"
}
```

## Endpoints disponibles

### Registrar un departamento

```
POST /departamentos
Content-Type: application/json
```

**Validaciones:** todos los campos son obligatorios y no pueden estar vacíos.

**Respuestas:**

| Código | Caso |
|---|---|
| `201 Created` | Departamento registrado. Devuelve el departamento creado. |
| `400 Bad Request` | El `id` ya está registrado, o algún campo es inválido/vacío. |

### Consultar un departamento por id

```
GET /departamentos/{id}
```

**Respuestas:**

| Código | Caso |
|---|---|
| `200 OK` | Devuelve el departamento solicitado. |
| `404 Not Found` | No existe un departamento con ese id. |

### Listar todos los departamentos

```
GET /departamentos
```

**Respuesta:** `200 OK` con un arreglo de departamentos (vacío si no hay ninguno).

### Formato uniforme de error

Todas las respuestas de error siguen esta estructura:

```json
{
  "status": 0,
  "mensaje": "string",
  "timestamp": "yyyy-MM-ddTHH:mm:ss"
}
```

## Ejecución local (sin Docker)

1. Crear y activar un entorno virtual:

   ```bash
   python -m venv venv
   .\venv\Scripts\Activate.ps1
   pip install -r requirements.txt
   ```

2. Definir las variables de entorno de conexión a PostgreSQL:

   ```powershell
   $env:DB_URL="postgresql://localhost:5433/departamentos_db"
   $env:DB_USERNAME="deptuser"
   $env:DB_PASSWORD="deptpass"
   ```

3. Ejecutar:

   ```bash
   uvicorn app.main:app --reload --port 8081
   ```

4. Disponible en `http://localhost:8081`, Swagger UI en `http://localhost:8081/docs`.

## Ejecución con Docker

```bash
docker build -t departamentos-service .
docker run -p 8081:8081 -e DB_URL=... -e DB_USERNAME=... -e DB_PASSWORD=... departamentos-service
```

(En el sistema completo, estas variables las provee `docker-compose.yml`.)

## Pruebas

Verificado manualmente vía Swagger UI (`/docs`) contra una instancia real
de PostgreSQL en Docker: registro exitoso (201), consulta exitosa (200),
consulta de id inexistente (404), registro de id duplicado (400), listado
completo (200). Verificado también dentro del contenedor Docker final,
conectado a PostgreSQL, con resultados idénticos.

## Estructura del proyecto

```
app/
├── routers/        # Endpoints HTTP (equivalente a @RestController)
├── services/       # Lógica de negocio
├── repositories/    # Acceso a datos (SQLAlchemy)
├── models/         # Entidad SQLAlchemy
├── schemas/        # Contratos Pydantic de entrada/salida
├── exceptions/      # Manejo global de errores
├── database.py     # Configuración de conexión
└── main.py         # Punto de entrada + registro de routers/handlers
```
