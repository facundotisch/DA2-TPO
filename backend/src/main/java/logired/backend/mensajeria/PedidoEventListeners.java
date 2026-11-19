package logired.backend.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class PedidoEventListeners {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventListeners.class);
    private final List<EventoLogDto> eventosRecientes = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;
    private final ProcessedMessageRepository processedMessageRepository;

    public PedidoEventListeners(ObjectMapper objectMapper,
                                ProcessedMessageRepository processedMessageRepository) {
        this.objectMapper = objectMapper;
        this.processedMessageRepository = processedMessageRepository;
    }

    @JmsListener(destination = "${logired.jms.queue.pedidos-creados:logired.pedidos.creados}",
            containerFactory = "queueJmsListenerContainerFactory")
    public void consumirPedidoDesdeCola(String payloadJson) {
        procesarIdempotente(payloadJson, "cola-pedidos", () -> {
            String logMsg = "Pedido recibido por integración punto a punto";
            log.info("[CONSUMIDOR COLA] Evento recibido desde Cola JMS -> {}", logMsg);
            registrar("Integración legada / Cola", "📥", logMsg, payloadJson);
        });
    }

    public List<EventoLogDto> getEventosRecientes() {
        return eventosRecientes;
    }

    // Suscriptor 1: Subsistema de Depósito / Inventario
    @JmsListener(destination = "${logired.jms.topic.envios-creados}", containerFactory = "jmsListenerContainerFactory")
    public void consumirParaDeposito(String payloadJson) {
        procesarIdempotente(payloadJson, "deposito-inventario", () -> {
            String logMsg = "Iniciando picking y packing del envío en depósito central";
            log.info("📦 [SUBSCRIPTOR DEPÓSITO] Evento recibido desde Tópico JMS -> {}", logMsg);
            registrar("Depósito / Inventario", "📦", logMsg, payloadJson);
        });
    }

    // Suscriptor 2: Subsistema de Ruteo y Repartidores
    @JmsListener(destination = "${logired.jms.topic.envios-creados}", containerFactory = "jmsListenerContainerFactory")
    public void consumirParaRepartidores(String payloadJson) {
        procesarIdempotente(payloadJson, "ruteo-repartidores", () -> {
            String logMsg = "Calculando ruta óptima y asignando chofer de flota";
            log.info("🛵 [SUBSCRIPTOR REPARTIDORES] Evento recibido desde Tópico JMS -> {}", logMsg);
            registrar("Ruteo y Repartidores", "🛵", logMsg, payloadJson);
        });
    }

    // Suscriptor 3: Subsistema de Notificaciones
    @JmsListener(destination = "${logired.jms.topic.envios-creados}", containerFactory = "jmsListenerContainerFactory")
    public void consumirParaNotificaciones(String payloadJson) {
        procesarIdempotente(payloadJson, "notificaciones", () -> {
            String logMsg = "Enviando confirmación por WhatsApp y correo electrónico al cliente";
            log.info("📲 [SUBSCRIPTOR NOTIFICACIONES] Evento recibido desde Tópico JMS -> {}", logMsg);
            registrar("Notificaciones B2C", "📲", logMsg, payloadJson);
        });
    }

    @Transactional
    protected void procesarIdempotente(String payloadJson, String consumerName, Runnable accion) {
        try {
            PedidoCreadoEvent evento = objectMapper.readValue(payloadJson, PedidoCreadoEvent.class);
            if (evento.getEventoId() == null || evento.getEventoId().isBlank()) {
                throw new IllegalArgumentException("El evento no contiene eventoId.");
            }
            if (processedMessageRepository
                    .findByEventoIdAndConsumerName(evento.getEventoId(), consumerName)
                    .isPresent()) {
                log.info("[IDEMPOTENCIA] Evento {} ya procesado por {}", evento.getEventoId(), consumerName);
                return;
            }
            accion.run();
            processedMessageRepository.saveAndFlush(
                    new ProcessedMessage(evento.getEventoId(), consumerName));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("Payload JMS inválido.", exception);
        }
    }

    private void registrar(String suscriptor, String icono, String accion, String payload) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        eventosRecientes.add(0, new EventoLogDto(suscriptor, icono, accion, timestamp, payload));
        if (eventosRecientes.size() > 50) {
            eventosRecientes.remove(eventosRecientes.size() - 1);
        }
    }
}