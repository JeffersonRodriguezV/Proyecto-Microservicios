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
     * Consulta si un departamento existe en departamentos-service.
     *
     * Reintenta hasta 4 veces (intento inicial + 3 reintentos) con
     * espera creciente 1s -> 2s -> 4s, solo ante fallos de disponibilidad
     * (timeout, conexión rechazada, 5xx). Un 404 real NUNCA dispara
     * reintentos: es una respuesta definitiva, no un fallo transitorio.
     *
     * @throws DepartamentoServiceUnavailableException ante cualquier
     *         fallo de disponibilidad, ANTES de agotar los reintentos
     *         (Spring Retry la relanza en cada intento fallido).
     */
    @Retryable(
            retryFor = DepartamentoServiceUnavailableException.class,
            maxAttempts = 4,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public DepartamentoValidacionResultado consultarExistencia(String departamentoId) {
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
     * Se ejecuta automáticamente cuando consultarExistencia() agota
     * los 4 intentos sin éxito. Aquí se materializa la decisión del
     * equipo: en vez de propagar el error, se acepta el registro del
     * empleado como pendiente de validación (INDETERMINADO), en lugar
     * de rechazarlo.
     *
     * La firma debe calzar exactamente con el método @Retryable:
     * primer parámetro es la excepción, luego los mismos parámetros
     * del método original.
     */
    @Recover
    public DepartamentoValidacionResultado recuperarTrasFallosAgotados(
            DepartamentoServiceUnavailableException ex,
            String departamentoId
    ) {
        return DepartamentoValidacionResultado.INDETERMINADO;
    }
}