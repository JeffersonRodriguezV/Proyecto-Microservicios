package com.microservicios.gestionempleados.controller;

import com.microservicios.gestionempleados.exception.ErrorResponse;
import com.microservicios.gestionempleados.model.Empleado;
import com.microservicios.gestionempleados.service.EmpleadoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba la traducción de excepciones de negocio a códigos HTTP (la lógica real del controller).
 * Se invoca el controller directamente: no hace falta levantar un servidor ni MockMvc.
 */
@ExtendWith(MockitoExtension.class)
class EmpleadoControllerTest {

    @Mock
    private EmpleadoService servicio;
    @InjectMocks
    private EmpleadoController controller;

    private static ErrorResponse error(ResponseEntity<?> r) {
        return (ErrorResponse) r.getBody();
    }

    @Test
    @DisplayName("POST: éxito -> 200 con el empleado creado")
    void crearOk() {
        Empleado e = new Empleado();
        when(servicio.crearEmpleado(e)).thenReturn(e);

        ResponseEntity<?> r = controller.crearEmpleado(e);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertSame(e, r.getBody());
    }

    @Test
    @DisplayName("POST: regla de negocio violada (duplicado, departamento inexistente) -> 400 con el mensaje")
    void crearRechazado() {
        Empleado e = new Empleado();
        when(servicio.crearEmpleado(e)).thenThrow(new IllegalArgumentException("El email ya está registrado"));

        ResponseEntity<?> r = controller.crearEmpleado(e);

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("El email ya está registrado", error(r).getMensaje());
        assertEquals(400, error(r).getStatus());
    }

    @Test
    @DisplayName("PUT: éxito -> 200")
    void actualizarOk() {
        Empleado e = new Empleado();
        when(servicio.actualizarEmpleado(1, e)).thenReturn(e);

        assertEquals(HttpStatus.OK, controller.actualizarEmpleado(1, e).getStatusCode());
    }

    @Test
    @DisplayName("PUT: empleado inexistente -> 404")
    void actualizarInexistente() {
        Empleado e = new Empleado();
        when(servicio.actualizarEmpleado(9, e)).thenThrow(new IllegalArgumentException("El empleado con id 9 no existe"));

        ResponseEntity<?> r = controller.actualizarEmpleado(9, e);

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals(404, error(r).getStatus());
    }

    @Test
    @DisplayName("[caracterización] PUT con departamento inexistente responde 404 (por el texto 'no existe'); sería más correcto 400")
    void actualizarDepartamentoInexistente() {
        Empleado e = new Empleado();
        when(servicio.actualizarEmpleado(1, e)).thenThrow(new IllegalArgumentException("El departamentoId 'X' no existe"));

        // El controller decide el 404 mirando si el mensaje contiene "no existe": frágil, y aquí confunde
        // "departamento inexistente" (debería ser 400) con "empleado inexistente". Se documenta el estado actual.
        assertEquals(HttpStatus.NOT_FOUND, controller.actualizarEmpleado(1, e).getStatusCode());
    }

    @Test
    @DisplayName("DELETE: éxito -> 200 y se pasa el motivo recibido")
    void retirarOk() {
        Empleado e = new Empleado();
        when(servicio.retirarEmpleado(1, "DESPIDO")).thenReturn(e);

        ResponseEntity<?> r = controller.retirarEmpleado(1, "DESPIDO");

        assertEquals(HttpStatus.OK, r.getStatusCode());
        verify(servicio).retirarEmpleado(1, "DESPIDO");
    }

    @Test
    @DisplayName("DELETE: empleado inexistente o ya retirado -> 404 con el mensaje del servicio")
    void retirarRechazado() {
        when(servicio.retirarEmpleado(1, "RENUNCIA"))
                .thenThrow(new IllegalArgumentException("El empleado con id 1 ya está retirado"));

        ResponseEntity<?> r = controller.retirarEmpleado(1, "RENUNCIA");

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals("El empleado con id 1 ya está retirado", error(r).getMensaje());
    }

    @Test
    @DisplayName("GET /{id}: existe -> 200 con el empleado")
    void obtenerExistente() {
        Empleado e = new Empleado();
        when(servicio.obtenerEmpleadoPorId(1)).thenReturn(Optional.of(e));

        ResponseEntity<?> r = controller.obtenerEmpleado(1);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertSame(e, r.getBody());
    }

    @Test
    @DisplayName("GET /{id}: no existe -> 404 con mensaje")
    void obtenerInexistente() {
        when(servicio.obtenerEmpleadoPorId(7)).thenReturn(Optional.empty());

        ResponseEntity<?> r = controller.obtenerEmpleado(7);

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals("El empleado con id 7 no existe", error(r).getMensaje());
    }

    @Test
    @DisplayName("GET sin filtro lista todos")
    void listarTodos() {
        when(servicio.listarEmpleados()).thenReturn(List.of(new Empleado()));

        ResponseEntity<?> r = controller.listarEmpleados(null, null, null);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(1, ((List<?>) r.getBody()).size());
    }

    @Test
    @DisplayName("GET con estado y fechas delega en listarPorEstado")
    void listarPorEstado() {
        LocalDate desde = LocalDate.of(2024, 1, 1);
        LocalDate hasta = LocalDate.of(2024, 1, 31);
        when(servicio.listarPorEstado("RETIRADO", desde, hasta)).thenReturn(List.of());

        ResponseEntity<?> r = controller.listarEmpleados("RETIRADO", desde, hasta);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        verify(servicio).listarPorEstado("RETIRADO", desde, hasta);
    }
}