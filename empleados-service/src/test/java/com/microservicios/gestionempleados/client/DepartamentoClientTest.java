package com.microservicios.gestionempleados.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Cómo se traduce cada respuesta de departamentos-service. Se usa un RestClient REAL contra un servidor
 * HTTP simulado (MockRestServiceServer): se prueba el código HTTP de verdad, sin red.
 * Los reintentos (@Retryable) los aplica Spring AOP y se verifican en integración, no aquí.
 */
class DepartamentoClientTest {

    private MockRestServiceServer servidor;
    private DepartamentoClient cliente;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://departamentos");
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new DepartamentoClient(builder.build());
    }

    @Test
    @DisplayName("200 -> EXISTE")
    void existe() {
        servidor.expect(requestTo("http://departamentos/departamentos/DEP-1")).andRespond(withSuccess());

        assertEquals(DepartamentoValidacionResultado.EXISTE, cliente.consultarExistenciaConReintentos("DEP-1"));
        servidor.verify();
    }

    @Test
    @DisplayName("404 -> NO_EXISTE (respuesta real: no se reintenta ni se acepta como pendiente)")
    void noExiste() {
        servidor.expect(requestTo("http://departamentos/departamentos/X")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals(DepartamentoValidacionResultado.NO_EXISTE, cliente.consultarExistenciaConReintentos("X"));
    }

    @Test
    @DisplayName("500 -> DepartamentoServiceUnavailableException (para que se reintente y cuente en el circuit breaker)")
    void errorDelServidor() {
        servidor.expect(requestTo("http://departamentos/departamentos/DEP-1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(DepartamentoServiceUnavailableException.class,
                () -> cliente.consultarExistenciaConReintentos("DEP-1"));
    }

    @Test
    @DisplayName("fallo de conexión -> DepartamentoServiceUnavailableException")
    void sinConexion() {
        servidor.expect(requestTo("http://departamentos/departamentos/DEP-1"))
                .andRespond(withException(new IOException("conexión rechazada")));

        assertThrows(DepartamentoServiceUnavailableException.class,
                () -> cliente.consultarExistenciaConReintentos("DEP-1"));
    }

    @Test
    @DisplayName("al agotar los reintentos se RELANZA el error (no se absorbe como INDETERMINADO)")
    void recuperacionRelanza() {
        DepartamentoServiceUnavailableException original =
                new DepartamentoServiceUnavailableException("caído", new RuntimeException());

        DepartamentoServiceUnavailableException lanzada = assertThrows(DepartamentoServiceUnavailableException.class,
                () -> cliente.recuperarTrasFallosAgotados(original, "DEP-1"));

        assertSame(original, lanzada);
    }

    @Test
    @DisplayName("la configuración construye el RestClient con la URL y los timeouts configurados")
    void configuracion() {
        DepartamentosClientConfig config = new DepartamentosClientConfig();
        ReflectionTestUtils.setField(config, "departamentosServiceUrl", "http://departamentos:8081");
        ReflectionTestUtils.setField(config, "timeoutConnectMs", 3000);
        ReflectionTestUtils.setField(config, "timeoutReadMs", 3000);

        assertNotNull(config.departamentosRestClient());
    }
}