package com.microservicios.authservice.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilidadConfigTest {

    private final ObservationPredicate predicado = new ObservabilidadConfig().omitirRutasDeMonitoreo();

    private boolean observa(String ruta) {
        var contexto = new ServerRequestObservationContext(new MockHttpServletRequest("GET", ruta), new MockHttpServletResponse());
        return predicado.test("http.server.requests", contexto);
    }

    @Test
    @DisplayName("no observa /health ni /actuator/** (healthcheck y Prometheus)")
    void omiteMonitoreo() {
        assertFalse(observa("/health"));
        assertFalse(observa("/actuator/prometheus"));
        assertFalse(observa("/actuator/health"));
    }

    @Test
    @DisplayName("sí observa las rutas de negocio y los contextos que no son HTTP")
    void observaNegocio() {
        assertTrue(observa("/empleados"));
        assertTrue(predicado.test("otra", new Observation.Context()));
    }
}
