#!/usr/bin/env bash
# ============================================================================
# run-all.sh — COMANDO UNICO del Reto 6
#   1) levanta el sistema con docker compose
#   2) corre las pruebas unitarias + de integracion (empleados, auth, vacaciones, perfiles)
#   3) corre la suite BDD completa en el contenedor bdd-tests
#   4) apaga el sistema
# Devuelve 0 solo si TODO paso; si cualquier suite falla, sale con codigo != 0.
#
# Uso:       bash run-all.sh        (desde la raiz del repositorio)
# Requisito: Docker encendido (Testcontainers lo necesita para la integracion).
# ============================================================================
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

EXIT_GLOBAL=0
declare -a RESULTADOS=()

log()  { printf "\n\033[1;34m==> %s\033[0m\n" "$*"; }
ok()   { printf "\033[1;32m[OK]\033[0m %s\n" "$*"; }
fail() { printf "\033[1;31m[FAIL]\033[0m %s\n" "$*"; }

registrar() {
  local nombre="$1" code="$2"
  if [ "$code" -eq 0 ]; then
    RESULTADOS+=("OK   - $nombre")
  else
    RESULTADOS+=("FAIL - $nombre (codigo $code)")
    EXIT_GLOBAL=1
  fi
}

# ---------- 0. Prerrequisitos ----------
log "Comprobando prerrequisitos"
command -v docker >/dev/null 2>&1 || { fail "Docker no esta en PATH"; exit 1; }
docker info      >/dev/null 2>&1 || { fail "Docker no responde (Docker Desktop apagado?)"; exit 1; }
[ -f .env ]                      || { fail "Falta .env en la raiz (copia .env.example)"; exit 1; }
ok "Docker y .env presentes"

# ---------- 1. Levantar el sistema ----------
log "docker compose up --build -d"
docker compose up --build -d
registrar "docker compose up" $?
[ $EXIT_GLOBAL -eq 0 ] || { printf '%s\n' "${RESULTADOS[@]}"; exit 1; }

# ---------- 2. Esperar health del gateway ----------
log "Esperando a http://localhost:8080/actuator/health"
for i in $(seq 1 40); do
  curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1 && break
  sleep 2
done

# ---------- 3. Java (unit + integracion con Testcontainers) ----------
for svc in empleados-service auth-service; do
  log "$svc  ->  ./mvnw clean verify"
  ( cd "$svc" && ./mvnw -q clean verify )
  registrar "$svc clean verify" $?
done

# ---------- 4. vacaciones-service (unit) ----------
if [ -d vacaciones-service ]; then
  log "vacaciones-service  ->  npm test"
  ( cd vacaciones-service && npm ci --silent && npm test )
  registrar "vacaciones-service npm test" $?
fi

# ---------- 5. perfiles-service (.NET) ----------
if [ -d perfiles-service.Tests ]; then
  log "perfiles-service.Tests"
  if [ -x perfiles-service.Tests/run-tests.sh ]; then
    ( cd perfiles-service.Tests && ./run-tests.sh )
    registrar "perfiles-service run-tests.sh" $?
  elif command -v dotnet >/dev/null 2>&1; then
    ( cd perfiles-service.Tests && dotnet test )
    registrar "perfiles-service dotnet test" $?
  else
    fail "perfiles-service: sin run-tests.sh ni dotnet en PATH"
    registrar "perfiles-service" 1
  fi
fi

# ---------- 6. BDD (contenedor) ----------
log "BDD  ->  docker compose --profile tests run --rm bdd-tests"
docker compose --profile tests run --rm bdd-tests
registrar "BDD e2e-tests" $?

# ---------- 7. Resumen ----------
log "Resumen"
printf '%s\n' "${RESULTADOS[@]}"
[ $EXIT_GLOBAL -eq 0 ] && ok "Todo en verde" || fail "Hubo fallos"

# ---------- 8. Tear down opcional ----------
#   KEEP_UP=1 bash run-all.sh   -> deja el sistema levantado al terminar
if [ "${KEEP_UP:-0}" != "1" ]; then
  log "docker compose down"
  docker compose down
fi

exit $EXIT_GLOBAL