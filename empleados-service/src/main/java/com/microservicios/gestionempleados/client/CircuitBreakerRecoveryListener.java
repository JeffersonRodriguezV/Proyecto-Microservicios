package com.microservicios.gestionempleados.client;

import com.microservicios.gestionempleados.service.EmpleadoService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;
/**
 * Escucha los cambios de estado del Circuit Breaker de
 * departamentos-service. Cuando pasa a CLOSED (se confirmó que el
 * servicio volvió a responder), dispara la reconciliación de los
 * empleados que quedaron pendientes de validación — sin necesidad
 * de reiniciar el servicio ni consultar por tiempo (polling).
 */
@Component
public class CircuitBreakerRecoveryListener {
    public CircuitBreakerRecoveryListener(
            CircuitBreakerRegistry registry,
            EmpleadoService empleadoService
    ) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("departamentosService");

        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.State estadoNuevo = event.getStateTransition().getToState();
            if (estadoNuevo == CircuitBreaker.State.CLOSED) {
                empleadoService.reconciliarDepartamentosPendientes();
            }
        });
    }

}
