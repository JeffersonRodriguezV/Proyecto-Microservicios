package com.microservicios.gestionempleados.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Se lanza cuando un objeto anotado con @Valid falla alguna
     * de sus validaciones (@NotBlank, @Email, etc.).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), mensaje);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }


    /**
     * Se lanza cuando el JSON recibido no se puede convertir correctamente
     * al objeto esperado: formato de fecha inválido, valor de enum
     * inexistente, JSON mal formado, etc.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonInvalido(HttpMessageNotReadableException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "El cuerpo de la petición contiene datos inválidos o mal formateados"
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}