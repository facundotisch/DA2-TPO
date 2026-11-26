package logired.backend.mensajeria;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import logired.backend.pedido.entity.Pedido;

/**
 * Patrón Transactional Outbox para los eventos de pedidos.
 *
 * 1. registrarPedidoCreado() guarda el evento en la tabla outbox, dentro de la transacción
 *    que crea el pedido (Propagation.MANDATORY: falla si se la llama fuera de una).
 * 2. publicarPendientes() corre cada pocos segundos, publica en el broker y marca ENVIADO.
 *    Si el broker no responde, el evento queda PENDIENTE y se reintenta en la próxima vuelta.
 *
 * La entrega es "al menos una vez": si el broker recibió el mensaje pero falló el UPDATE a
 * ENVIADO, se republica. Los consumidores son idempotentes por eventoId, así que se procesa una vez.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final JmsTemplate jmsTemplate;
    private final String topicEnviosCreados;

    public OutboxService(OutboxEventRepository repository,
                         ObjectMapper objectMapper,
                         JmsTemplate jmsTemplate,
                         @Value("${logired.jms.topic.envios-creados:VirtualTopic.logired.envios.creados}")
                         String topicEnviosCreados) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.jmsTemplate = jmsTemplate;
        this.topicEnviosCreados = topicEnviosCreados;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent registrarPedidoCreado(Pedido pedido) {
        PedidoCreadoEvent evento = new PedidoCreadoEvent(
                UUID.randomUUID().toString(),
                "PEDIDO_CREADO",
                pedido.getId(),
                pedido.getTotal(),
                pedido.getEstado());
        try {
            String payload = objectMapper.writeValueAsString(evento);
            return repository.save(new OutboxEvent(evento.getEventoId(), evento.getTipoEvento(), payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No fue posible serializar el evento del pedido.", exception);
        }
    }

    @Scheduled(initialDelayString = "${logired.outbox.demora-inicial-ms:5000}",
            fixedDelayString = "${logired.outbox.intervalo-ms:2000}")
    public void publicarPendientes() {
        List<OutboxEvent> pendientes = repository.findTop50ByEstadoOrderByIdAsc(OutboxEvent.PENDIENTE);
        for (OutboxEvent evento : pendientes) {
            if (!publicar(evento)) {
                // El broker no responde: se corta la vuelta y se reintenta en la próxima.
                return;
            }
        }
    }

    /**
     * Publica un evento y actualiza su estado. Devuelve false si el broker no lo aceptó.
     */
    public boolean publicar(OutboxEvent evento) {
        try {
            jmsTemplate.send(topicEnviosCreados, session -> {
                TextMessage message = session.createTextMessage(evento.getPayload());
                message.setStringProperty("tipoEvento", evento.getTipoEvento());
                message.setStringProperty("eventoId", evento.getEventoId());
                return message;
            });
        } catch (RuntimeException exception) {
            evento.registrarFallo(exception.getMessage());
            repository.save(evento);
            log.warn("[OUTBOX] No se pudo publicar el evento {} (intento {}). Queda pendiente: {}",
                    evento.getEventoId(), evento.getIntentos(), exception.getMessage());
            return false;
        }
        evento.marcarEnviado();
        repository.save(evento);
        log.info("[OUTBOX] Evento {} publicado en '{}'", evento.getEventoId(), topicEnviosCreados);
        return true;
    }

    public long pendientes() {
        return repository.countByEstado(OutboxEvent.PENDIENTE);
    }
}
