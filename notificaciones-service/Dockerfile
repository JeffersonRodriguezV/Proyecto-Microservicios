# ==========================================
# Etapa 1: build - compila el binario de Go
# Se usa el tag "alpine" sin fijar versión exacta de Go, para
# evitar el riesgo de pedir una versión que todavía no exista
# publicada en Docker Hub (ya nos pasó con otras imágenes muy
# nuevas en este proyecto).
# ==========================================
FROM golang:alpine AS build

WORKDIR /build

COPY go.mod go.sum ./
RUN go mod download

COPY . .

# CGO_ENABLED=0 genera un binario completamente estático -- posible
# porque el driver de SQLite usado (modernc.org/sqlite) es Go puro,
# sin dependencias de C.
RUN CGO_ENABLED=0 go build -o notificaciones-service .

# ==========================================
# Etapa 2: runtime - imagen mínima solo con el binario
# ==========================================
FROM alpine:3.20

WORKDIR /app

RUN apk add --no-cache wget

COPY --from=build /build/notificaciones-service .

EXPOSE 8084

HEALTHCHECK --interval=5s --timeout=3s --retries=10 --start-period=10s \
    CMD wget -qO- http://localhost:8084/health || exit 1

CMD ["./notificaciones-service"]