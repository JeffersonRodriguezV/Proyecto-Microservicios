package main

import (
	_ "embed"
	"net/http"
)

// La especificación OpenAPI se incrusta en el binario al compilar, así que
// la imagen final de Docker no necesita copiar el archivo openapi.json.
//
//go:embed openapi.json
var especificacionOpenAPI []byte

// Página mínima de Swagger UI. Carga sus archivos desde un CDN, por lo que el
// navegador necesita acceso a internet para verla.
const paginaSwagger = `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <title>Notificaciones Service - API</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.17.14/swagger-ui.css">
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.17.14/swagger-ui-bundle.js"></script>
  <script>
    window.onload = function () {
      window.ui = SwaggerUIBundle({ url: "/notificaciones/openapi.json", dom_id: "#swagger-ui" });
    };
  </script>
</body>
</html>`

func servirOpenAPI(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Write(especificacionOpenAPI)
}

func servirSwaggerUI(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Write([]byte(paginaSwagger))
}
