package logired.backend.pedido.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.TextMessage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.mensajeria.PedidoCreadoEvent;
import logired.backend.pedido.dto.ItemPedidoRequest;
import logired.backend.pedido.dto.PedidoCreacionRequest;
import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.entity.PedidoItem;
import logired.backend.pedido.repository.PedidoRepository;
import logired.backend.producto.entity.Producto;
import logired.backend.producto.repository.ProductoRepository;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);
    private static final Pattern SKU_PATTERN = Pattern.compile("SKU:([A-Za-z0-9-]+)");

    private final PedidoRepository pedidoRepository;
    private final DepositoRepository depositoRepository;
    private final InventarioRepository inventarioRepository;
    private final ProductoRepository productoRepository;
    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${logired.jms.topic.envios-creados:logired.envios.creados}")
    private String topicEnviosCreados;

    public PedidoService(PedidoRepository pedidoRepository,
                         DepositoRepository depositoRepository,
                         InventarioRepository inventarioRepository,
                         ProductoRepository productoRepository,
                         @Autowired(required = false) JmsTemplate jmsTemplate,
                         @Autowired(required = false) ObjectMapper objectMapper) {
        this.pedidoRepository = pedidoRepository;
        this.depositoRepository = depositoRepository;
        this.inventarioRepository = inventarioRepository;
        this.productoRepository = productoRepository;
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
        pedido.setEstado("CREADO");
        if (pedido.getDeposito() == null && depositoRepository != null) {
            depositoRepository.findFirstByOrderByIdAsc().ifPresent(pedido::setDeposito);
        }

        if (pedido.getDescripcion() != null && pedido.getDescripcion().contains("SKU:")) {
            reservarStockDesdeDescripcion(pedido);
        }

        Pedido pedidoGuardado = pedidoRepository.save(pedido);
        publicarEventoPedidoCreado(pedidoGuardado);
        return pedidoGuardado;
    }

    @Transactional
    public Pedido crearDesdeCatalogo(PedidoCreacionRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("El pedido debe incluir al menos un producto.");
        }

        Map<Long, Integer> cantidadesPorProducto = new LinkedHashMap<>();
        for (ItemPedidoRequest item : request.getItems()) {
            if (item.getProductoId() == null || item.getCantidad() == null || item.getCantidad() <= 0) {
                throw new IllegalArgumentException("Cada línea debe indicar productoId y una cantidad mayor que cero.");
            }
            cantidadesPorProducto.merge(item.getProductoId(), item.getCantidad(), Integer::sum);
        }

        Map<Long, Producto> productos = productoRepository.findAllById(cantidadesPorProducto.keySet()).stream()
                .collect(Collectors.toMap(Producto::getId, producto -> producto));
        if (productos.size() != cantidadesPorProducto.size()) {
            throw new IllegalArgumentException("Uno o más productos no existen en el catálogo.");
        }

        Deposito depositoSolicitado = null;
        if (request.getDepositoId() != null) {
            depositoSolicitado = depositoRepository.findById(request.getDepositoId())
                    .orElseThrow(() -> new IllegalArgumentException("El depósito solicitado no existe."));
        }

        List<ItemReservado> reservaSeleccionada = new ArrayList<>();
        for (Map.Entry<Long, Integer> linea : cantidadesPorProducto.entrySet().stream()
            .sorted(Map.Entry.comparingByKey()).toList()) {
            Producto producto = productos.get(linea.getKey());
            List<ItemInventario> opcionesStock = depositoSolicitado == null
                    ? inventarioRepository.findBySku(producto.getSku()).stream()
                            .filter(item -> item.getDeposito() != null)
                            .sorted((first, second) -> Integer.compare(
                                    valorStock(second.getStockDisponible()), valorStock(first.getStockDisponible())))
                            .toList()
                    : List.of(new ItemInventario(producto.getSku(), producto.getNombre(),
                            depositoSolicitado.getNombre(), 0, 0));

            ItemReservado seleccionado = null;
            for (ItemInventario opcion : opcionesStock) {
                String depositoNombre = depositoSolicitado == null ? opcion.getDeposito() : depositoSolicitado.getNombre();
                Optional<ItemInventario> stock = inventarioRepository.findBySkuAndDeposito(producto.getSku(), depositoNombre);
                Optional<Deposito> deposito = depositoRepository.findByNombre(depositoNombre);
                if (stock.isPresent() && deposito.isPresent() && stock.get().getStockDisponible() != null
                        && stock.get().getStockDisponible() >= linea.getValue()) {
                    seleccionado = new ItemReservado(producto, stock.get(), deposito.get(), linea.getValue());
                    break;
                }
            }

            if (seleccionado == null) {
                throw new IllegalStateException("No hay stock suficiente de " + producto.getNombre()
                        + " en un depósito habilitado para preparar el pedido.");
            }
            reservaSeleccionada.add(seleccionado);
        }

        Pedido pedido = new Pedido();
        pedido.setEstado("CREADO");
        pedido.setClienteNombre(request.getClienteNombre());
        pedido.setDireccionEntrega(request.getDireccionEntrega());
        pedido.setDeposito(reservaSeleccionada.get(0).deposito());
        BigDecimal total = BigDecimal.ZERO;

        for (ItemReservado reserva : reservaSeleccionada) {
            ItemInventario stock = reserva.inventario();
            stock.setStockDisponible(stock.getStockDisponible() - reserva.cantidad());
            stock.setStockBloqueado(valorStock(stock.getStockBloqueado()) + reserva.cantidad());
            inventarioRepository.save(stock);

            PedidoItem pedidoItem = new PedidoItem(reserva.producto(), reserva.deposito(), reserva.cantidad(), reserva.producto().getPrecio());
            pedido.agregarItem(pedidoItem);
            total = total.add(pedidoItem.getSubtotal());
        }

        pedido.setDescripcion("Pedido de " + valorTexto(request.getClienteNombre(), "Cliente")
                + " | Entrega: " + valorTexto(request.getDireccionEntrega(), "Dirección pendiente"));
        pedido.setTotal(total.doubleValue());

        Pedido pedidoGuardado = pedidoRepository.save(pedido);
        publicarEventoPedidoCreado(pedidoGuardado);
        return pedidoGuardado;
    }

    private record ItemReservado(Producto producto, ItemInventario inventario, Deposito deposito, int cantidad) {
    }

    private int valorStock(Integer stock) {
        return stock == null ? 0 : stock;
    }

    private String valorTexto(String valor, String predeterminado) {
        return valor == null || valor.isBlank() ? predeterminado : valor.trim();
    }

    private void reservarStockDesdeDescripcion(Pedido pedido) {
        if (inventarioRepository == null) {
            return;
        }

        Matcher matcher = SKU_PATTERN.matcher(pedido.getDescripcion());
        if (!matcher.find()) {
            return;
        }

        String sku = matcher.group(1);
        String depositoNombre = pedido.getDeposito() != null ? pedido.getDeposito().getNombre() : "Depósito Central Barracas";

        ItemInventario item = resolverItemInventarioPorSku(sku, depositoNombre).orElse(null);

        if (item == null) {
            throw new IllegalStateException("No existe stock para el SKU " + sku + " en el depósito configurado.");
        }

        if (item.getStockDisponible() == null || item.getStockDisponible() <= 0) {
            throw new IllegalStateException("Stock insuficiente para el SKU " + sku + ".");
        }

        item.setStockDisponible(item.getStockDisponible() - 1);
        item.setStockBloqueado(item.getStockBloqueado() == null ? 1 : item.getStockBloqueado() + 1);
        inventarioRepository.save(item);
        log.info("[AUTO-HOLD] SKU {} reservado en depósito {} para pedido nuevo.", sku, depositoNombre);
    }

    private Optional<ItemInventario> resolverItemInventarioPorSku(String sku, String depositoNombre) {
        return inventarioRepository.findBySkuAndDeposito(sku, depositoNombre)
            .or(() -> inventarioRepository.findAll().stream()
                .filter(item -> sku.equals(item.getSku()))
                .findFirst());
    }

    private void liberarStockDesdeDescripcion(Pedido pedido) {
        if (inventarioRepository == null || pedido.getDescripcion() == null) {
            return;
        }

        Matcher matcher = SKU_PATTERN.matcher(pedido.getDescripcion());
        if (!matcher.find()) {
            return;
        }

        String sku = matcher.group(1);
        String depositoNombre = pedido.getDeposito() != null ? pedido.getDeposito().getNombre() : "Depósito Central Barracas";

        resolverItemInventarioPorSku(sku, depositoNombre)
                .ifPresent(item -> {
                    int bloqueado = valorStock(item.getStockBloqueado());
                    if (bloqueado > 0) {
                        item.setStockBloqueado(Math.max(0, bloqueado - 1));
                        item.setStockDisponible(valorStock(item.getStockDisponible()) + 1);
                        inventarioRepository.save(item);
                    }
                });
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
            if (pedido.getItems() == null || pedido.getItems().isEmpty()) {
                pedido.setTotal(datos.getTotal());
            }
            return pedidoRepository.save(pedido);
        });
    }

    public boolean eliminar(Long id) {
        Optional<Pedido> pedidoOpt = pedidoRepository.findById(id);
        if (pedidoOpt.isEmpty()) return false;

        Pedido pedido = pedidoOpt.get();
        if (pedido.getItems() != null && !pedido.getItems().isEmpty()) {
            for (PedidoItem linea : pedido.getItems()) {
                Deposito depositoLinea = linea.getDeposito() != null ? linea.getDeposito() : pedido.getDeposito();
                if (depositoLinea == null) {
                    continue;
                }
                inventarioRepository.findBySkuAndDeposito(
                        linea.getProducto().getSku(), depositoLinea.getNombre()).ifPresent(item -> {
                            int bloqueado = valorStock(item.getStockBloqueado());
                            int cantidadLiberada = Math.min(bloqueado, linea.getCantidad());
                            if (cantidadLiberada > 0) {
                                item.setStockBloqueado(bloqueado - cantidadLiberada);
                                item.setStockDisponible(valorStock(item.getStockDisponible()) + cantidadLiberada);
                                inventarioRepository.save(item);
                            }
                        });
            }
        }
        liberarStockDesdeDescripcion(pedido);
        pedidoRepository.deleteById(id);
        return true;
    }
}