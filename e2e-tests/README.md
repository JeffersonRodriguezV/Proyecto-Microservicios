# e2e-tests — pruebas BDD de extremo a extremo

Pruebas funcionales del sistema completo con **Cucumber.js** y Gherkin en español (`# language: es`),
ejecutadas contra el API Gateway. Requiere Node 22 o superior.

## Estructura

| Carpeta | Contenido |
|---|---|
| `features/` | Escenarios `.feature` |
| `step_definitions/` | Implementación de los pasos |
| `support/` | `config.js` (variables de entorno), `world.js` (contexto compartido), `readiness.js` (espera de disponibilidad) |

## Ejecución local

```powershell
cd e2e-tests
Copy-Item .env.example .env     # una sola vez; ajuste los valores si hace falta
npm install
npm test
```

El sistema debe estar levantado (`docker compose up -d`) y el gateway disponible en `BASE_URL`.

## Variables de entorno

| Variable | Descripción |
|---|---|
| `BASE_URL` | URL base del sistema (por defecto `http://localhost:8080`) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Usuario de prueba con rol ADMIN (semilla de `auth-service`) |
| `USER_EMAIL`, `USER_PASSWORD` | Usuario de prueba con rol USER (semilla de `auth-service`) |

## Contexto compartido (World)

- Una instancia nueva por escenario: el token y la última respuesta no se filtran entre escenarios.
- El token vive en `this.token` y se envía solo en cada solicitud; un paso `Dado` lo obtiene y los `Cuando` lo reutilizan.
- La autenticación va en los Antecedentes: `Dado que estoy autenticado como ADMIN`.
- Pasos reutilizables: `que el sistema está desplegado y operativo`, `que estoy autenticado como {word}`,
  `la respuesta debe tener código {int}`. Para solicitudes propias: `await this.solicitar('POST', '/ruta', { body })`.
