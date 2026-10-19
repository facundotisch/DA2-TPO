package logired.backend.pedido.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.TextMessage;
import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.mensajeria.PedidoCreadoEvent;
import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final DepositoRepository depositoRepository;
    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${logired.jms.topic.envios-creados:logired.envios.creados}")
    private String topicEnviosCreados;

    public PedidoService(PedidoRepository pedidoRepository,
                         DepositoRepository depositoRepository,
                         @Autowired(required = false) JmsTemplate jmsTemplate,
                         @Autowired(required = false) ObjectMapper objectMapper) {
        this.pedidoRepository = pedidoRepository;
        this.depositoRepository = depositoRepository;
        this.jmsTemplate = jmsTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Pedido> obtenerTodos() {
        return pedidoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Pedido> obtenerPorId(Long id) {
        return pedidoRepository.findById(id);
    }

    @Transactional
    public Pedido crear(Pedido pedido) {
        // 1. Persistencia sincrónica ACID en PostgreSQL
        pedido.setEstado("CREADO");
        if (pedido.getDeposito() == null && depositoRepository != null) {
            depositoRepository.findFirstByOrderByIdAsc().ifPresent(pedido::setDeposito);
        }
        Pedido pedidoGuardado = pedidoRepository.save(pedido);

        // 2. Publicación asincrónica en Tópico JMS (Fire-and-Forget)
        publicarEventoPedidoCreado(pedidoGuardado);

        return pedidoGuardado;
    }

    private void publicarEventoPedidoCreado(Pedido pedido) {
        if (jmsTemplate == null || objectMapper == null) {
            return;
        }
        try {
            PedidoCreadoEvent evento = new PedidoCreadoEvent(
                    "evt-" + UUID.randomUUID().toString().substring(0, 8),
                    "PEDIDO_CREADO",
                    pedido.getId(),
                    pedido.getDescripcion(),
                    pedido.getTotal(),
                    pedido.getEstado()
            );

            String jsonPayload = objectMapper.writeValueAsString(evento);

            jmsTemplate.send(topicEnviosCreados, session -> {
                TextMessage message = session.createTextMessage(jsonPayload);
                message.setStringProperty("tipoEvento", "PEDIDO_CREADO");
                return message;
            });

            log.info("🚀 [PRODUCTOR JMS] Evento publicado en tópico '{}': Pedido #{}", topicEnviosCreados, pedido.getId());
        } catch (Exception e) {
            log.error("⚠️ Error al publicar evento en el broker JMS para el pedido #{}: {}", pedido.getId(), e.getMessage());
        }
    }

    public Optional<Pedido> actualizar(Long id, Pedido datos) {
        return pedidoRepository.findById(id).map(pedido -> {
            pedido.setDescripcion(datos.getDescripcion());
            pedido.setEstado(datos.getEstado());
            pedido.setTotal(datos.getTotal());
            return pedidoRepository.save(pedido);
        });
    }

    public boolean eliminar(Long id) {
        if (!pedidoRepository.existsById(id)) return false;
        pedidoRepository.deleteById(id);
        return true;
    }
}