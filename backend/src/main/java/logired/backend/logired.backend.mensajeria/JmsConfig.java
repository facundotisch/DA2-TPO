package logired.backend.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;

import java.net.Socket;

@Configuration
@EnableJms
public class JmsConfig {

    private static final Logger log = LoggerFactory.getLogger(JmsConfig.class);
    private BrokerService embeddedBroker;

    @Bean
    public BrokerService brokerService() {
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
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setPubSubDomain(true); // Fuerza envío a Tópico (Pub/Sub)
        return template;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPubSubDomain(true); // Fuerza escucha en Tópico (Pub/Sub)
        return factory;
    }
}