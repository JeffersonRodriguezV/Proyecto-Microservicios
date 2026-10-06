package com.microservicios.authservice.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declara el exchange compartido del ecosistema y serializa los mensajes como JSON. */
@Configuration
public class RabbitMQConfig {

    @Value("${eventos.exchange}")
    private String exchangeName;

    @Bean
    public TopicExchange eventosExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}