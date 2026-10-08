package com.microservicios.authservice.exception;

public class PasswordInvalidaException extends RuntimeException {
    public PasswordInvalidaException(String mensaje) {
        super(mensaje);
    }
}