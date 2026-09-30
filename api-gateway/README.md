# API Gateway - Reto 3

Punto de entrada único del sistema de microservicios de gestión de
empleados y departamentos. A partir de este reto, ningún microservicio
es accesible directamente desde fuera de la red de Docker: todo el
tráfico externo pasa por aquí.

## Tecnologías

- Node.js 22
- Express
- http-proxy-middleware
- Docker

## Qué hace

Enruta las peticiones entrantes hacia los microservicios internos:

- `/empleados/*` → `empleados-service`
- `/departamentos/*` → `departamentos-service`

Reenvía el cuerpo, las cabeceras y el código de estado **sin alterarlos**.
Si el servicio destino no responde (timeout, conexión rechazada), devuelve
`503 Service Unavailable` con un cuerpo JSON descriptivo, en vez de dejar
que el request se cuelgue o que se propague un error crudo de Node.

## Puertos

| Contexto | Puerto |
|---|---|
| Interno (dentro del contenedor) | `3000` (variable `PORT`) |
| Publicado al host | `8080` — **único puerto público de todo el sistema** |

## Variables de entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `PORT` | Puerto interno donde escucha el Gateway | `3000` |
| `EMPLEADOS_URL` | URL base de `empleados-service` | `http://localhost:8080` |
| `DEPARTAMENTOS_URL` | URL base de `departamentos-service` | `http://localhost:8081` |

Los valores por defecto están pensados para pruebas locales fuera de
Docker.
Dentro de `docker-compose.yml` se sobreescriben con los
hostnames internos de la red (`http://empleados-service:8080`,
`http://departamentos-service:8081`).

## Ejecución local (sin Docker)

```bash
npm install
npm start
```

Disponible en `http://localhost:3000`.

## Ejecución con Docker

```bash
docker build -t api-gateway .
docker run -p 8080:3000 \
  -e EMPLEADOS_URL=http://localhost:8080 \
  -e DEPARTAMENTOS_URL=http://localhost:8081 \
  api-gateway
```

## Endpoints

### `GET /health`

Devuelve el estado del propio Gateway.

```json
{ "servicio": "api-gateway", "estado": "activo" }
```

### Rutas reenviadas (proxy)

| Ruta externa | Servicio interno |
|---|---|
| `/empleados/*` | `empleados-service` |
| `/departamentos/*` | `departamentos-service` |

## Manejo de errores

Si el servicio destino no responde:

```json
{
  "status": 503,
  "mensaje": "El servicio en /departamentos no está disponible",
  "timestamp": "2026-09-22T03:53:47.297Z"
}
```

## Decisiones de diseño

### Healthcheck sin dependencias extra

`Dockerfile` usa el propio Node (`require('http')`) para el
`HEALTHCHECK`, evitando instalar `curl` o `wget` en la imagen.

## Estructura del proyecto

```
api-gateway/
├── src/
│   └── server.js
├── package.json
├── package-lock.json
├── Dockerfile
├── .dockerignore
└── .gitignore
```