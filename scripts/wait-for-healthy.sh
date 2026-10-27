#!/usr/bin/env bash
# Espera a que el sistema este listo antes de correr las pruebas BDD.
# 1) el gateway responde en /health   2) auth-service responde a traves del gateway.
# Uso: ./scripts/wait-for-healthy.sh [BASE_URL] [TIMEOUT_SEGUNDOS]
BASE="${1:-${BASE_URL:-http://localhost:8080}}"
TIMEOUT="${2:-${HEALTH_TIMEOUT:-180}}"
INTERVALO=5
inicio=$(date +%s)

esperar() {  # esperar "descripcion" comando...
  local desc="$1"; shift
  echo "Esperando: $desc"
  until "$@"; do
    if [ $(( $(date +%s) - inicio )) -ge "$TIMEOUT" ]; then
      echo "ERROR: '$desc' no respondio en ${TIMEOUT}s" >&2
      exit 1
    fi
    echo "  aun no, reintentando en ${INTERVALO}s..."
    sleep "$INTERVALO"
  done
}

gateway_vivo() { curl -fsS -o /dev/null "$BASE/health"; }
auth_vivo() {
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST \
    -H 'Content-Type: application/json' -d '{}' "$BASE/auth/login")
  [ "$code" != "000" ] && [ "$code" -lt 500 ]
}

esperar "gateway ($BASE/health)" gateway_vivo
esperar "auth-service via gateway ($BASE/auth/login)" auth_vivo
echo "Sistema listo."