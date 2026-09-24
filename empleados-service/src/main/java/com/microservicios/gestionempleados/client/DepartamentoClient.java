package com.microservicios.gestionempleados.client;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class DepartamentoClient {

    private final RestClient restClient;

    public DepartamentoClient(RestClient departamentosRestClient) {
        this.restClient = departamentosRestClient;
    }

    /**
     * Consulta si un departamento existe, con reintentos y espera creciente.
     *
     * A diferencia del Reto 2, ya NO absorbe el fallo final devolviendo
     * INDETERMINADO — lo relanza, para que la capa de Circuit Breaker
     * (DepartamentoValidador) se entere del fallo y decida.
     */
    @Retryable(
            retryFor = DepartamentoServiceUnavailableException.class,
            maxAttempts = 4,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public DepartamentoValidacionResultado consultarExistenciaConReintentos(String departamentoId) {
        try {
            restClient.get()
                    .uri("/departamentos/{id}", departamentoId)
                    .retrieve()
                    .toBodilessEntity();
            return DepartamentoValidacionResultado.EXISTE;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return DepartamentoValidacionResultado.NO_EXISTE;
            }
            throw new DepartamentoServiceUnavailableException(
                    "departamentos-service respondió con error: " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            throw new DepartamentoServiceUnavailableException(
                    "No se pudo contactar a departamentos-service", ex);
        }
    }

    /**
     * Se ejecuta cuando se agotan los 4 intentos. Ya no absorbe el error:
     * lo relanza para que el Circuit Breaker externo cuente este fallo.
     */
    @Recover
    public DepartamentoValidacionResultado recuperarTrasFallosAgotados(
            DepartamentoServiceUnavailableException ex,
            String departamentoId
    ) {
        throw ex;
    }
}