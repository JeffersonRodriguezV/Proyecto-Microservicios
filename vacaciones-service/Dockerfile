# Usa Node.js 22 sobre Alpine Linux, una imagen ligera
FROM node:22-alpine

# Define el directorio de trabajo dentro del contenedor
WORKDIR /app
# Instala wget para utilizarlo en el healthcheck
RUN apk add --no-cache wget
# Copia los archivos de dependencias
COPY package*.json ./
# Instala únicamente las dependencias de producción
RUN npm ci --omit=dev
# Copia el código fuente de la aplicación
COPY src ./src

EXPOSE 8085
# Comprueba cada 5 segundos que el servidor esté respondiendo
# Si no responde correctamente después de 10 intentos, se marca como unhealthy
HEALTHCHECK --interval=5s --timeout=3s --retries=10 --start-period=10s \
    CMD wget -qO- http://localhost:8085/health || exit 1
# Inicia el servidor usando SQLite experimental de Node.js
CMD ["node", "--experimental-sqlite", "src/server.js"]