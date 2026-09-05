# Gestión de Empleados - Reto 1

Servicio web para el registro y consulta básica de empleados, desarrollado como
parte de una serie de retos progresivos orientados a construir un sistema de
onboarding/offboarding basado en microservicios.

## Tecnologías

- Java 21
- Spring Boot
- Maven
- Docker

## Requisitos previos

Para ejecutar el proyecto necesitas tener instalado alguno de los siguientes,
según la forma en que quieras correrlo:

- **Ejecución local:** Java 21 y Maven.
- **Ejecución con Docker:** Docker Desktop.

## Ejecución local (sin Docker)

1. Clonar el repositorio:
   ```bash
   git clone https://github.com/JeffersonRodriguezV/Proyecto-Microservicios.git
   cd Proyecto-Microservicios
   ```

2. Ejecutar la aplicación con Maven:
   ```bash
   mvn spring-boot:run
   ```

3. La aplicación quedará disponible en:
   ```
   http://localhost:8080
   ```

## Ejecución con Docker

1. Construir la imagen:
   ```bash
   docker build -t servidor-empleados .
   ```

2. Ejecutar el contenedor:
   ```bash
   docker run -p 8080:8080 servidor-empleados
   ```

3. La aplicación quedará disponible en:
   ```
   http://localhost:8080
   ```

## Modelo de Empleado

```json
{
  "id": 1,
  "nombre": "Juan",
  "apellido": "Pérez",
  "email": "juan.perez@empresa.com",
  "numeroEmpleado": "EMP-2026-001",
  "cargo": "Desarrollador Senior",
  "area": "Tecnología",
  "departamentoId": "IT",
  "fechaIngreso": "2026-02-10",
  "estado": "ACTIVO"
}
```

Estados posibles: `ACTIVO`, `EN_VACACIONES`, `RETIRADO` (en este reto solo se
maneja `ACTIVO`).

## Endpoints disponibles

### Registrar un empleado

```
POST /empleados
Content-Type: application/json
```

**Body de ejemplo:**
```json
{
  "nombre": "Juan",
  "apellido": "Pérez",
  "email": "juan.perez@empresa.com",
  "numeroEmpleado": "EMP-2026-001",
  "cargo": "Desarrollador Senior",
  "area": "Tecnología",
  "departamentoId": "IT",
  "fechaIngreso": "2026-02-10",
  "estado": "ACTIVO"
}
```

**Respuestas:**
| Código | Caso |
|---|---|
| `200 OK` | Empleado registrado correctamente. Devuelve el empleado creado (con `id` asignado). |
| `400 Bad Request` | El `email` o el `numeroEmpleado` ya están registrados. Devuelve un mensaje descriptivo del error. |

### Consultar un empleado por id

```
GET /empleados/{id}
```

**Respuestas:**
| Código | Caso |
|---|---|
| `200 OK` | Devuelve la información del empleado solicitado. |
| `404 Not Found` | No existe un empleado con ese id. Devuelve: `El empleado con id {id} no existe` |

### Rutas o métodos no soportados

Cualquier ruta inexistente, o cualquier método HTTP no definido para una ruta
existente, responde:

```
404 Not Found
```
```
Recurso no encontrado
```

## Pruebas

El proyecto fue probado con **Postman**. La colección con todos los casos de
prueba se encuentra en:

```
/postman/Gestion-Empleados-Reto1.postman_collection.json
```

Para importarla en Postman: **File → Import** y seleccionar el archivo.

### Casos cubiertos por la colección

| # | Solicitud | Método | Ruta | Código esperado |
|---|---|---|---|---|
| 1 | Registrar un empleado | `POST` | `/empleados` | `200 OK` |
| 2 | Registrar empleado duplicado (Email) | `POST` | `/empleados` | `400 Bad Request` |
| 3 | Registrar empleado duplicado (Número empleado) | `POST` | `/empleados` | `400 Bad Request` |
| 4 | Consultar un empleado existente | `GET` | `/empleados/{id}` | `200 OK` |
| 5 | Consultar empleado inexistente | `GET` | `/empleados/{id}` | `404 Not Found` |
| 6 | Ruta no soportada | `GET` | `/clientes` | `404 Not Found` |
| 7 | Método no soportado | `DELETE` | `/empleados/{id}` | `404 Not Found` |

## Estructura del proyecto

```
src/main/java/com/microservicios/gestionempleados/
├── controller/    # Controladores REST
├── service/       # Lógica de negocio
├── repository/    # Acceso a datos (JPA)
├── model/         # Entidades y enums
└── exception/     # Manejo global de errores
```
