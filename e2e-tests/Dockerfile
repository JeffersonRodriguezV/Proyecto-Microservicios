# Imagen de la suite BDD (Reto 6): corre Cucumber.js sin instalar nada en el equipo.
FROM node:22-alpine

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

COPY . .

CMD ["npx", "--no-install", "cucumber-js"]