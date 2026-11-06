# Guía de reproducibilidad del CI (Reto 7)

Objetivo: levantar Jenkins + SonarQube desde cero en otra máquina y obtener
builds verdes sin pasos manuales ocultos. Probado con una instalación limpia
(clon nuevo, volúmenes vacíos): `empleados-service #1` y `vacaciones-service #1`
verdes, Quality Gate OK e imágenes publicadas en `localhost:5000`.

## Requisitos
- Docker Desktop con Compose v2, ~8 GB de RAM libres.
- La carpeta del clon **debe llamarse `Proyecto-Microservicios`**. La red del
  CI tiene nombre fijo (`proyecto-microservicios_microservices-network`), que
  es el que usan los Jenkinsfile en `docker run --network`.

## Pasos
1. `git clone https://github.com/JeffersonRodriguezV/Proyecto-Microservicios.git`
2. Crear los `.env` **antes** de levantar (JCasC los monta en solo lectura y
   los convierte en las credenciales `env-sistema` y `env-e2e`):
   - `cp .env.example .env` (ajustar `JENKINS_ADMIN_PASSWORD`, `SONAR_ADMIN_PASSWORD`).
   - `cp e2e-tests/.env.example e2e-tests/.env`
3. `docker compose -f docker-compose.ci.yml up -d` y esperar a que Sonar esté
   `UP` (http://localhost:9000).
4. Configurar SonarQube (una sola vez, vía UI o API):
   - Cambiar la clave de `admin` por `SONAR_ADMIN_PASSWORD`.
   - Crear el Quality Gate **`cobertura-70`** (condiciones: `coverage` < 70 y
     `new_coverage` < 70, sin otras condiciones) y marcarlo como *default*.
   - Crear un token de usuario y ponerlo en `.env` como `SONAR_TOKEN`.
   - Webhook: nombre `jenkins`, URL `http://jenkins:8080/sonarqube-webhook/`.
5. Recrear Jenkins para que lea el token:
   `docker compose -f docker-compose.ci.yml up -d --force-recreate jenkins`
6. Entrar a http://localhost:9090 (usuario/clave de `.env`), comprobar que
   existen las credenciales `sonar-token`, `env-sistema`, `env-e2e` y los jobs
   `empleados-service`, `vacaciones-service`, `e2e-bdd`.
7. Lanzar `empleados-service` y `vacaciones-service` con `RUN_SONAR=true`.

## Hallazgos de la prueba (y cómo quedaron resueltos)
| Problema | Causa | Solución en el repo |
|---|---|---|
| Fallo "Could not find credentials entry with ID 'env-sistema'" tras reiniciar | JCasC borra las credenciales creadas a mano | Credenciales `file:` declaradas en `jenkins/casc/jenkins.yaml` desde `.env` montados |
| Fallo "network ... not found" | El nombre de la red dependía de la carpeta | `name:` fijo en `docker-compose.ci.yml` |
| Jobs nuevos no aparecen en un Jenkins ya levantado | Job DSL solo corre al iniciar | *Manage Jenkins → Reload Configuration from Disk* |
| `buildWithParameters` responde 400 tras reiniciar | El job aún no conoce sus parámetros | Lanzar una vez con *Build Now*; después ya es parametrizado |
| Quality Gate con condiciones extra | Gate copiado del por defecto | Dejar solo `coverage` y `new_coverage` < 70 |

## Secretos
`.env`, `e2e-tests/.env` y el token de Sonar están en `.gitignore` y nunca se
suben; solo viven en la máquina y en los volúmenes de Jenkins/Sonar.
