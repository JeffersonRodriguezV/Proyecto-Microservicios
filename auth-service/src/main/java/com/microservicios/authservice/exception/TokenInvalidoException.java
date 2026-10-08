package com.microservicios.authservice.exception;

public class TokenInvalidoException extends RuntimeException {
    public TokenInvalidoException() {
        super("El token es inválido o ha expirado");
    }
}