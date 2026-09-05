package com.microservicios.gestionempleados.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de errores personalizado.
 * Reemplaza el manejo por defecto de Spring Boot para
 * rutas no encontradas y métodos no soportados,
 * devolviendo una respuesta uniforme: 404 "Recurso no encontrado".
 */
@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<String> handleError(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        int status = (statusCode != null) ? Integer.parseInt(statusCode.toString()) : 500;

        if (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Recurso no encontrado");
        }

        // Para cualquier otro error inesperado (500, etc.)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Ocurrió un error inesperado");
    }
}