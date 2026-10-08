package com.microservicios.authservice.exception;

import com.microservicios.authservice.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({CredencialesInvalidasException.class, TokenAccesoInvalidoException.class})
    public ResponseEntity<ErrorResponse> noAutenticado(RuntimeException e) {
        return respuesta(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(CuentaNoActivaException.class)
    public ResponseEntity<ErrorResponse> cuentaNoActiva(CuentaNoActivaException e) {
        return respuesta(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler({TokenInvalidoException.class, PasswordInvalidaException.class})
    public ResponseEntity<ErrorResponse> peticionRechazada(RuntimeException e) {
        return respuesta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> peticionInvalida(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST, "La petición es inválida: revise los campos obligatorios");
    }

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), mensaje, LocalDateTime.now()));
    }
}
