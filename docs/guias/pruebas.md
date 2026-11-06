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
