package com.microservicios.authservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

/**
 * Metadatos del contrato OpenAPI del auth-service.
 * Define el esquema "BearerAuth" (JWT HS256) que usan los endpoints protegidos;
 * en Swagger UI aparece el botón "Authorize" para pegar el accessToken.
 * Swagger UI: /swagger-ui.html  ·  JSON: /v3/api-docs
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Auth Service API",
                version = "1.0",
                description = "Emisión de JWT, recuperación y cambio de contraseña. "
                        + "El Gateway valida el JWT con el mismo secreto compartido (JWT_SECRET)."),
        servers = @Server(url = "/", description = "Servidor actual"))
@SecurityScheme(
        name = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Pegue el accessToken devuelto por /auth/login (sin el prefijo 'Bearer ').")
public class OpenApiConfig {
}
