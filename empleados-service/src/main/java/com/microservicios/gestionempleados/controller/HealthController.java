package com.microservicios.gestionempleados.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check común a todos los microservicios del ecosistema:
 * {"status":"UP","service":"empleados-service","checks":{"database":"UP","messageBroker":"UP"}}.
 * Responde 200 si todo está UP y 503 si alguna dependencia está DOWN.
 *
 * Es independiente de /actuator/health (que sigue disponible para el healthcheck de Docker).
 */
@RestController
@Tag(name = "Health")
public class HealthController {

    private static final int TIMEOUT_VALIDACION_SEGUNDOS = 2;

    private final DataSource dataSource;
    private final ConnectionFactory connectionFactory;
    private final String servicio;

    public HealthController(DataSource dataSource,
                            ConnectionFactory connectionFactory,
                            @Value("${spring.application.name:empleados-service}") String servicio) {
        this.dataSource = dataSource;
        this.connectionFactory = connectionFactory;
        this.servicio = servicio;
    }

    @GetMapping("/health")
    @Operation(summary = "Estado del servicio y de sus dependencias (base de datos, broker)",
            description = "200 si todo está UP; 503 si la base de datos o el broker están DOWN.")
    public ResponseEntity<Map<String, Object>> health() {
        boolean baseDatosOk = baseDatosDisponible();
        boolean brokerOk = brokerDisponible();
        boolean arriba = baseDatosOk && brokerOk;

        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("database", estado(baseDatosOk));
        checks.put("messageBroker", estado(brokerOk));

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", estado(arriba));
        cuerpo.put("service", servicio);
        cuerpo.put("checks", checks);

        return ResponseEntity.status(arriba ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(cuerpo);
    }

    private boolean baseDatosDisponible() {
        try (Connection conexion = dataSource.getConnection()) {
            return conexion.isValid(TIMEOUT_VALIDACION_SEGUNDOS);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean brokerDisponible() {
        try {
            return connectionFactory.createConnection().isOpen();
        } catch (Exception e) {
            return false;
        }
    }

    private static String estado(boolean ok) {
        return ok ? "UP" : "DOWN";
    }
}
