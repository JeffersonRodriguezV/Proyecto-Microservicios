# Imagen de la suite BDD (Reto 6): corre Cucumber.js sin instalar nada en el equipo.
FROM node:22-alpine

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

COPY . .

# Soporte de Docker Secrets: exporta NOMBRE desde NOMBRE_FILE antes de arrancar el servicio
COPY --chmod=755 entrypoint-secrets.sh /usr/local/bin/entrypoint-secrets.sh
ENTRYPOINT ["/usr/local/bin/entrypoint-secrets.sh"]

CMD ["npx", "--no-install", "cucumber-js"]