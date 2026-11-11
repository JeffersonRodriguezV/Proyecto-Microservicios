#!/bin/sh
# Entrypoint común de los microservicios: soporte de Docker Compose Secrets.
#
# Convención "_FILE" (la misma de las imágenes oficiales de MySQL, Postgres, RabbitMQ...):
# si existe la variable NOMBRE_FILE apuntando a un archivo (p. ej. /run/secrets/jwt_secret),
# se exporta NOMBRE con el contenido de ese archivo y luego se ejecuta el comando original.
# Así el secreto vive en un archivo montado en memoria (/run/secrets) y NUNCA en el entorno
# del contenedor visible con `docker inspect`, ni en la imagen, ni en el repositorio.
# El código de cada servicio no cambia: sigue leyendo NOMBRE de su entorno.
set -e

for par in $(env | sed -n 's/^\([A-Za-z_][A-Za-z0-9_]*\)_FILE=.*/\1/p'); do
  archivo=$(printenv "${par}_FILE")
  [ -n "$archivo" ] || continue
  if [ ! -r "$archivo" ]; then
    echo "entrypoint-secrets: no se puede leer ${par}_FILE=$archivo" >&2
    exit 1
  fi
  valor=$(cat "$archivo")
  export "$par=$valor"
done

exec "$@"
