package com.microservicios.gestionempleados.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** Verifica el envelope del Catálogo de Eventos y que un fallo del broker no rompa la operación. */
@ExtendWith(MockitoExtension.class)
class EventoPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private EventoPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new EventoPublisher(rabbitTemplate, "ecosistema.eventos");
    }

    @Test
    @DisplayName("publica en el exchange con routing key = type y envelope completo")
    @SuppressWarnings("unchecked")
    void envelopeCompleto() {
        Map<String, Object> data = Map.of("empleadoId", "1");

        publisher.publicar("empleado.creado", data);

        ArgumentCaptor<Object> cuerpo = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq("ecosistema.eventos"), eq("empleado.creado"), cuerpo.capture());
        Map<String, Object> envelope = (Map<String, Object>) cuerpo.getValue();

        assertNotNull(envelope.get("id"));
        assertEquals("empleado.creado", envelope.get("type"));
        assertEquals(1, envelope.get("version"));
        assertEquals("empleados-service", envelope.get("producer"));
        assertTrue(((String) envelope.get("occurredAt")).endsWith("Z"));
        assertEquals(data, envelope.get("data"));
    }

    @Test
    @DisplayName("si el broker falla, NO propaga la excepción (lo ya persistido no se revierte)")
    void fallaDelBrokerNoSePropaga() {
        doThrow(new RuntimeException("broker caído"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertDoesNotThrow(() -> publisher.publicar("empleado.creado", Map.of()));
    }

    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}