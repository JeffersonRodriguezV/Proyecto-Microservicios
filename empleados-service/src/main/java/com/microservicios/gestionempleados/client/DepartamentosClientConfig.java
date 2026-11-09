package com.microservicios.gestionempleados.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class DepartamentosClientConfig {

    @Value("${departamentos.service.url}")
    private String departamentosServiceUrl;

    @Value("${departamentos.service.timeout-connect-ms}")
    private int timeoutConnectMs;

    @Value("${departamentos.service.timeout-read-ms}")
    private int timeoutReadMs;

    @Bean
    // Se recibe el RestClient.Builder autoconfigurado (no RestClient.builder()): trae el interceptor de observación
    // que propaga `traceparent` hacia departamentos-service y mide las llamadas salientes.
    public RestClient departamentosRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutConnectMs);
        requestFactory.setReadTimeout(timeoutReadMs);

        return builder
                .baseUrl(departamentosServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }
}