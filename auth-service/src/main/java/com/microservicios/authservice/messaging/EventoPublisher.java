package com.microservicios.authservice.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publica eventos con el envelope del Catálogo: {id, type, version, occurredAt, producer, data}.
 * Si falla, se registra el error pero no se propaga: no debe revertir la operación ya guardada.
 */
@Component
public class EventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventoPublisher.class);
    private static final String PRODUCER = "auth-service";

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;

    public EventoPublisher(RabbitTemplate rabbitTemplate,
                           @Value("${eventos.exchange}") String exchangeName) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
    }

    public void publicar(String type, Map<String, Object> data) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("id", UUID.randomUUID().toString());
            envelope.put("type", type);
            envelope.put("version", 1);
            envelope.put("occurredAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
            envelope.put("producer", PRODUCER);
            envelope.put("data", data);

            rabbitTemplate.convertAndSend(exchangeName, type, envelope);
            log.info("Evento publicado: {}", type);
        } catch (Exception ex) {
            log.error("No se pudo publicar el evento '{}': {}", type, ex.getMessage());
        }
    }
}