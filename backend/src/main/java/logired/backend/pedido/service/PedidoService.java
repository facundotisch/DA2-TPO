package logired.backend.pedido.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.TextMessage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
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
import java.util.concurrent.ThreadLocalRandom;

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
        PedidoCreacionRequest request = new PedidoCreacionRequest();
        request.setClienteNombre(pedido.getClienteNombre());
        request.setDireccionEntrega(pedido.getDireccionEntrega());
        request.setDescripcion(pedido.getDescripcion());
        if (pedido.getDeposito() != null) {
            request.setDepositoId(pedido.getDeposito().getId());
        }
        if (pedido.getDescripcion() != null) {
            Matcher matcher = SKU_PATTERN.matcher(pedido.getDescripcion());
            if (matcher.find()) {
                Producto producto = productoRepository.findBySku(matcher.group(1))
                        .orElseThrow(() -> new IllegalArgumentException(
                                "El SKU indicado no existe en el catálogo."));
                ItemPedidoRequest linea = new ItemPedidoRequest();
                linea.setProductoId(producto.getId());
                linea.setCantidad(1);
                request.setItems(List.of(linea));
            }
        }
        return crearDesdeCatalogo(request);
    }

    @Transactional
    public Pedido crearDesdeCatalogo(PedidoCreacionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud del pedido es obligatoria.");
        }

        Deposito depositoSolicitado = obtenerDepositoSolicitado(request.getDepositoId());
        if (request.getItems() == null || request.getItems().isEmpty()) {
            request.setItems(seleccionarItemsAleatorios(depositoSolicitado));
        }

        List<ItemReservado> reservas = seleccionarReservas(request, depositoSolicitado);
        Pedido pedido = new Pedido();
        pedido.setEstado("CREADO");
        pedido.setClienteNombre(request.getClienteNombre());
        pedido.setDireccionEntrega(request.getDireccionEntrega());
        pedido.setDeposito(reservas.get(0).deposito());
        pedido.setDescripcion(valorTexto(request.getDescripcion(),
                "Pedido de " + valorTexto(request.getClienteNombre(), "Cliente")
                        + " | Entrega: " + valorTexto(request.getDireccionEntrega(), "Dirección pendiente")));
        pedido.setTotal(reservarItemsEnPedido(pedido, reservas).doubleValue());

        Pedido pedidoGuardado = pedidoRepository.save(pedido);
        publicarEventoPedidoCreado(pedidoGuardado);
        return pedidoGuardado;
    }

    @Transactional
    public int completarPedidosSinItems() {
        List<Pedido> pedidosPendientes = pedidoRepository.buscarPedidosSinItems();
        int completados = 0;

        for (Pedido pedido : pedidosPendientes) {
            PedidoCreacionRequest request = new PedidoCreacionRequest();
            request.setClienteNombre(pedido.getClienteNombre());
            request.setDireccionEntrega(pedido.getDireccionEntrega());
            request.setDescripcion(pedido.getDescripcion());
            if (pedido.getDeposito() != null) {
                request.setDepositoId(pedido.getDeposito().getId());
            }

            Deposito depositoSolicitado = obtenerDepositoSolicitado(request.getDepositoId());
            try {
                request.setItems(seleccionarItemsAleatorios(depositoSolicitado));
            } catch (IllegalStateException noHayStockEnDeposito) {
                request.setDepositoId(null);
                depositoSolicitado = null;
                request.setItems(seleccionarItemsAleatorios(null));
            }
            List<ItemReservado> reservas = seleccionarReservas(request, depositoSolicitado);
            if (pedido.getDescripcion() == null || pedido.getDescripcion().isBlank()) {
                pedido.setDescripcion("Pedido completado automáticamente con productos del inventario.");
            }
            pedido.setDeposito(reservas.get(0).deposito());
            pedido.setTotal(reservarItemsEnPedido(pedido, reservas).doubleValue());
            pedidoRepository.save(pedido);
            completados++;
        }

        return completados;
    }

    private Deposito obtenerDepositoSolicitado(Long depositoId) {
        if (depositoId == null) {
            return null;
        }
        return depositoRepository.findById(depositoId)
                .orElseThrow(() -> new IllegalArgumentException("El depósito solicitado no existe."));
    }

    private List<ItemReservado> seleccionarReservas(PedidoCreacionRequest request, Deposito depositoSolicitado) {
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

        return reservaSeleccionada;
    }

    private BigDecimal reservarItemsEnPedido(Pedido pedido, List<ItemReservado> reservas) {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemReservado reserva : reservas) {
            ItemInventario stock = reserva.inventario();
            stock.setStockDisponible(stock.getStockDisponible() - reserva.cantidad());
            stock.setStockBloqueado(valorStock(stock.getStockBloqueado()) + reserva.cantidad());
            inventarioRepository.save(stock);

            PedidoItem pedidoItem = new PedidoItem(reserva.producto(), reserva.deposito(), reserva.cantidad(), reserva.producto().getPrecio());
            pedido.agregarItem(pedidoItem);
            total = total.add(pedidoItem.getSubtotal());
        }
        return total;
    }

    private List<ItemPedidoRequest> seleccionarItemsAleatorios(Deposito depositoSolicitado) {
        Map<Long, Producto> productosConStock = new LinkedHashMap<>();
        inventarioRepository.findAll().stream()
                .filter(item -> item.getProducto() != null
                        && item.getProducto().getId() != null
                        && valorStock(item.getStockDisponible()) > 0
                        && item.getDeposito() != null
                        && (depositoSolicitado == null
                                || depositoSolicitado.getNombre().equals(item.getDeposito())))
                .forEach(item -> productosConStock.putIfAbsent(item.getProducto().getId(), item.getProducto()));

        List<Producto> candidatos = new ArrayList<>(productosConStock.values());
        if (candidatos.isEmpty()) {
            throw new IllegalStateException("No hay productos del inventario con stock disponible para el pedido.");
        }

        Collections.shuffle(candidatos);
        int cantidadProductos = ThreadLocalRandom.current().nextInt(1, Math.min(2, candidatos.size()) + 1);
        return candidatos.subList(0, cantidadProductos).stream()
                .map(producto -> {
                    ItemPedidoRequest linea = new ItemPedidoRequest();
                    linea.setProductoId(producto.getId());
                    linea.setCantidad(1);
                    return linea;
                })
                .toList();
    }

    private record ItemReservado(Producto producto, ItemInventario inventario, Deposito deposito, int cantidad) {
    }

    private int valorStock(Integer stock) {
        return stock == null ? 0 : stock;
    }

    private String valorTexto(String valor, String predeterminado) {
        return valor == null || valor.isBlank() ? predeterminado : valor.trim();
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
        if (pedido.getItems() == null || pedido.getItems().isEmpty()) {
            liberarStockDesdeDescripcion(pedido);
        }
        pedidoRepository.deleteById(id);
        return true;
    }

    @Transactional
public Pedido cambiarEstado(Long id, String accion) {
    Pedido pedido = pedidoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));

    switch (accion.toUpperCase()) {
        case "ASIGNAR":
            pedido.avanzarAAsignado();
            break;
        case "EN_RUTA":
            pedido.avanzarAEnRuta();
            break;
        case "ENTREGAR":
            pedido.avanzarAEntregado();
            break;
        case "FALLAR":
            pedido.reportarFalla();
            break;
        default:
            throw new IllegalArgumentException("Acción no reconocida: " + accion);
    }

    return pedidoRepository.save(pedido);
}
}