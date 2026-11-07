# Guía: ejecutar las pruebas

Cada servicio se prueba con su propia herramienta; ninguna necesita el sistema levantado salvo el E2E.

| Servicio | Comando (desde la carpeta del servicio) | Reporte de cobertura |
|---|---|---|
| api-gateway | `npm ci && npm test` | `reports/lcov.info` |
| vacaciones-service | `npm ci && npm test` | `reports/lcov.info` |
| departamentos-service | `pip install -r requirements.txt -r requirements-dev.txt && python -m pytest --cov=app` | consola / `coverage.xml` |
| notificaciones-service | `go test ./... -cover` | `-coverprofile=coverage.out` |
| perfiles-service | `bash perfiles-service.Tests/run-tests.sh` dentro de `mcr.microsoft.com/dotnet/sdk:10.0` | `/reports/coverage` |
| auth-service, empleados-service | `mvn verify` (exige ≥ 70 % con JaCoCo) | `target/site/jacoco` |

E2E (BDD con Cucumber, sistema completo): ver `e2e-tests/README.md` y `scripts/e2e.sh`.

Meta de calidad: **cobertura ≥ 70 %** por servicio; el Quality Gate de SonarQube la hace cumplir en CI.

## E2E y el sistema en marcha

El E2E (`e2e-bdd` y la etapa E2E de cada servicio) levanta el sistema completo con nombres de contenedor fijos y el puerto 8080, así que choca con el sistema que ya esté corriendo, incluso si sus contenedores solo están parados. Antes de lanzarlo hay que ejecutar `docker compose down` (**sin `-v`**, para conservar los volúmenes con los datos). Al terminar, se restaura el sistema por fases:

```powershell
$env:COMPOSE_PROJECT_NAME = "proyecto-microservicios"
# 1) Infraestructura, esperando a que esté sana
docker compose up -d --wait --wait-timeout 240 message-broker db-auth db-empleados db-departamentos
# 2) El resto de servicios
docker compose up -d
```

Comprueba después que los contenedores están sanos y que `http://localhost:8080/health` responde `UP`.
