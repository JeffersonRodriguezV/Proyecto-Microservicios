#!/usr/bin/env bash
# Genera los secretos locales del proyecto para Docker Compose Secrets.
#
# Uso:
#   bash scripts/setup-secrets.sh            # crea lo que falte (no toca secretos existentes)
#   bash scripts/setup-secrets.sh --rotate   # regenera TODOS los secretos (rotación)
#
# Crea:
#   .env                    configuración no secreta (copia de .env.example si no existe)
#   secrets/<nombre>        un archivo por secreto, que Compose monta en /run/secrets/<nombre>
#   secrets/secrets.env     los mismos valores en formato KEY=VALUE (credencial "env-sistema" del E2E en Jenkins)
#   e2e-tests/.env          credenciales de la suite BDD (solo si falta o con --rotate)
#
# Orden de decisión para cada secreto: archivo ya existente > valor que ya estaba en .env
# (migración desde la versión anterior: así no se rompen las bases de datos ya creadas) > valor aleatorio.
# Los valores se generan aquí y no salen de este equipo.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROTAR=false
[ "${1:-}" = "--rotate" ] && ROTAR=true

# nombre_del_archivo:CLAVE_EN_ENV:tipo   (tipo: corto | largo | fijo:<valor>)
SECRETOS=(
  "jwt_secret:JWT_SECRET:largo"
  "admin_password:ADMIN_PASSWORD:corto"
  "seed_user_password:SEED_USER_PASSWORD:corto"
  "rabbitmq_password:RABBITMQ_PASSWORD:corto"
  "db_empleados_password:DB_EMPLEADOS_PASSWORD:corto"
  "db_empleados_root_password:DB_EMPLEADOS_ROOT_PASSWORD:corto"
  "db_auth_password:DB_AUTH_PASSWORD:corto"
  "db_auth_root_password:DB_AUTH_ROOT_PASSWORD:corto"
  "db_departamentos_password:DB_DEPARTAMENTOS_PASSWORD:corto"
  "grafana_admin_password:GRAFANA_ADMIN_PASSWORD:corto"
  "discord_webhook_url:DISCORD_WEBHOOK_URL:fijo:https://discord.com/api/webhooks/0/CAMBIAR_POR_WEBHOOK_REAL"
  "jenkins_admin_password:JENKINS_ADMIN_PASSWORD:corto"
  "sonar_token:SONAR_TOKEN:fijo:pendiente"
  "sonar_db_password:SONAR_DB_PASSWORD:corto"
)

# Alfanuméricos + sufijo "Aa1": cumple la política de contraseñas de auth-service y no rompe URLs JDBC/AMQP.
generar() { printf '%sAa1' "$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c "$1")"; }

# Lee una clave de un archivo .env (último valor), vacío si no existe.
leer_env() { [ -f "$2" ] && grep -E "^$1=" "$2" | tail -n1 | cut -d= -f2- | tr -d '\r' || true; }

mkdir -p "$RAIZ/secrets"
[ -f "$RAIZ/.env" ] || { cp "$RAIZ/.env.example" "$RAIZ/.env"; echo "Creado .env a partir de .env.example"; }

nuevos=0; migrados=0; conservados=0
: > "$RAIZ/secrets/.secrets.env.tmp"
for def in "${SECRETOS[@]}"; do
  IFS=: read -r nombre clave tipo extra <<< "$def"
  # "fijo:https://..." contiene ':' -> reconstruir el valor completo
  fijo="${def#*:*:fijo:}"
  archivo="$RAIZ/secrets/$nombre"
  valor=""; origen=""
  if [ "$ROTAR" = false ] && [ -s "$archivo" ]; then
    valor="$(cat "$archivo")"; origen=conservado
  fi
  if [ -z "$valor" ] && [ "$ROTAR" = false ]; then
    previo="$(leer_env "$clave" "$RAIZ/.env")"
    case "$previo" in ''|CHANGE_ME|CAMBIAR*) ;; *) valor="$previo"; origen=migrado ;; esac
  fi
  if [ -z "$valor" ]; then
    case "$tipo" in
      largo) valor="$(generar 48)" ;;
      fijo)  valor="$fijo" ;;
      *)     valor="$(generar 24)" ;;
    esac
    origen=nuevo
  fi
  printf '%s' "$valor" > "$archivo"
  chmod 644 "$archivo"   # los contenedores no-root (auth-service) deben poder leerlo; la carpeta no se sube a Git
  printf '%s=%s\n' "$clave" "$valor" >> "$RAIZ/secrets/.secrets.env.tmp"
  case "$origen" in nuevo) nuevos=$((nuevos+1));; migrado) migrados=$((migrados+1));; *) conservados=$((conservados+1));; esac
done
mv "$RAIZ/secrets/.secrets.env.tmp" "$RAIZ/secrets/secrets.env"; chmod 644 "$RAIZ/secrets/secrets.env"

# .env de la suite BDD (Cucumber): mismas credenciales que los usuarios semilla
E2E="$RAIZ/e2e-tests/.env"
if [ ! -f "$E2E" ] || [ "$ROTAR" = true ]; then
  admin_email="$(leer_env ADMIN_EMAIL "$RAIZ/.env")"; user_email="$(leer_env SEED_USER_EMAIL "$RAIZ/.env")"
  {
    echo "BASE_URL=http://localhost:8080"
    echo "ADMIN_EMAIL=${admin_email:-admin@empresa.com}"
    echo "ADMIN_PASSWORD=$(cat "$RAIZ/secrets/admin_password")"
    echo "USER_EMAIL=${user_email:-user@empresa.com}"
    echo "USER_PASSWORD=$(cat "$RAIZ/secrets/seed_user_password")"
  } > "$E2E"
  echo "Generado e2e-tests/.env"
fi

echo "Listo: $nuevos nuevo(s), $migrados migrado(s) desde .env, $conservados conservado(s) en secrets/"
echo "Recuerde: sonar_token se genera dentro de SonarQube (Mi cuenta > Seguridad) y se pega en secrets/sonar_token."
echo "          discord_webhook_url: pegue su webhook real en secrets/discord_webhook_url (sin salto de línea)."
if [ "$ROTAR" = true ]; then
  echo "AVISO: tras rotar, las bases de datos y RabbitMQ conservan las contraseñas anteriores en sus volúmenes."
  echo "       Recréelos con: docker compose down -v   (borra los datos locales) y luego docker compose up --build"
fi
