package com.microservicios.gestionempleados.controller;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de /health: sin Spring, sin base de datos y sin RabbitMQ (todo con dobles). */
class HealthControllerTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    private final HealthController controller = new HealthController(dataSource, connectionFactory, "empleados-service");

    private void baseDatos(boolean valida) throws SQLException {
        java.sql.Connection conexion = mock(java.sql.Connection.class);
        when(conexion.isValid(anyInt())).thenReturn(valida);
        when(dataSource.getConnection()).thenReturn(conexion);
    }

    private void broker(boolean abierto) {
        Connection conexion = mock(Connection.class);
        when(conexion.isOpen()).thenReturn(abierto);
        when(connectionFactory.createConnection()).thenReturn(conexion);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> checks(ResponseEntity<Map<String, Object>> r) {
        return (Map<String, String>) r.getBody().get("checks");
    }

    @Test
    void todoUp_responde200ConFormatoComun() throws SQLException {
        baseDatos(true);
        broker(true);

        ResponseEntity<Map<String, Object>> r = controller.health();

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("UP", r.getBody().get("status"));
        assertEquals("empleados-service", r.getBody().get("service"));
        assertEquals("UP", checks(r).get("database"));
        assertEquals("UP", checks(r).get("messageBroker"));
    }

    @Test
    void brokerCaido_responde503() throws SQLException {
        baseDatos(true);
        broker(false);

        ResponseEntity<Map<String, Object>> r = controller.health();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.getStatusCode());
        assertEquals("DOWN", r.getBody().get("status"));
        assertEquals("UP", checks(r).get("database"));
        assertEquals("DOWN", checks(r).get("messageBroker"));
    }

    @Test
    void baseDeDatosInvalida_responde503() throws SQLException {
        baseDatos(false);
        broker(true);

        ResponseEntity<Map<String, Object>> r = controller.health();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.getStatusCode());
        assertEquals("DOWN", checks(r).get("database"));
    }

    @Test
    void excepcionesEnLasDependencias_cuentanComoDown() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("sin bd"));
        when(connectionFactory.createConnection()).thenThrow(new RuntimeException("sin broker"));

        ResponseEntity<Map<String, Object>> r = controller.health();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.getStatusCode());
        assertEquals("DOWN", checks(r).get("database"));
        assertEquals("DOWN", checks(r).get("messageBroker"));
    }
}
