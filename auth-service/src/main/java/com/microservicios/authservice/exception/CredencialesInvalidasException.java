package com.microservicios.authservice.exception;

public class CredencialesInvalidasException extends RuntimeException {
    public CredencialesInvalidasException() {
        super("Credenciales inválidas");
    }
}