# auth-service

Servicio de autenticación del ecosistema. Valida credenciales, emite JWT y gestiona el ciclo de vida de las cuentas.
Java 21 · Spring Boot · MySQL 8 · RabbitMQ · puerto interno **8086**.

## Endpoints

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/auth/login` | Devuelve el JWT de acceso | No |
| POST | `/auth/recover-password` | Solicita recuperación. Siempre responde 200 | No |
| POST | `/auth/reset-password` | Establece contraseña con el token de recuperación | Token de recuperación |
| POST | `/auth/change-password` | Cambia la contraseña del usuario autenticado | Bearer JWT |

Swagger UI: `http://localhost:8080/auth/swagger-ui.html` (a través del Gateway) · Contrato: `/auth/api-docs` (esquema `BearerAuth`).
## Cómo obtener un token

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@empresa.com","password":"Admin123!"}'
```

Respuesta: `{"accessToken":"...","tokenType":"Bearer","expiresIn":3600}`.
Se envía en las demás peticiones como `Authorization: Bearer <accessToken>`.

El usuario `admin` se siembra al arrancar con `ADMIN_EMAIL` y `ADMIN_PASSWORD` (solo para desarrollo).

## Tokens

- **Acceso** (HS256): claims `sub` (empleadoId), `role` (`ADMIN` | `USER`), `email`, `iat`, `exp`. Duración: `JWT_EXPIRATION_MINUTES` (60 por defecto).
- **Recuperación**: claims `sub`, `type=RESET_PASSWORD`, `email`, `iat`, `exp`. **No lleva `role`**, así que no sirve como sesión.
  Se publica en el evento `usuario.recuperacion`.
- Validación en el Gateway con el mismo `JWT_SECRET`: debe exigir `role` y rechazar tokens con claim `type`.

## Estados de cuenta

`PENDIENTE_ACTIVACION` · `ACTIVA` · `SUSPENDIDA_TEMPORAL` · `DESACTIVADA_PERMANENTE`

| Situación | Respuesta |
|---|---|
| Credenciales incorrectas | 401 (mensaje genérico) |
| Contraseña correcta, cuenta no activa | 403 |
| `reset-password` sobre cuenta pendiente | Pasa a `ACTIVA` y publica `cuenta.activada` (`ACTIVACION_INICIAL`) |
| `reset-password` sobre cuenta suspendida | Cambia la contraseña, sigue suspendida |
| `reset-password` sobre cuenta desactivada | 403 |

Política de contraseña: mínimo 8 caracteres, con mayúscula, minúscula y dígito. Se guardan con BCrypt.

## Configuración (variables de entorno)

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Sí | Conexión a `db-auth` |
| `JWT_SECRET` | Sí | Secreto compartido con el Gateway (≥ 32 caracteres). Sin valor por defecto: el servicio no arranca sin él |
| `JWT_EXPIRATION_MINUTES` | No (60) | Vida del token de acceso |
| `JWT_RESET_EXPIRATION_MINUTES` | No (60) | Vida del token de recuperación |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | No | Admin sembrado al arrancar |
| `RABBITMQ_HOST` | No (localhost) | Broker de mensajería |

En Docker, `JWT_SECRET` se toma del archivo `.env` de la raíz (ver `.env.example`).

## Limitaciones conocidas

- El token de recuperación es *stateless*: puede reutilizarse hasta que expire. Mitigación: vida corta.
- Los tokens de acceso no se pueden revocar antes de su expiración.