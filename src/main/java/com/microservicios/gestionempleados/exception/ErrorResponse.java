package com.microservicios.gestionempleados.exception;



import java.time.LocalDateTime;

/**
 * Representa la estructura estándar de una respuesta de error
 * devuelta por la API en formato JSON.
 */
public class ErrorResponse {

    private int status;
    private String mensaje;
    private LocalDateTime timestamp;

    public ErrorResponse(int status, String mensaje) {
        this.status = status;
        this.mensaje = mensaje;
        this.timestamp = LocalDateTime.now();
    }

    public int getStatus() {
        return status;
    }

    public String getMensaje() {
        return mensaje;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
