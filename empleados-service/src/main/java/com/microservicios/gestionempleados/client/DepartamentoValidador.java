package com.microservicios.gestionempleados.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;

/**
 * Envuelve a DepartamentoClient (retry) con un Circuit Breaker.
 *
 * Está en una clase separada a propósito: @CircuitBreaker y @Retryable
 * son ambos proxies de Spring AOP, y una llamada interna entre métodos
 * de la MISMA clase no pasa por el proxy (auto-invocación). Al estar en
 * beans distintos, la llamada de este método hacia DepartamentoClient
 * sí atraviesa el proxy de @Retryable correctamente.
 */
@Component
public class DepartamentoValidador {

    private final DepartamentoClient departamentoClient;

    public DepartamentoValidador(DepartamentoClient departamentoClient) {
        this.departamentoClient = departamentoClient;
    }

    /**
     * Punto de entrada que usa EmpleadoService. Mientras el circuito
     * está CLOSED, delega en DepartamentoClient (con su retry completo).
     * Si el circuito está OPEN, Resilience4j ni siquiera ejecuta este
     * cuerpo: va directo a consultarExistenciaFallback().
     */
    @CircuitBreaker(name = "departamentosService", fallbackMethod = "consultarExistenciaFallback")
    public DepartamentoValidacionResultado consultarExistencia(String departamentoId) {
        return departamentoClient.consultarExistenciaConReintentos(departamentoId);
    }

    /**
     * Fallback del Circuit Breaker. Se dispara en dos casos distintos:
     * - Circuito OPEN: ni se intentó la llamada real.
     * - Circuito CLOSED/HALF_OPEN pero se agotaron los reintentos
     *   (la excepción relanzada por DepartamentoClient).
     *
     * En ambos casos, la decisión de negocio del equipo es la misma
     * que en el Reto 2: aceptar como pendiente de validación.
     */
    public DepartamentoValidacionResultado consultarExistenciaFallback(String departamentoId, Throwable t) {
        return DepartamentoValidacionResultado.INDETERMINADO;
    }
}