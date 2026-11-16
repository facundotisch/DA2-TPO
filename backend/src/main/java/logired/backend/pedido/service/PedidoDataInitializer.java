package logired.backend.pedido.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class PedidoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PedidoDataInitializer.class);

    private final PedidoService pedidoService;

    public PedidoDataInitializer(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @Override
    public void run(String... args) {
        int completados = pedidoService.completarPedidosSinItems();
        if (completados > 0) {
            log.info("[PEDIDOS] Se completaron {} pedidos anteriores sin productos, reservando stock del inventario.", completados);
        }
    }
}