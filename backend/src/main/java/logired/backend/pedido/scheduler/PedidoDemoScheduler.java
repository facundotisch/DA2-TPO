package logired.backend.pedido.scheduler;

import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.pedido.dto.ItemPedidoRequest;
import logired.backend.pedido.dto.PedidoCreacionRequest;
import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.repository.PedidoRepository;
import logired.backend.pedido.service.PedidoService;

/**
 * Genera pedidos de ejemplo cada 2 minutos. Solo se activa con el perfil "demo"
 * (SPRING_PROFILES_ACTIVE=demo): en tests, en la prueba en vivo y en cualquier otro
 * entorno no crea pedidos que nadie pidió ni vuelve no deterministas los tests.
 */
@Component
@Profile("demo")
public class PedidoDemoScheduler {

    private static final Logger log = LoggerFactory.getLogger(PedidoDemoScheduler.class);
    private static final int MAX_PEDIDOS_AUTOMATICOS = 10;
    private static final Random RANDOM = new Random();

    private static final List<String> USUARIOS = List.of(
            "Usuario 1",
            "Usuario 2",
            "Usuario 3",
            "Usuario 4",
            "Usuario 5",
            "Usuario 6",
            "Usuario 7",
            "Usuario 8",
            "Usuario 9",
            "Usuario 10"
    );

    private static final List<String> DIRECCIONES = List.of(
            "Av. Corrientes 1234, CABA",
            "Calle Florida 456, CABA",
            "Rivadavia 789, CABA",
            "Sarmiento 1520, CABA",
            "Av. 9 de Julio 1000, CABA",
            "Reconquista 555, CABA",
            "Urquiza 234, CABA",
            "Avenida Santa Fe 2000, CABA",
            "Pueyrredón 315, CABA",
            "Lima 1220, CABA"
    );

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final DepositoRepository depositoRepository;
    private final InventarioRepository inventarioRepository;

    public PedidoDemoScheduler(PedidoRepository pedidoRepository,
                              PedidoService pedidoService,
                              DepositoRepository depositoRepository,
                              InventarioRepository inventarioRepository) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
        this.depositoRepository = depositoRepository;
        this.inventarioRepository = inventarioRepository;
    }

    @Scheduled(initialDelay = 20000, fixedDelay = 120000)
    public void generarPedidoAutomaticoSiHaceFalta() {
        long cantidadActual = pedidoRepository.count();

        if (cantidadActual >= MAX_PEDIDOS_AUTOMATICOS) {
            log.info("[AUTO-DEMO] Ya existen {} pedidos activos; no se generan más hasta que haya espacio.", cantidadActual);
            return;
        }

        List<ItemInventario> productosConStock = inventarioRepository.findAll().stream()
            .filter(item -> item.getProducto() != null && item.getStockDisponible() != null && item.getStockDisponible() > 0)
                .toList();

        if (productosConStock.isEmpty()) {
            log.warn("[AUTO-DEMO] No hay productos con stock disponible para generar un pedido automático.");
            return;
        }

        ItemInventario itemSeleccionado = productosConStock.get(RANDOM.nextInt(productosConStock.size()));
        String usuario = USUARIOS.get(RANDOM.nextInt(USUARIOS.size()));
        String direccion = DIRECCIONES.get(RANDOM.nextInt(DIRECCIONES.size()));
        Deposito deposito = depositoRepository.findByNombre(itemSeleccionado.getDeposito()).orElseGet(() -> depositoRepository.findAll().stream()
            .filter(d -> d.getNombre().equals(itemSeleccionado.getDeposito()))
            .findFirst()
            .orElse(null));

        PedidoCreacionRequest request = new PedidoCreacionRequest();
        request.setClienteNombre(usuario);
        request.setDireccionEntrega(direccion);
        if (deposito != null) {
            request.setDepositoId(deposito.getId());
        }
        ItemPedidoRequest linea = new ItemPedidoRequest();
        linea.setProductoId(itemSeleccionado.getProducto().getId());
        linea.setCantidad(1);
        request.setItems(List.of(linea));

        try {
            Pedido creado = pedidoService.crearDesdeCatalogo(request);
            log.info("[AUTO-DEMO] Pedido generado automáticamente: #{} | SKU: {} | Usuario: {} | Dirección: {}",
                    creado.getId(), itemSeleccionado.getSku(), usuario, direccion);
        } catch (Exception e) {
            log.warn("[AUTO-DEMO] No se pudo generar el pedido automático para SKU {}: {}",
                    itemSeleccionado.getSku(), e.getMessage());
        }
    }
}
