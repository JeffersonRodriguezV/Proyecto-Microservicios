package com.microservicios.gestionempleados.metricas;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricasNegocioTest {

    private SimpleMeterRegistry registro;
    private MetricasNegocio metricas;

    @BeforeEach
    void preparar() {
        registro = new SimpleMeterRegistry();
        metricas = new MetricasNegocio(registro);
    }

    @Test
    @DisplayName("empleadoCreado incrementa empleados.creados")
    void cuentaCreados() {
        metricas.empleadoCreado();
        metricas.empleadoCreado();
        assertEquals(2.0, registro.get("empleados.creados").counter().count());
    }

    @Test
    @DisplayName("empleadoRetirado incrementa empleados.retirados")
    void cuentaRetirados() {
        metricas.empleadoRetirado();
        assertEquals(1.0, registro.get("empleados.retirados").counter().count());
    }

    @Test
    @DisplayName("operacionRechazada separa el conteo por operación")
    void cuentaRechazosPorOperacion() {
        metricas.operacionRechazada("crear");
        metricas.operacionRechazada("crear");
        metricas.operacionRechazada("retirar");

        assertEquals(2.0, registro.get("empleados.operaciones.rechazadas").tag("operacion", "crear").counter().count());
        assertEquals(1.0, registro.get("empleados.operaciones.rechazadas").tag("operacion", "retirar").counter().count());
    }
}
