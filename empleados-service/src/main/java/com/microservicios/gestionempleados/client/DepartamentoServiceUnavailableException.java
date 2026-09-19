package com.microservicios.gestionempleados.client;

/**
 * Señala que departamentos-service no respondió (timeout, conexión
 * rechazada, error 5xx). Es la excepción que Spring Retry va a
 * interceptar en el Paso 4 para reintentar con espera creciente.
 */
public class DepartamentoServiceUnavailableException extends RuntimeException {
    public DepartamentoServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}