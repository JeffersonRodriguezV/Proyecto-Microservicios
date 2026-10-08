package com.microservicios.authservice.exception;

public class TokenAccesoInvalidoException extends RuntimeException {
    public TokenAccesoInvalidoException() {
        super("Token de acceso requerido o inválido");
    }
}
