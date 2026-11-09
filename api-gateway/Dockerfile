# ==========================================
# Imagen liviana de Node.js. No necesita etapa
# de build separada (no hay compilación como en
# Java): solo se instalan dependencias y se copia
# el código.
# ==========================================
FROM node:22-alpine

WORKDIR /app

# Se copia primero package.json para aprovechar la cache de capas
# de Docker: si solo cambia el código y no las dependencias, esta
# capa no se reconstruye.
COPY package*.json ./
RUN npm install --omit=dev

COPY src ./src

EXPOSE 8080

# Se usa el propio Node (con require('http')) para el healthcheck
# en vez de instalar curl/wget, evitando una dependencia extra en
# la imagen — el healthcheck apunta al propio /health del Gateway.
HEALTHCHECK --interval=5s --timeout=3s --retries=10 --start-period=5s \
    CMD node -e "require('http').get('http://localhost:' + (process.env.PORT || 8080) + '/health', res => process.exit(res.statusCode === 200 ? 0 : 1)).on('error', () => process.exit(1))"

# --require carga OpenTelemetry ANTES que Express para instrumentar el HTTP entrante y saliente
CMD ["node", "--require", "./src/tracing.js", "src/server.js"]