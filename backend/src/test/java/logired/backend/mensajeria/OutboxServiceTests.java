package logired.backend.mensajeria;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;

import logired.backend.pedido.entity.Pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxServiceTests {

    private static final String TOPICO = "VirtualTopic.logired.envios.creados";

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final JmsTemplate jmsTemplate = mock(JmsTemplate.class);
    private final OutboxService outbox = new OutboxService(repository, new ObjectMapper(), jmsTemplate, TOPICO);

    @Test
    void elEventoNoIncluyeDatosPersonalesDelCliente() {
        Pedido pedido = new Pedido("Pedido de Ana Gómez | Entrega: Calle Falsa 123", "CREADO", 250.0);
        pedido.setClienteNombre("Ana Gómez");
        pedido.setDireccionEntrega("Calle Falsa 123");
        when(repository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutboxEvent evento = outbox.registrarPedidoCreado(pedido);

        assertEquals(OutboxEvent.PENDIENTE, evento.getEstado());
        assertFalse(evento.getPayload().contains("Ana"));
        assertFalse(evento.getPayload().contains("Calle Falsa"));
        assertTrue(evento.getPayload().contains("\"tipoEvento\":\"PEDIDO_CREADO\""));
    }

    @Test
    void conElBrokerCaidoElEventoQuedaPendienteYSeReintentaDespues() {
        OutboxEvent evento = new OutboxEvent("evt-1", "PEDIDO_CREADO", "{\"eventoId\":\"evt-1\"}");
        OutboxEvent siguiente = new OutboxEvent("evt-2", "PEDIDO_CREADO", "{\"eventoId\":\"evt-2\"}");
        when(repository.findTop50ByEstadoOrderByIdAsc(OutboxEvent.PENDIENTE))
                .thenReturn(List.of(evento, siguiente));
        doThrow(new UncategorizedJmsException("Connection refused"))
                .when(jmsTemplate).send(eq(TOPICO), any(MessageCreator.class));

        outbox.publicarPendientes();

        assertEquals(OutboxEvent.PENDIENTE, evento.getEstado());
        assertEquals(1, evento.getIntentos());
        assertTrue(evento.getUltimoError().contains("Connection refused"));
        // Con el broker caído no se sigue intentando el resto de la tanda.
        verify(jmsTemplate, times(1)).send(anyString(), any(MessageCreator.class));

        // El broker vuelve: la siguiente vuelta publica ambos y los marca como enviados.
        doNothing().when(jmsTemplate).send(eq(TOPICO), any(MessageCreator.class));
        outbox.publicarPendientes();

        assertEquals(OutboxEvent.ENVIADO, evento.getEstado());
        assertEquals(OutboxEvent.ENVIADO, siguiente.getEstado());
        assertNull(evento.getUltimoError());
    }
}
