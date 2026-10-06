package com.microservicios.authservice.exception;

import com.microservicios.authservice.model.EstadoCuenta;

public class CuentaNoActivaException extends RuntimeException {
    public CuentaNoActivaException(EstadoCuenta estado) {
        super("La cuenta no está activa (estado: " + estado + ")");
    }
}