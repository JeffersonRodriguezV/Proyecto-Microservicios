package com.microservicios.authservice.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservicios.authservice.service.CicloVidaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Recibe los mensajes de la cola, valida el envelope del Catálogo y delega en CicloVidaService.
 * Se lee el cuerpo "a mano" porque los productores (Node, Go...) no siempre envían content-type JSON.
 * Un mensaje malformado se descarta con un log; reintentarlo no lo arreglaría.
 */
@Component
public class EventoConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventoConsumer.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final CicloVidaService cicloVida;

    public EventoConsumer(CicloVidaService cicloVida) {
        this.cicloVida = cicloVida;
    }

    @RabbitListener(queues = ConsumerConfig.COLA)
    @SuppressWarnings("unchecked")
    public void recibir(Message mensaje) {
        Map<String, Object> envelope;
        try {
            envelope = mapper.readValue(mensaje.getBody(), new TypeReference<Map<String, Object>>() { });
        } catch (IOException e) {
            log.error("Mensaje descartado: no es JSON válido ({})", e.getMessage());
            return;
        }

        Object id = envelope.get("id");
        Object type = envelope.get("type");
        Object data = envelope.get("data");
        if (!(id instanceof String) || !(type instanceof String) || !(data instanceof Map)) {
            log.error("Mensaje descartado: envelope incompleto (se requieren id, type y data)");
            return;
        }
        /**
         * Delegar en el servicio de ciclo de vida para procesar el evento según su tipo.
         * El servicio se encarga de la deduplicación y de la lógica específica de cada evento.
         */
        cicloVida.procesar((String) id, (String) type, (Map<String, Object>) data);
    }
}