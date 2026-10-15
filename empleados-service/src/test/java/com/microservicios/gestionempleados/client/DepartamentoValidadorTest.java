package com.microservicios.gestionempleados.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Prueba la decisión de negocio del fallback (sin Spring: el Circuit Breaker en sí es
 * infraestructura de Resilience4j y se prueba en la integración, no aquí).
 */
@ExtendWith(MockitoExtension.class)
class DepartamentoValidadorTest {

    @Mock
    private DepartamentoClient departamentoClient;

    @Test
    @DisplayName("delega en el cliente con reintentos")
    void delegaEnElCliente() {
        when(departamentoClient.consultarExistenciaConReintentos("DEP-1"))
                .thenReturn(DepartamentoValidacionResultado.EXISTE);

        assertEquals(DepartamentoValidacionResultado.EXISTE,
                new DepartamentoValidador(departamentoClient).consultarExistencia("DEP-1"));
    }

    @Test
    @DisplayName("el fallback (circuito abierto o reintentos agotados) devuelve INDETERMINADO, nunca NO_EXISTE")
    void fallbackEsIndeterminado() {
        DepartamentoValidador validador = new DepartamentoValidador(departamentoClient);

        assertEquals(DepartamentoValidacionResultado.INDETERMINADO,
                validador.consultarExistenciaFallback("DEP-1", new RuntimeException("caído")));
    }
}