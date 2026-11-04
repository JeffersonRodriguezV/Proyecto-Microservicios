# Reto 7 - Persona 1: pipeline de empleados-service

Pipeline de integracion continua de `empleados-service` (Java 21, Spring Boot, Maven) en Jenkins.
Archivo: `empleados-service/Jenkinsfile`. Job definido como codigo en `jenkins/casc/jobs.yaml`.

## Etapas

| Etapa | Que hace | Si falla |
|---|---|---|
| Checkout | Descarga el codigo desde `main` | FAILURE |
| Build | `mvn clean compile` en un agente `maven:3.9-eclipse-temurin-21` | FAILURE |
| Test | `mvn verify`: pruebas unitarias e integracion (Testcontainers) + cobertura JaCoCo | UNSTABLE si hay tests rotos |
| SonarQube + Quality Gate | _pendiente_ | FAILURE |
| Package / Publish | _pendiente_ | FAILURE |
| E2E | _pendiente_ | UNSTABLE |

## Decisiones

- **Agente Maven en Docker** con cache `maven_cache:/root/.m2`: builds reproducibles y mas rapidos desde el segundo.
- **`docker.sock` montado** en el agente: las pruebas de integracion usan Testcontainers.
- **Tests rotos = UNSTABLE, no FAILURE** (`-Dmaven.test.failure.ignore=true`): el reporte se publica y el fallo queda visible; solo compilacion, Quality Gate y Package rompen el build.
- **Cobertura minima 70%** exigida por JaCoCo en el `pom.xml`.
- **`disableConcurrentBuilds()`** para no pisar el workspace ni los puertos.

## Resultados

- Pruebas: 64 ejecutadas, 0 fallos.
- Cobertura de lineas: 100% (199/199). Ramas: 92,86%.

## Evidencias

_Pendiente: capturas en `docs/img/`._

## Ejecuciones

| Build | Resultado | Notas |
|---|---|---|
| #2 | SUCCESS | 64 tests, 100% cobertura |

## Fallo simulado

_Pendiente._
