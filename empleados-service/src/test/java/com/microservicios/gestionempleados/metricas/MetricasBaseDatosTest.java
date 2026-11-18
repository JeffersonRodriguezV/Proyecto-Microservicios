package com.microservicios.gestionempleados.metricas;

import com.microservicios.gestionempleados.model.enume.EstadoEmpleado;
import com.microservicios.gestionempleados.repository.ConteoPorDepartamento;
import com.microservicios.gestionempleados.repository.EmpleadoRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de las metricas basadas en la base de datos (repositorio simulado). */
class MetricasBaseDatosTest {

    private EmpleadoRepository repo;
    private SimpleMeterRegistry registro;
    private MetricasBaseDatos metricas;

    @BeforeEach
    void preparar() {
        repo = mock(EmpleadoRepository.class);
        registro = new SimpleMeterRegistry();
        metricas = new MetricasBaseDatos(repo, registro);
    }

    @Test
    void antesDeLaPrimeraLecturaLaBaseNoSeConsideraDisponible() {
        assertEquals(0.0, registro.get("empleados.bd.disponible").gauge().value());
    }

    @Test
    void refrescarPublicaTotalesPorEstadoDepartamentoYSinValidar() {
        when(repo.countByEstado(EstadoEmpleado.ACTIVO)).thenReturn(5L);
        when(repo.countByEstado(EstadoEmpleado.EN_VACACIONES)).thenReturn(2L);
        when(repo.countByEstado(EstadoEmpleado.RETIRADO)).thenReturn(1L);
        when(repo.countByDepartamentoValidadoFalse()).thenReturn(3L);
        when(repo.contarVigentesPorDepartamento()).thenReturn(
                List.of(new ConteoPorDepartamento("TI", 4), new ConteoPorDepartamento("RH", 3)));

        metricas.refrescar();

        assertEquals(1.0, registro.get("empleados.bd.disponible").gauge().value());
        assertEquals(5.0, registro.get("empleados.bd.por_estado").tag("estado", "ACTIVO").gauge().value());
        assertEquals(2.0, registro.get("empleados.bd.por_estado").tag("estado", "EN_VACACIONES").gauge().value());
        assertEquals(1.0, registro.get("empleados.bd.por_estado").tag("estado", "RETIRADO").gauge().value());
        assertEquals(3.0, registro.get("empleados.bd.sin_validar").gauge().value());
        assertEquals(4.0, registro.get("empleados.bd.por_departamento").tag("departamento", "TI").gauge().value());
        assertEquals(3.0, registro.get("empleados.bd.por_departamento").tag("departamento", "RH").gauge().value());
    }

    @Test
    void siLaBaseFallaMarcaNoDisponibleYConservaUltimosTotales() {
        when(repo.countByEstado(any())).thenReturn(7L);
        when(repo.contarVigentesPorDepartamento()).thenReturn(List.of());
        metricas.refrescar();

        when(repo.countByEstado(any())).thenThrow(new RuntimeException("MySQL caido"));
        metricas.refrescar();

        assertEquals(0.0, registro.get("empleados.bd.disponible").gauge().value());
        assertEquals(7.0, registro.get("empleados.bd.por_estado").tag("estado", "ACTIVO").gauge().value());
    }

    @Test
    void publicaSoloLosDiezMayoresDepartamentosYAgrupaElRestoEnOtros() {
        when(repo.contarVigentesPorDepartamento()).thenReturn(java.util.stream.IntStream.rangeClosed(1, 13)
                .mapToObj(i -> new ConteoPorDepartamento("D" + String.format("%02d", i), i)).toList());

        metricas.refrescar();

        // Mayores: D13..D04. Quedan fuera D03, D02, D01 (3+2+1 = 6) -> "otros"
        assertEquals(13.0, registro.get("empleados.bd.por_departamento").tag("departamento", "D13").gauge().value());
        assertEquals(6.0, registro.get("empleados.bd.por_departamento").tag("departamento", "otros").gauge().value());
        assertEquals(11, registro.find("empleados.bd.por_departamento").gauges().size());
    }
}