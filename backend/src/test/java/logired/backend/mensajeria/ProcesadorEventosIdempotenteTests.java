package logired.backend.mensajeria;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcesadorEventosIdempotenteTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProcessedMessageRepository repository = mock(ProcessedMessageRepository.class);
    private final ProcesadorEventosIdempotente procesador =
            new ProcesadorEventosIdempotente(objectMapper, repository);

    @Test
    void noReprocesaUnEventoYaConsumido() {
        when(repository.findByEventoIdAndConsumerName("evt-123", "deposito-inventario"))
                .thenReturn(Optional.of(new ProcessedMessage("evt-123", "deposito-inventario")));
        AtomicInteger acciones = new AtomicInteger();

        boolean procesado = procesador.procesar(
                "{\"eventoId\":\"evt-123\",\"tipoEvento\":\"PEDIDO_CREADO\"}",
                "deposito-inventario",
                acciones::incrementAndGet);

        assertFalse(procesado);
        assertEquals(0, acciones.get());
        verify(repository, never()).saveAndFlush(any(ProcessedMessage.class));
    }

    @Test
    void registraElEventoAntesDeEjecutarLaAccion() {
        when(repository.findByEventoIdAndConsumerName("evt-456", "notificaciones"))
                .thenReturn(Optional.empty());
        Runnable accion = mock(Runnable.class);

        boolean procesado = procesador.procesar(
                "{\"eventoId\":\"evt-456\",\"tipoEvento\":\"PEDIDO_CREADO\"}",
                "notificaciones",
                accion);

        assertTrue(procesado);
        // Si la acción falla, la transacción revierte también el registro y el broker reintenta.
        InOrder orden = inOrder(repository, accion);
        orden.verify(repository).saveAndFlush(any(ProcessedMessage.class));
        orden.verify(accion).run();
    }

    @Test
    void aceptaMensajesViejosQueTodaviaTraenDescripcion() {
        when(repository.findByEventoIdAndConsumerName("evt-789", "deposito-inventario"))
                .thenReturn(Optional.empty());

        assertTrue(procesador.procesar(
                "{\"eventoId\":\"evt-789\",\"descripcion\":\"Pedido de Ana | Entrega: Calle 1\"}",
                "deposito-inventario",
                () -> { }));
    }

    @Test
    void payloadInvalidoLanzaExcepcionParaQueElBrokerReintenteYTermineEnDlq() {
        assertThrows(IllegalArgumentException.class,
                () -> procesador.procesar("esto no es json", "deposito-inventario", () -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> procesador.procesar("{\"tipoEvento\":\"PEDIDO_CREADO\"}", "deposito-inventario", () -> { }));
        verify(repository, never()).saveAndFlush(any(ProcessedMessage.class));
    }
}
