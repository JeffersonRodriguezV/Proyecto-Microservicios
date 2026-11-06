#!/usr/bin/env bash
# Genera los secretos locales del proyecto en el archivo .env.
#
# Uso:
#   bash scripts/setup-secrets.sh            # crea .env y rellena lo que falte (no toca valores existentes)
#   bash scripts/setup-secrets.sh --rotate   # regenera TODOS los secretos (rotación)
#
# Los valores se generan aquí, con el generador aleatorio del sistema, y no salen de este equipo.
# Cada variable con el valor CHANGE_ME en .env.example recibe un valor aleatorio.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EJEMPLO="$RAIZ/.env.example"
DESTINO="$RAIZ/.env"
ROTAR=false
[ "${1:-}" = "--rotate" ] && ROTAR=true

[ -f "$EJEMPLO" ] || { echo "No se encontró $EJEMPLO" >&2; exit 1; }

# 24 caracteres alfanuméricos + sufijo "Aa1": cumple la política de contraseñas de auth-service
# (mayúscula, minúscula y número) y no contiene símbolos que rompan URLs de JDBC/AMQP.
generar() {
  local valor
  valor="$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 24)"
  printf '%sAa1' "$valor"
}

[ -f "$DESTINO" ] || { cp "$EJEMPLO" "$DESTINO"; echo "Creado .env a partir de .env.example"; }

agregadas=0; generadas=0
while IFS= read -r linea || [ -n "$linea" ]; do
  case "$linea" in ''|\#*) continue ;; esac
  clave="${linea%%=*}"
  valor_ejemplo="${linea#*=}"

  actual="$(grep -E "^${clave}=" "$DESTINO" | head -n1 | cut -d= -f2- || true)"

  if [ -z "$actual" ] && ! grep -qE "^${clave}=" "$DESTINO"; then
    # La variable no existe en .env (por ejemplo, se agregó después): se añade
    echo "${clave}=${valor_ejemplo}" >> "$DESTINO"
    actual="$valor_ejemplo"; agregadas=$((agregadas + 1))
  fi

  if [ "$valor_ejemplo" = "CHANGE_ME" ] && { [ "$actual" = "CHANGE_ME" ] || [ "$ROTAR" = true ]; }; then
    nuevo="$(generar)"
    # reemplazo seguro: el valor generado es alfanumérico
    sed -i.bak "s|^${clave}=.*|${clave}=${nuevo}|" "$DESTINO" && rm -f "$DESTINO.bak"
    generadas=$((generadas + 1))
  fi
done < "$EJEMPLO"

echo "Listo: $generadas secreto(s) generado(s), $agregadas variable(s) agregada(s) en .env"
echo "Recuerde: SONAR_TOKEN se genera dentro de SonarQube y se pega a mano en .env."
if [ "$ROTAR" = true ]; then
  echo "AVISO: tras rotar, las bases de datos y RabbitMQ conservan las contraseñas anteriores en sus volúmenes."
  echo "       Recréelos con: docker compose down -v   (borra los datos locales) y luego docker compose up --build"
fi
