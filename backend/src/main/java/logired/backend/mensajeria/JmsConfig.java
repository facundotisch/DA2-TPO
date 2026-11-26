package logired.backend.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.context.annotation.Primary;

import java.net.Socket;

@Configuration
@EnableJms
public class JmsConfig {

    private static final Logger log = LoggerFactory.getLogger(JmsConfig.class);
    private BrokerService embeddedBroker;

    /**
     * Broker embebido solo para desarrollo local. En docker compose se desactiva con
     * LOGIRED_JMS_BROKER_EMBEBIDO=false y la app usa el contenedor de ActiveMQ, que es el
     * que se apaga y se vuelve a levantar en la prueba de resiliencia.
     */
    @Bean
    public BrokerService brokerService(
            @Value("${logired.jms.broker-embebido:true}") boolean brokerEmbebido) {
        if (!brokerEmbebido) {
            log.info("Broker embebido deshabilitado; se usa el broker externo configurado.");
            return null;
        }
        try {
            // Si ya hay un broker escuchando en 61616 (ej: contenedor Docker), no levantamos broker embebido
            try (Socket socket = new Socket("localhost", 61616)) {
                log.info("🔌 Broker ActiveMQ externo detectado en tcp://localhost:61616.");
                return null;
            } catch (Exception ignored) {
                // Puerto libre: procedemos a levantar el broker embebido
            }

            embeddedBroker = new BrokerService();
            embeddedBroker.setBrokerName("logired-broker");
            embeddedBroker.setPersistent(false);
            embeddedBroker.setUseJmx(false);
            embeddedBroker.addConnector("tcp://localhost:61616");
            embeddedBroker.start();
            log.info("🚀 Broker ActiveMQ Classic iniciado exitosamente en tcp://localhost:61616");
            return embeddedBroker;
        } catch (Exception e) {
            log.warn("No se pudo iniciar el broker ActiveMQ embebido: {}", e.getMessage());
            return null;
        }
    }

    @PreDestroy
    public void detenerBroker() {
        if (embeddedBroker != null) {
            try {
                embeddedBroker.stop();
                log.info("🛑 Broker ActiveMQ Classic detenido.");
            } catch (Exception ignored) {
            }
        }
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @Primary
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setPubSubDomain(true); // Fuerza envío a Tópico (Pub/Sub)
        template.setReceiveTimeout(5000);
        template.setDeliveryPersistent(true);
        template.setExplicitQosEnabled(true);
        template.setTimeToLive(0);
        return template;
    }

    @Bean(name = "queueJmsTemplate")
    public JmsTemplate queueJmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setPubSubDomain(false);
        template.setReceiveTimeout(5000);
        template.setDeliveryPersistent(true);
        template.setExplicitQosEnabled(true);
        template.setTimeToLive(0);
        return template;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPubSubDomain(true); // Fuerza escucha en Tópico (Pub/Sub)
        factory.setSessionTransacted(true);
        factory.setErrorHandler(error -> log.error("Error procesando mensaje JMS. Se aplica redelivery y DLQ.", error));
        return factory;
    }

    @Bean(name = "queueJmsListenerContainerFactory")
    public DefaultJmsListenerContainerFactory queueJmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPubSubDomain(false);
        factory.setSessionTransacted(true);
        factory.setErrorHandler(error -> log.error("Error procesando mensaje de cola. Se aplica redelivery y DLQ.", error));
        return factory;
    }
}