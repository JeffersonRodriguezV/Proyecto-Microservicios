package com.microservicios.authservice.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Reto 8: el healthcheck de Docker (/health) y Prometheus (/actuator/**) consultan el servicio cada pocos
 * segundos. Se excluyen de la observación (trazas y métricas HTTP) para no inundar Zipkin con ruido.
 */
@Configuration
public class ObservabilidadConfig {

    static boolean esRutaDeMonitoreo(String ruta) {
        return "/health".equals(ruta) || ruta.startsWith("/actuator");
    }

    @Bean
    public ObservationPredicate omitirRutasDeMonitoreo() {
        return (nombre, contexto) -> {
            if (contexto instanceof ServerRequestObservationContext servidor) {
                return !esRutaDeMonitoreo(servidor.getCarrier().getRequestURI());
            }
            return true;
        };
    }
}
