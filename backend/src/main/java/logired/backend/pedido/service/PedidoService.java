package logired.backend.pedido.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.common.exception.RecursoNoEncontradoException;
import logired.backend.common.exception.StockInsuficienteException;
import logired.backend.mensajeria.OutboxService;
import logired.backend.pedido.dto.ItemPedidoRequest;
import logired.backend.pedido.dto.PedidoActualizacionRequest;
import logired.backend.pedido.dto.PedidoCreacionRequest;
import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.entity.PedidoItem;
import logired.backend.pedido.exception.TransicionInvalidaException;
import logired.backend.pedido.repository.PedidoRepository;
import logired.backend.producto.entity.Producto;
import logired.backend.producto.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PedidoService {

    private static final Pattern SKU_PATTERN = Pattern.compile("SKU:([A-Za-z0-9-]+)");

    private final PedidoRepository pedidoRepository;
    private final DepositoRepository depositoRepository;
    private final InventarioRepository inventarioRepository;
    private final ProductoRepository productoRepository;
    private final OutboxService outboxService;

    /**
     * outboxService puede ser null en tests unitarios que construyen el servicio a mano:
     * en ese caso el pedido se guarda sin registrar el evento.
     */
    public PedidoService(PedidoRepository pedidoRepository,
                         DepositoRepository depositoRepository,
                         InventarioRepository inventarioRepository,
                         ProductoRepository productoRepository,
                         OutboxService outboxService) {
        this.pedidoRepository = pedidoRepository;
        this.depositoRepository = depositoRepository;
        this.inventarioRepository = inventarioRepository;
        this.productoRepository = productoRepository;
        this.outboxService = outboxService;
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
        request.setLatitudEntrega(pedido.getLatitudEntrega());
        request.setLongitudEntrega(pedido.getLongitudEntrega());
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
        validarCoordenadasEntrega(request.getLatitudEntrega(), request.getLongitudEntrega());
        Double latitud = request.getLatitudEntrega();
        Double longitud = request.getLongitudEntrega();
        if (latitud == null && longitud == null && request.getDireccionEntrega() != null) {
            double[] coords = resolverCoordenadasPredefinidas(request.getDireccionEntrega());
            if (coords != null) {
                latitud = coords[0];
                longitud = coords[1];
            }
        }

        Deposito depositoSolicitado = obtenerDepositoSolicitado(request.getDepositoId());
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un producto para el pedido.");
        }

        List<ItemReservado> reservas = seleccionarReservas(request, depositoSolicitado);
        Pedido pedido = new Pedido(); // nace en CREADO por el patrón State
        pedido.setClienteNombre(request.getClienteNombre());
        pedido.setDireccionEntrega(request.getDireccionEntrega());
        pedido.setLatitudEntrega(latitud);
        pedido.setLongitudEntrega(longitud);
        pedido.setDeposito(reservas.get(0).deposito());
        pedido.setDescripcion(valorTexto(request.getDescripcion(),
                "Pedido de " + valorTexto(request.getClienteNombre(), "Cliente")
                        + " | Entrega: " + valorTexto(request.getDireccionEntrega(), "Dirección pendiente")));
        pedido.setTotal(reservarItemsEnPedido(pedido, reservas).doubleValue());

        Pedido pedidoGuardado = pedidoRepository.save(pedido);
        // El evento se guarda en la tabla outbox dentro de esta misma transacción:
        // si el pedido hace rollback, el evento también; si el broker está caído, queda pendiente.
        if (outboxService != null) {
            outboxService.registrarPedidoCreado(pedidoGuardado);
        }
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
            // Se bloquean (SELECT ... FOR UPDATE) todas las filas del SKU en la PRIMERA lectura.
            // Antes se leían sin lock y Hibernate devolvía esas instancias ya cargadas en las
            // lecturas con lock posteriores, así que el chequeo de stock usaba datos viejos.
            List<ItemInventario> opcionesStock = depositoSolicitado == null
                    ? inventarioRepository.findAllForUpdateBySku(producto.getSku()).stream()
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
                throw new StockInsuficienteException("No hay stock suficiente de " + producto.getNombre()
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

    private void validarCoordenadasEntrega(Double latitud, Double longitud) {
        if ((latitud == null) != (longitud == null)) {
            throw new IllegalArgumentException("La ubicación de entrega debe incluir latitud y longitud.");
        }
        if (latitud != null && (!Double.isFinite(latitud) || latitud < -90 || latitud > 90
                || !Double.isFinite(longitud) || longitud < -180 || longitud > 180)) {
            throw new IllegalArgumentException("Las coordenadas de entrega están fuera de rango.");
        }
    }

    public static double[] resolverCoordenadasPredefinidas(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            return null;
        }
        String d = direccion.toLowerCase();
        if (d.contains("lacroze") || d.contains("3496")) return new double[]{-34.5818, -58.4513};
        if (d.contains("elcano") || d.contains("3240")) return new double[]{-34.5779, -58.4589};
        if (d.contains("florida") || d.contains("570")) return new double[]{-34.6025, -58.3755};
        if (d.contains("corrientes 992") || (d.contains("corrientes") && d.contains("992"))) return new double[]{-34.6037, -58.3816};
        if (d.contains("corrientes 1650") || (d.contains("corrientes") && d.contains("1650"))) return new double[]{-34.6047, -58.3915};
        if (d.contains("cordoba") || d.contains("córdoba") || d.contains("1765")) return new double[]{-34.5982, -58.3937};
        if (d.contains("santa fe 2468") || (d.contains("santa fe") && (d.contains("recoleta") || d.contains("2468")))) return new double[]{-34.5947, -58.4018};
        if (d.contains("santa fe 4280") || (d.contains("santa fe") && (d.contains("palermo") || d.contains("4280")))) return new double[]{-34.5802, -58.4215};
        if (d.contains("moreau de justo") || d.contains("1190") || d.contains("puerto madero")) return new double[]{-34.6133, -58.3656};
        if (d.contains("cabildo") || d.contains("2254")) return new double[]{-34.5617, -58.4564};
        if (d.contains("iriarte") || d.contains("3500") || d.contains("barracas")) return new double[]{-34.6462, -58.3858};
        return null;
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

    @Transactional
    public Optional<Pedido> actualizarDesdeCatalogo(Long id, PedidoActualizacionRequest request) {
        return actualizarDesdeCatalogo(id, request, pedido -> { });
    }

    /**
     * Actualiza datos de entrega y líneas de un pedido.
     *
     * @param precondicion se evalúa con el pedido actual dentro de la transacción, antes de
     *                     modificar nada (la usa el controlador para validar If-Match).
     * @throws TransicionInvalidaException si el pedido ya no está en CREADO: un pedido asignado,
     *                                     en ruta o cerrado no puede cambiar sus líneas.
     */
    @Transactional
    public Optional<Pedido> actualizarDesdeCatalogo(Long id, PedidoActualizacionRequest request,
                                                    Consumer<Pedido> precondicion) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud del pedido es obligatoria.");
        }
        validarCoordenadasEntrega(request.getLatitudEntrega(), request.getLongitudEntrega());

        return pedidoRepository.findById(id).map(pedido -> {
            precondicion.accept(pedido);
            if (!"CREADO".equals(pedido.getEstado())) {
                throw new TransicionInvalidaException("Solo se puede editar un pedido en estado CREADO; este está "
                        + pedido.getEstado() + ".");
            }
            if (request.getClienteNombre() != null) {
                pedido.setClienteNombre(request.getClienteNombre());
            }
            if (request.getDireccionEntrega() != null) {
                boolean cambioDireccion = !Objects.equals(pedido.getDireccionEntrega(), request.getDireccionEntrega());
                pedido.setDireccionEntrega(request.getDireccionEntrega());
                if (cambioDireccion && request.getLatitudEntrega() == null) {
                    double[] coords = resolverCoordenadasPredefinidas(request.getDireccionEntrega());
                    if (coords != null) {
                        pedido.setLatitudEntrega(coords[0]);
                        pedido.setLongitudEntrega(coords[1]);
                    } else {
                        pedido.setLatitudEntrega(null);
                        pedido.setLongitudEntrega(null);
                    }
                }
            }
            if (request.getLatitudEntrega() != null) {
                pedido.setLatitudEntrega(request.getLatitudEntrega());
                pedido.setLongitudEntrega(request.getLongitudEntrega());
            }
            if (request.getDescripcion() != null) {
                pedido.setDescripcion(request.getDescripcion());
            }
            if (request.tieneItemsDeCatalogo()) {
                liberarReservasDelPedido(pedido);
                pedido.getItems().clear();
                Deposito depositoSolicitado = obtenerDepositoSolicitado(request.getDepositoId());
                List<ItemReservado> reservas = seleccionarReservas(request.comoSolicitudDeReserva(), depositoSolicitado);
                pedido.setDeposito(reservas.get(0).deposito());
                if (request.getDescripcion() == null) {
                    pedido.setDescripcion("Pedido de " + valorTexto(pedido.getClienteNombre(), "Cliente")
                            + " | Entrega: " + valorTexto(pedido.getDireccionEntrega(), "Dirección pendiente"));
                }
                pedido.setTotal(reservarItemsEnPedido(pedido, reservas).doubleValue());
            }

            return pedidoRepository.save(pedido);
        });
    }

    private void liberarReservasDelPedido(Pedido pedido) {
        if (pedido.getItems() == null) {
            return;
        }

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

    @Transactional
    public boolean eliminar(Long id) {
        Optional<Pedido> pedidoOpt = pedidoRepository.findById(id);
        if (pedidoOpt.isEmpty()) return false;

        Pedido pedido = pedidoOpt.get();
        if (pedido.getItems() != null && !pedido.getItems().isEmpty()) {
            liberarReservasDelPedido(pedido);
        }
        if (pedido.getItems() == null || pedido.getItems().isEmpty()) {
            liberarStockDesdeDescripcion(pedido);
        }
        pedidoRepository.deleteById(id);
        return true;
    }

    @Transactional
    public Pedido cambiarEstado(Long id, String accion) {
        return cambiarEstado(id, accion, pedido -> { });
    }

    /**
     * Aplica una transición del patrón State. Las transiciones inválidas lanzan
     * TransicionInvalidaException (HTTP 409) y un pedido inexistente da HTTP 404.
     */
    @Transactional
    public Pedido cambiarEstado(Long id, String accion, Consumer<Pedido> precondicion) {
        if (accion == null || accion.isBlank()) {
            throw new IllegalArgumentException("La acción es obligatoria.");
        }
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pedido " + id + " no existe."));
        precondicion.accept(pedido);

        switch (accion.trim().toUpperCase()) {
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
