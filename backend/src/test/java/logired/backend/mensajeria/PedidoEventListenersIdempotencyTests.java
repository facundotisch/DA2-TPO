package logired.backend.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PedidoEventListenersIdempotencyTests {

    @Test
    void noReprocesaUnEventoYaConsumido() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessedMessageRepository repository = mock(ProcessedMessageRepository.class);
        when(repository.findByEventoIdAndConsumerName("evt-123", "deposito-inventario"))
                .thenReturn(Optional.of(new ProcessedMessage("evt-123", "deposito-inventario")));

        PedidoEventListeners listeners =
                new PedidoEventListeners(objectMapper, repository);
        AtomicInteger acciones = new AtomicInteger();

        listeners.procesarIdempotente(
                "{\"eventoId\":\"evt-123\",\"tipoEvento\":\"PEDIDO_CREADO\"}",
                "deposito-inventario",
                acciones::incrementAndGet);

        assertEquals(0, acciones.get());
        verify(repository, never()).saveAndFlush(any(ProcessedMessage.class));
    }

    @Test
    void registraUnEventoNuevoDespuesDeProcesarlo() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessedMessageRepository repository = mock(ProcessedMessageRepository.class);
        when(repository.findByEventoIdAndConsumerName("evt-456", "notificaciones"))
                .thenReturn(Optional.empty());

        PedidoEventListeners listeners =
                new PedidoEventListeners(objectMapper, repository);
        AtomicInteger acciones = new AtomicInteger();

        listeners.procesarIdempotente(
                "{\"eventoId\":\"evt-456\",\"tipoEvento\":\"PEDIDO_CREADO\"}",
                "notificaciones",
                acciones::incrementAndGet);

        assertEquals(1, acciones.get());
        verify(repository).saveAndFlush(any(ProcessedMessage.class));
    }
}
