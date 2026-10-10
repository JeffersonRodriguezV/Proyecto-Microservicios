package com.microservicios.gestionempleados.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

/**
 * Contrato OpenAPI de empleados-service.
 * Declara el esquema "BearerAuth" (JWT) y lo exige por defecto en todas las operaciones:
 * en Swagger UI aparece el botón "Authorize". Es solo documentación: el JWT lo valida
 * el API Gateway, no este servicio (empleados llama a departamentos sin token).
 * servers = "/" hace que "Try it out" use el host desde el que se abrió Swagger (el Gateway).
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Empleados Service API",
                version = "1.0",
                description = "Registro, consulta y retiro de empleados. "
                        + "Requiere JWT: obténgalo en POST /auth/login y use el botón Authorize."),
        servers = @Server(url = "/", description = "Servidor actual"),
        security = @SecurityRequirement(name = "BearerAuth"))
@SecurityScheme(
        name = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Pegue el accessToken devuelto por /auth/login (sin el prefijo 'Bearer ').")
public class OpenApiConfig {
}