package com.microservicios.gestionempleados.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Formato de los errores que ve el cliente de la API. */
@ExtendWith(MockitoExtension.class)
class ManejoDeErroresTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final CustomErrorController errores = new CustomErrorController();

    @Mock
    private HttpServletRequest request;

    @Test
    @DisplayName("validación @Valid fallida -> 400 con 'campo: mensaje' por cada error")
    void validacion() {
        BindingResult binding = mock(BindingResult.class);
        when(binding.getFieldErrors()).thenReturn(List.of(
                new FieldError("empleado", "nombre", "El nombre es obligatorio"),
                new FieldError("empleado", "email", "El email debe tener un formato válido")));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(binding);

        ResponseEntity<ErrorResponse> r = handler.handleValidationErrors(ex);

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("nombre: El nombre es obligatorio, email: El email debe tener un formato válido",
                r.getBody().getMensaje());
    }

    @Test
    @DisplayName("JSON mal formado -> 400 con mensaje genérico (no filtra detalles internos)")
    void jsonInvalido() {
        ResponseEntity<ErrorResponse> r = handler.handleJsonInvalido(mock(HttpMessageNotReadableException.class));

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("El cuerpo de la petición contiene datos inválidos o mal formateados", r.getBody().getMensaje());
    }

    @Test
    @DisplayName("/error con 404 -> 'Recurso no encontrado'")
    void error404() {
        when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);

        ResponseEntity<ErrorResponse> r = errores.handleError(request);

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals("Recurso no encontrado", r.getBody().getMensaje());
    }

    @Test
    @DisplayName("/error con 405 también se presenta como 404")
    void error405() {
        when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(405);

        assertEquals(HttpStatus.NOT_FOUND, errores.handleError(request).getStatusCode());
    }

    @Test
    @DisplayName("/error sin código o con otro código -> 500 con mensaje genérico")
    void errorInesperado() {
        when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(null);

        ResponseEntity<ErrorResponse> r = errores.handleError(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, r.getStatusCode());
        assertEquals("Ocurrió un error inesperado", r.getBody().getMensaje());
    }
}