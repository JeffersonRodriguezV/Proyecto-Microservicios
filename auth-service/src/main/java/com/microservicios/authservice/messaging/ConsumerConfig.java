package com.microservicios.authservice.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Cola propia del auth-service enlazada al exchange compartido, solo con los eventos que consume.
 * Cola durable: si el servicio está caído, los eventos esperan en el broker.
 */
@Configuration
public class ConsumerConfig {

    public static final String COLA = "auth-service.eventos";
    /**
     * Lista de eventos que el auth-service consume del exchange compartido.
     * Se usa para crear los bindings de la cola propia.
     */
    private static final List<String> EVENTOS_CONSUMIDOS = List.of(
            "empleado.creado",
            "empleado.retirado",
            "vacaciones.iniciadas",
            "vacaciones.finalizadas");

    /**
     * Crea la cola propia del auth-service, durable, para recibir eventos del exchange compartido.
     * @return la cola de eventos del auth-service
     */
    @Bean
    public Queue colaEventosAuth() {
        return QueueBuilder.durable(COLA).build();
    }

    /**
     * Crea los bindings de la cola propia del auth-service con el exchange compartido, solo para los eventos que consume.
     * @param colaEventosAuth la cola propia del auth-service
     * @param eventosExchange el exchange compartido de eventos
     * @return los bindings de la cola propia del auth-service con el exchange compartido
     */
    @Bean
    public Declarables bindingsEventosAuth(Queue colaEventosAuth, TopicExchange eventosExchange) {
        List<Binding> bindings = EVENTOS_CONSUMIDOS.stream()
                .map(clave -> BindingBuilder.bind(colaEventosAuth).to(eventosExchange).with(clave))
                .toList();
        return new Declarables(bindings);
    }
}