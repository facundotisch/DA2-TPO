package logired.backend.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class PedidoEventListeners {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventListeners.class);

    // Suscriptor 1: Subsistema de Depósito / Inventario
    @JmsListener(destination = "${logired.jms.topic.envios-creados}", containerFactory = "jmsListenerContainerFactory")
    public void consumirParaDeposito(String payloadJson) {
        log.info("📦 [SUBSCRIPTOR DEPÓSITO] Evento recibido desde Tópico JMS -> Iniciando picking y packing: {}", payloadJson);
    }

    // Suscriptor 2: Subsistema de Ruteo y Repartidores
    @JmsListener(destination = "${logired.jms.topic.envios-creados}", containerFactory = "jmsListenerContainerFactory")
    public void consumirParaRepartidores(String payloadJson) {
        log.info("🛵 [SUBSCRIPTOR REPARTIDORES] Evento recibido desde Tópico JMS -> Calculando ruta óptima y asignando chofer: {}", payloadJson);
    }

    // Suscriptor 3: Subsistema de Notificaciones
    @JmsListener(destination = "${logired.jms.topic.envios-creados}", containerFactory = "jmsListenerContainerFactory")
    public void consumirParaNotificaciones(String payloadJson) {
        log.info("📲 [SUBSCRIPTOR NOTIFICACIONES] Evento recibido desde Tópico JMS -> Enviando WhatsApp/Email de confirmación al cliente: {}", payloadJson);
    }
}