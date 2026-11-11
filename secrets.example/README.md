# secrets.example/

Plantilla **sin valores reales** de la carpeta `secrets/`, que es la que usa Docker Compose
(`secrets:` en `docker-compose.yml`) y que **no se sube al repositorio** (`.gitignore`).

Cada archivo contiene un único secreto, sin salto de línea final. Para crear los reales:

```bash
bash scripts/setup-secrets.sh                                   # Linux / macOS / Git Bash
powershell -ExecutionPolicy Bypass -File scripts\setup-secrets.ps1   # Windows
```

| Archivo | Lo usa |
|---|---|
| `jwt_secret` | api-gateway, auth-service (mínimo 32 caracteres) |
| `admin_password`, `seed_user_password` | auth-service (usuarios semilla) y pruebas BDD |
| `rabbitmq_password` | broker y servicios que publican/consumen eventos |
| `db_*_password`, `db_*_root_password` | MySQL de empleados y auth, PostgreSQL de departamentos |
| `grafana_admin_password`, `discord_webhook_url` | Grafana y su alerta a Discord |
| `jenkins_admin_password`, `sonar_token`, `sonar_db_password` | Jenkins y SonarQube |

`secrets.env` y `e2e-tests/.env` también se generan allí (agrupan los mismos valores para el pipeline E2E).

**Rotación:** `bash scripts/setup-secrets.sh --rotate` y `docker compose down -v` (las bases de datos
y RabbitMQ conservan la clave anterior en sus volúmenes) seguido de `docker compose up --build`.
