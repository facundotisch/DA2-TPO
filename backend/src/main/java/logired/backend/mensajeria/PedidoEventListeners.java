package logired.backend.mensajeria;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Suscriptores del evento PEDIDO_CREADO.
 *
 * Se usa un Virtual Topic de ActiveMQ: el productor publica en el tópico
 * "VirtualTopic.logired.envios.creados" y el broker copia cada mensaje a una cola
 * persistente por suscriptor ("Consumer.<nombre>.VirtualTopic..."). Así se mantiene el
 * modelo publicación/suscripción, pero si la aplicación o un consumidor están caídos los
 * mensajes esperan en su cola en vez de perderse, como pasaba con el tópico no durable.
 *
 * Si un mensaje falla, la sesión transaccional hace rollback y el broker lo reentrega
 * (política en spring.activemq.broker-url). Agotados los reintentos va a ActiveMQ.DLQ,
 * donde se puede inspeccionar y reprocesar desde la consola web de ActiveMQ.
 */
@Component
public class PedidoEventListeners {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventListeners.class);
    private static final String TOPICO = "${logired.jms.topic.envios-creados:VirtualTopic.logired.envios.creados}";

    private final List<EventoLogDto> eventosRecientes = new CopyOnWriteArrayList<>();
    private final ProcesadorEventosIdempotente procesador;

    public PedidoEventListeners(ProcesadorEventosIdempotente procesador) {
        this.procesador = procesador;
    }

    public List<EventoLogDto> getEventosRecientes() {
        return eventosRecientes;
    }

    @JmsListener(destination = "Consumer.integracion-legada." + TOPICO,
            containerFactory = "queueJmsListenerContainerFactory")
    public void consumirParaIntegracionLegada(String payloadJson) {
        procesar(payloadJson, "integracion-legada", "Integración legada", "📥",
                "Pedido recibido por integración punto a punto");
    }

    @JmsListener(destination = "Consumer.deposito." + TOPICO,
            containerFactory = "queueJmsListenerContainerFactory")
    public void consumirParaDeposito(String payloadJson) {
        procesar(payloadJson, "deposito-inventario", "Depósito / Inventario", "📦",
                "Iniciando picking y packing del envío en depósito central");
    }

    @JmsListener(destination = "Consumer.repartidores." + TOPICO,
            containerFactory = "queueJmsListenerContainerFactory")
    public void consumirParaRepartidores(String payloadJson) {
        procesar(payloadJson, "ruteo-repartidores", "Ruteo y Repartidores", "🛵",
                "Calculando ruta óptima y asignando chofer de flota");
    }

    @JmsListener(destination = "Consumer.notificaciones." + TOPICO,
            containerFactory = "queueJmsListenerContainerFactory")
    public void consumirParaNotificaciones(String payloadJson) {
        procesar(payloadJson, "notificaciones", "Notificaciones B2C", "📲",
                "Enviando confirmación por WhatsApp y correo electrónico al cliente");
    }

    private void procesar(String payloadJson, String consumidor, String suscriptor,
                          String icono, String accion) {
        procesador.procesar(payloadJson, consumidor, () -> {
            log.info("[{}] Evento recibido -> {}", consumidor, accion);
            registrar(suscriptor, icono, accion, payloadJson);
        });
    }

    private void registrar(String suscriptor, String icono, String accion, String payload) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        eventosRecientes.add(0, new EventoLogDto(suscriptor, icono, accion, timestamp, payload));
        if (eventosRecientes.size() > 50) {
            eventosRecientes.remove(eventosRecientes.size() - 1);
        }
    }
}
