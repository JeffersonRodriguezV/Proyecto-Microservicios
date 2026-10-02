package com.microservicios.gestionempleados.eventos;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara el exchange compartido por todo el ecosistema de eventos.
 * Es tipo "topic" porque la routing key es el propio nombre del
 * evento (ej. "empleado.creado"), que ya viene separado por puntos
 * en el catálogo.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${eventos.exchange}")
    private String exchangeName;

    @Bean
    public TopicExchange eventosExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    /**
     * Sin esto, Spring AMQP serializa los mensajes con serialización
     * nativa de Java (ilegible para servicios en otros lenguajes).
     * Con este converter, se serializan como JSON normal.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}