package com.microservicios.gestionempleados.client;

import com.microservicios.gestionempleados.service.EmpleadoService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Con un CircuitBreaker REAL de Resilience4j: al volver a CLOSED (el servicio se recuperó) se reconcilian
 * los empleados pendientes; en cualquier otra transición no se hace nada.
 */
@ExtendWith(MockitoExtension.class)
class CircuitBreakerRecoveryListenerTest {

    @Mock
    private EmpleadoService empleadoService;

    private CircuitBreaker circuito;

    @BeforeEach
    void setUp() {
        CircuitBreakerRegistry registro = CircuitBreakerRegistry.ofDefaults();
        new CircuitBreakerRecoveryListener(registro, empleadoService);
        circuito = registro.circuitBreaker("departamentosService");
    }

    @Test
    @DisplayName("abrir el circuito NO dispara la reconciliación")
    void abrirNoReconcilia() {
        circuito.transitionToOpenState();

        verify(empleadoService, never()).reconciliarDepartamentosPendientes();
    }

    @Test
    @DisplayName("OPEN -> HALF_OPEN -> CLOSED dispara la reconciliación una sola vez")
    void cerrarReconcilia() {
        circuito.transitionToOpenState();
        circuito.transitionToHalfOpenState();
        circuito.transitionToClosedState();

        verify(empleadoService, times(1)).reconciliarDepartamentosPendientes();
    }
}