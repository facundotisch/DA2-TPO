package logired.backend.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Procesa un evento una sola vez por consumidor.
 *
 * Está en un bean aparte para que @Transactional funcione: antes el método vivía en
 * PedidoEventListeners y se invocaba desde la misma clase (auto-invocación), así que
 * el proxy de Spring nunca abría la transacción.
 *
 * Orden: primero se inserta (eventoId, consumidor) y después se ejecuta la acción, todo en
 * la misma transacción. Si la acción falla, se revierte el registro y el broker reintenta.
 * Si dos entregas del mismo evento llegan a la vez, la restricción única de la tabla hace
 * fallar a la segunda; en el reintento ya figura como procesado y se descarta.
 */
@Component
public class ProcesadorEventosIdempotente {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorEventosIdempotente.class);

    private final ObjectMapper objectMapper;
    private final ProcessedMessageRepository processedMessageRepository;

    public ProcesadorEventosIdempotente(ObjectMapper objectMapper,
                                        ProcessedMessageRepository processedMessageRepository) {
        this.objectMapper = objectMapper;
        this.processedMessageRepository = processedMessageRepository;
    }

    /**
     * @return true si el evento se procesó, false si ya había sido procesado por ese consumidor.
     * @throws IllegalArgumentException si el payload es inválido; el mensaje se reintenta y,
     *                                  agotados los reintentos, el broker lo manda a la DLQ.
     */
    @Transactional
    public boolean procesar(String payloadJson, String consumerName, Runnable accion) {
        PedidoCreadoEvent evento = leer(payloadJson);
        if (processedMessageRepository
                .findByEventoIdAndConsumerName(evento.getEventoId(), consumerName)
                .isPresent()) {
            log.info("[IDEMPOTENCIA] Evento {} ya procesado por {}", evento.getEventoId(), consumerName);
            return false;
        }
        processedMessageRepository.saveAndFlush(new ProcessedMessage(evento.getEventoId(), consumerName));
        accion.run();
        return true;
    }

    private PedidoCreadoEvent leer(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("Payload JMS vacío.");
        }
        try {
            PedidoCreadoEvent evento = objectMapper.readValue(payloadJson, PedidoCreadoEvent.class);
            if (evento.getEventoId() == null || evento.getEventoId().isBlank()) {
                throw new IllegalArgumentException("El evento no contiene eventoId.");
            }
            return evento;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Payload JMS inválido.", exception);
        }
    }
}
