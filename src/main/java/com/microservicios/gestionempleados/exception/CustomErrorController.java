package com.microservicios.gestionempleados.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = (statusCode != null) ? Integer.parseInt(statusCode.toString()) : 500;

        if (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            ErrorResponse error = new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Recurso no encontrado");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Ocurrió un error inesperado");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}