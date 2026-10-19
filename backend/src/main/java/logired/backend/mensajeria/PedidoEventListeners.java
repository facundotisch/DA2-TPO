package logired.backend.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class PedidoEventListeners {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventListeners.class);
    private final List<EventoLogDto> eventosRecientes = new CopyOnWriteArrayList<>();

    public List<EventoLogDto> getEventosRecientes() {
        return eventosRecientes;
    }

    // Suscriptor 1: Subsistema de Depósito / Inventario
    @JmsListener(destination = "${logired.jms.topic.envios-creados}", containerFactory = "jmsListenerContainerFactory")
    public void consumirParaDeposito(String payloadJson) {
        String logMsg = "Iniciando picking y packing del envío en depósito central";
        log.info("📦 [SUBSCRIPTOR DEPÓSITO] Evento recibido desde Tópico JMS -> {}", logMsg);
        registrar("Depósito / Inventario", "📦", logMsg, payloadJson);
    }

    // Suscriptor 2: Subsistema de Ruteo y Repartidores
    @JmsListener(destination = "${logired.jms.topic.envios-creados}", containerFactory = "jmsListenerContainerFactory")
    public void consumirParaRepartidores(String payloadJson) {
        String logMsg = "Calculando ruta óptima y asignando chofer de flota";
        log.info("🛵 [SUBSCRIPTOR REPARTIDORES] Evento recibido desde Tópico JMS -> {}", logMsg);
        registrar("Ruteo y Repartidores", "🛵", logMsg, payloadJson);
    }

    // Suscriptor 3: Subsistema de Notificaciones
    @JmsListener(destination = "${logired.jms.topic.envios-creados}", containerFactory = "jmsListenerContainerFactory")
    public void consumirParaNotificaciones(String payloadJson) {
        String logMsg = "Enviando confirmación por WhatsApp y correo electrónico al cliente";
        log.info("📲 [SUBSCRIPTOR NOTIFICACIONES] Evento recibido desde Tópico JMS -> {}", logMsg);
        registrar("Notificaciones B2C", "📲", logMsg, payloadJson);
    }

    private void registrar(String suscriptor, String icono, String accion, String payload) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        eventosRecientes.add(0, new EventoLogDto(suscriptor, icono, accion, timestamp, payload));
        if (eventosRecientes.size() > 50) {
            eventosRecientes.remove(eventosRecientes.size() - 1);
        }
    }
}