# Guía: levantar el sistema en local

Requisitos: Docker Desktop (o Docker Engine + Compose v2), Git, y 8 GB de RAM como mínimo.

1. `git clone https://github.com/JeffersonRodriguezV/Proyecto-Microservicios.git && cd Proyecto-Microservicios`
2. Generar secretos: `bash scripts/setup-secrets.sh` (Windows: `powershell -ExecutionPolicy Bypass -File scripts\setup-secrets.ps1`).
3. `docker compose up --build`. Espere a que todos los contenedores estén `healthy`
   (`bash scripts/wait-for-healthy.sh http://localhost:8080 240`).
4. Comprobar: `curl http://localhost:8080/health` y abrir `http://localhost:8080/swagger`.
5. Datos de ejemplo de departamentos: ver `scripts/seed-data.sql`.
6. Detener: `docker compose down` (añada `-v` para borrar también los datos).

## Problemas frecuentes

| Síntoma | Causa | Solución |
|---|---|---|
| `compose` se detiene pidiendo una variable | Falta `.env` o está desactualizado | Ejecute `scripts/setup-secrets` otra vez (no pisa lo existente) |
| Un servicio no conecta a MySQL/RabbitMQ tras rotar secretos | El volumen conserva la clave anterior | `docker compose down -v` y levantar de nuevo |
| `/health` responde 503 | Falla la BD o el broker | Revise `checks` en la respuesta: indica cuál |
| Poca memoria | Todo el stack junto | Levante solo lo necesario: `docker compose up <servicio>` |
