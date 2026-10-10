# API Gateway — Reto 3 y Reto 5

Punto de entrada único del sistema de microservicios. Ningún microservicio es
accesible desde fuera de la red de Docker: todo el tráfico externo pasa por aquí.

Desde el **Reto 5** el Gateway también es el **punto único de seguridad**: valida
el JWT emitido por el `auth-service` y aplica las reglas de autorización (RBAC y
propiedad del recurso) **antes** de enrutar. Se escribe una sola vez, en lugar de
repetirse en cada servicio (Java, Python, C#, Go y Node).

## Tecnologías

- Node.js 22
- Express
- http-proxy-middleware
- jsonwebtoken (validación HS256)
- Docker

## Qué hace

1. **Autentica** (401): exige `Authorization: Bearer <jwt>` en todo lo que no sea público.
2. **Autoriza** (403): aplica RBAC por rol y propiedad del recurso.
3. **Enruta** hacia los servicios internos, reenviando cuerpo, cabeceras y código
   de estado **sin alterarlos**.
4. Si el destino no responde, devuelve `503` con un JSON descriptivo.

## Rutas reenviadas

| Ruta externa | Servicio interno |
|---|---|
| `/auth/*` | `auth-service` |
| `/empleados/*` | `empleados-service` |
| `/departamentos/*` | `departamentos-service` |
| `/perfiles/*` | `perfiles-service` |
| `/notificaciones/*` | `notificaciones-service` |
| `/vacaciones/*` | `vacaciones-service` |
| `GET /health` | Gateway (propio, público) |

## Seguridad (Reto 5)

### Flujo de cada petición

```
Cliente ──► Gateway ──► ¿ruta pública? ──sí──► proxy
                │
                no
                ▼
        ¿Bearer válido? ──no──► 401
                │ sí
                ▼
        ¿RBAC / propiedad? ──no──► 403
                │ sí
                ▼
              proxy (la cabecera Authorization se reenvía intacta)
```

### Rutas públicas (sin token)

| Método y ruta | Motivo |
|---|---|
| `POST /auth/login` | aún no hay token |
| `POST /auth/recover-password` | el usuario no puede autenticarse |
| `POST /auth/reset-password` | usa un token de activación/recuperación propio |
| `GET /health` | healthcheck |
| `GET` de documentación (`/…/swagger…`, `/…/api-docs`, `/…/openapi…`, `/…/docs`) | Swagger UI de cada servicio |

Todo lo demás exige JWT.

### Autenticación — 401

Se rechaza con `401` si: falta la cabecera o no empieza por `Bearer `, la firma no
coincide (token alterado), el token venció, o no es un **token de acceso**.
Un token de acceso lleva el claim `role` y **no** lleva `type`; así, un token de
activación o recuperación (que viaja por correo) no abre la API.

```json
{ "status": 401, "mensaje": "Token inválido o expirado", "timestamp": "2026-10-10T01:59:20.797Z" }
```

### Autorización — 403

| Rol | Permitido |
|---|---|
| `ADMIN` | Todo (crear, modificar y eliminar en cualquier servicio) |
| `USER` | Solo lectura (`GET`, `HEAD`, `OPTIONS`) |
| `USER` | `POST /auth/change-password` (su propia contraseña) |
| `USER` | `PUT /perfiles/{empleadoId}` **solo si** `{empleadoId}` == claim `sub` del token |
| `USER` | Cualquier otra escritura → **403** |

La regla de propiedad compara el `empleadoId` de la ruta con el `sub` del token
(que el `auth-service` fija al `empleadoId` del empleado). Un USER que intenta
modificar el perfil de otro recibe `403`, no `401`.

```json
{ "status": 403, "mensaje": "No tiene permisos para realizar esta acción", "timestamp": "2026-10-10T02:00:00.000Z" }
```

Consecuencia: `POST /vacaciones/{id}/forzar-inicio` y `forzar-fin` (endpoints de
demostración del scheduler) solo los puede invocar un ADMIN, sin regla adicional.

### Código

| Archivo | Responsabilidad |
|---|---|
| `src/security.js` | `autenticar` (401) y `autorizar` (403). Sin lógica de negocio. |
| `src/server.js` | Proxies, `/health`, 503 y orden de middlewares (seguridad antes del proxy). |

## Puertos

| Contexto | Puerto |
|---|---|
| Interno (dentro del contenedor) | `8080` (variable `PORT`) |
| Publicado al host | `8080` — **único puerto público de la API** |

## Variables de entorno

| Variable | Descripción | Por defecto (solo pruebas locales) |
|---|---|---|
| `PORT` | Puerto interno del Gateway | `8080` |
| `JWT_SECRET` | Secreto HS256, **el mismo** que usa el `auth-service`. Obligatorio, mínimo 32 caracteres: si falta, el Gateway no arranca. | — (sin valor por defecto) |
| `AUTH_URL` | URL base de `auth-service` | `http://localhost:8086` |
| `EMPLEADOS_URL` | URL base de `empleados-service` | `http://localhost:8080` |
| `DEPARTAMENTOS_URL` | URL base de `departamentos-service` | `http://localhost:8081` |
| `PERFILES_URL` | URL base de `perfiles-service` | `http://localhost:8083` |
| `NOTIFICACIONES_URL` | URL base de `notificaciones-service` | `http://localhost:8084` |
| `VACACIONES_URL` | URL base de `vacaciones-service` | `http://localhost:8085` |

`JWT_SECRET` no está en el código: se define en el `.env` de la raíz (ver
`.env.example`) y `docker-compose.yml` lo inyecta al Gateway y al `auth-service`.
Dentro de Compose las URLs se sobreescriben con los hostnames internos de la red.

## Ejecución

```bash
# Con todo el sistema (recomendado), desde la raíz del repositorio
cp .env.example .env
docker compose up -d --build
```

```bash
# Solo el Gateway, fuera de Docker (requiere JWT_SECRET)
cd api-gateway
npm install
JWT_SECRET=una-clave-academica-de-al-menos-32-caracteres npm start
```

## Endpoints propios

### `GET /health`

```json
{ "servicio": "api-gateway", "estado": "activo" }
```

## Manejo de errores

| Código | Cuándo |
|---|---|
| `401` | Sin token, token alterado, vencido o que no es de acceso |
| `403` | Autenticado, pero sin permiso (RBAC o propiedad) |
| `503` | El servicio destino no responde |

```json
{
  "status": 503,
  "mensaje": "El servicio en /departamentos no está disponible",
  "timestamp": "2026-09-22T03:53:47.297Z"
}
```

## Evidencia de pruebas

Colección de Postman: [`Postman/Reto5-Seguridad.postman_collection.json`](../Postman/Reto5-Seguridad.postman_collection.json)
(21 requests, repetible, ejecutar con el Collection Runner). Resultado de la corrida:
**21 de 21 en verde**.

| # | Prueba | Esperado |
|---|---|---|
| 01 | `GET /empleados` sin token | 401 |
| 02 | Token alterado | 401 |
| 04–05 | ADMIN crea un empleado; el token de activación llega en la notificación `SEGURIDAD` | 200 |
| 06–07 | `reset-password` con ese token y login del USER | 200 |
| 08 | USER lee `/empleados` | 200 |
| 09 | USER `DELETE /empleados/{id}` | 403 |
| 10 | USER `PUT /perfiles/{su id}` | 200 |
| 11 | USER `PUT /perfiles/{otro id}` | 403 |
| 12–13 | USER crea departamento / fuerza vacaciones | 403 |
| 14–16 | `change-password`: la clave antigua falla (401) y la nueva funciona (200) | 200 / 401 / 200 |
| 17 | `recover-password` genera `usuario.recuperacion` | 200 |
| 18–20 | ADMIN retira al empleado; el login falla; aparece `RETIRADO` con `fechaRetiro` | 200/204, 401 o 403, 200 |

## Decisiones de diseño

### Validar el JWT en el Gateway y no en cada servicio

El Reto 5 permite validar en el Gateway o en cada microservicio. Se eligió el Gateway:
el ecosistema tiene seis servicios en cinco lenguajes, y validar por servicio
obligaría a resolver el JWT cinco veces con cinco librerías distintas y mantenerlas
sincronizadas. Además, el Reto 10 traslada la validación a JWKS contra un Identity
Provider: con el Gateway como único punto, ese cambio se hace una sola vez.

### Los servicios siguen sin validar el token

Los servicios no están expuestos al host (solo el Gateway publica puerto), así que
solo se llega a ellos por el Gateway. Además, `empleados-service` llama directamente a
`departamentos-service` para validar el departamento, sin token; exigir JWT en
departamentos rompería esa llamada y el Circuit Breaker. El esquema `BearerAuth`
aparece en el OpenAPI de cada servicio solo como documentación.

### Token de acceso distinto del token de activación

Ambos son JWT firmados con el mismo secreto. Se distinguen por contenido: el de
acceso lleva `role` y no `type`; el de activación/recuperación lleva
`type=RESET_PASSWORD` y no `role`. El Gateway exige la forma de acceso, de modo que
un token que viaja por correo no sirve para operar la API.

### Documentación pública

Los Swagger se sirven bajo el prefijo de cada servicio (`/empleados/swagger-ui.html`,
`/departamentos/docs`, `/perfiles/swagger`, `/notificaciones/docs`,
`/vacaciones/api-docs`) para que el Gateway pueda enrutarlos. Son de solo lectura y
públicos; los datos siguen protegidos.

### Healthcheck sin dependencias extra

El `Dockerfile` usa el propio Node (`require('http')`) para el `HEALTHCHECK`,
evitando instalar `curl` o `wget` en la imagen.

## Limitaciones conocidas

- **Propiedad del recurso solo en `/perfiles/{empleadoId}`.** Es el único recurso con
  dueño que el Reto 5 pide proteger; si aparecen otros, se agregan reglas en `autorizar`.
- **Secreto simétrico (HS256) compartido** entre Gateway y `auth-service`. Cualquiera
  que lo conozca puede firmar tokens. El Reto 10 lo reemplaza por JWKS (clave pública).
- **Sin revocación.** Un token válido sigue funcionando hasta que vence (60 min por
  defecto), aunque la cuenta se suspenda o se retire después. El auth-service impide
  nuevos logins, pero no invalida tokens ya emitidos.
- **La regla de propiedad compara el `sub` con la ruta.** Si el `sub` dejara de ser el
  `empleadoId`, habría que cambiarla.

## Estructura del proyecto

```
api-gateway/
├── src/
│   ├── server.js
│   └── security.js
├── package.json
├── package-lock.json
├── Dockerfile
├── .dockerignore
└── .gitignore
```