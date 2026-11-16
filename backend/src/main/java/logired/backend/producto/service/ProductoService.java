package logired.backend.producto.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.inventario.dto.ProductoStockRequest;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.inventario.service.InventarioCatalogoService;
import logired.backend.producto.dto.ProductoCatalogoRequest;
import logired.backend.producto.dto.ProductoResponse;
import logired.backend.producto.dto.StockDepositoResponse;
import logired.backend.producto.dto.UbicacionResponse;
import logired.backend.producto.dto.VendedorResponse;
import logired.backend.producto.entity.Producto;
import logired.backend.producto.repository.ProductoRepository;

@Service
@Transactional(readOnly = true)
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final InventarioRepository inventarioRepository;
    private final DepositoRepository depositoRepository;
    private final InventarioCatalogoService inventarioCatalogoService;

    public ProductoService(
            ProductoRepository productoRepository,
            InventarioRepository inventarioRepository,
            DepositoRepository depositoRepository,
            InventarioCatalogoService inventarioCatalogoService) {
        this.productoRepository = productoRepository;
        this.inventarioRepository = inventarioRepository;
        this.depositoRepository = depositoRepository;
        this.inventarioCatalogoService = inventarioCatalogoService;
    }

    public List<ProductoResponse> obtenerTodos() {
        Deposito ubicacionPredeterminada = obtenerDepositoPredeterminado();
        return productoRepository.findAll().stream()
                .map(producto -> convertir(producto, ubicacionPredeterminada))
                .filter(producto -> producto.stockDisponible() > 0)
                .toList();
    }

    public Optional<ProductoResponse> obtenerPorId(Long id) {
        Deposito ubicacionPredeterminada = obtenerDepositoPredeterminado();
        return productoRepository.findById(id).map(producto -> convertir(producto, ubicacionPredeterminada));
    }

    @Transactional
    public ProductoResponse crear(ProductoCatalogoRequest request) {
        validar(request);
        if (valor(request.stockBloqueado()) != 0) {
            throw new IllegalArgumentException("Un producto nuevo no puede tener stock reservado.");
        }
        String sku = request.sku().trim();
        if (productoRepository.findBySku(sku).isPresent()) {
            throw new IllegalArgumentException("Ya existe un producto con ese SKU.");
        }
        Deposito deposito = obtenerDepositoPredeterminado();
        ItemInventario item = inventarioCatalogoService.crear(new ProductoStockRequest(
                sku,
                request.nombre(),
                request.descripcion(),
                request.categoria(),
                request.precio(),
                request.stockDisponible(),
                deposito.getNombre()));
        return obtenerPorId(item.getProducto().getId())
                .orElseThrow(() -> new IllegalStateException("No se pudo recuperar el producto creado."));
    }

    @Transactional
    public Optional<ProductoResponse> actualizar(Long id, ProductoCatalogoRequest request) {
        validar(request);
        Optional<Producto> productoOpt = productoRepository.findById(id);
        if (productoOpt.isEmpty()) {
            return Optional.empty();
        }

        Producto producto = productoOpt.get();
        if (!producto.getSku().equals(request.sku().trim())) {
            throw new IllegalArgumentException("No se puede cambiar el SKU de un producto existente.");
        }

        producto.setNombre(request.nombre().trim());
        producto.setDescripcion(texto(request.descripcion()));
        producto.setCategoria(texto(request.categoria()));
        producto.setPrecio(request.precio());
        productoRepository.save(producto);

        List<ItemInventario> existencias = inventarioRepository.findAllForUpdateBySku(producto.getSku());
        if (existencias.isEmpty()) {
            Deposito deposito = obtenerDepositoPredeterminado();
            inventarioCatalogoService.crear(new ProductoStockRequest(
                    producto.getSku(),
                    producto.getNombre(),
                    producto.getDescripcion(),
                    producto.getCategoria(),
                    producto.getPrecio(),
                    request.stockDisponible(),
                    deposito.getNombre()));
        } else {
            existencias.forEach(item -> {
                item.setNombre(producto.getNombre());
                item.setProducto(producto);
            });
            ajustarStockDisponible(existencias, request.stockDisponible());
            inventarioRepository.saveAll(existencias);
        }

        return obtenerPorId(id);
    }

    @Transactional
    public boolean eliminar(Long id) {
        Optional<Producto> producto = productoRepository.findById(id);
        if (producto.isEmpty()) {
            return false;
        }

        List<ItemInventario> existencias =
                inventarioRepository.findAllForUpdateBySku(producto.get().getSku());
        if (existencias.stream().anyMatch(item -> valor(item.getStockBloqueado()) > 0)) {
            throw new IllegalStateException(
                    "No se puede eliminar un producto con unidades reservadas en pedidos activos.");
        }
        inventarioRepository.deleteAll(existencias);
        return true;
    }

    private void ajustarStockDisponible(List<ItemInventario> existencias, int cantidadDeseada) {
        int cantidadActual = existencias.stream().mapToInt(item -> valor(item.getStockDisponible())).sum();
        if (cantidadDeseada < cantidadActual) {
            int reducir = cantidadActual - cantidadDeseada;
            for (ItemInventario item : existencias) {
                int disponible = valor(item.getStockDisponible());
                int reduccion = Math.min(disponible, reducir);
                item.setStockDisponible(disponible - reduccion);
                reducir -= reduccion;
                if (reducir == 0) {
                    break;
                }
            }
        } else if (cantidadDeseada > cantidadActual) {
            String depositoPredeterminado = obtenerDepositoPredeterminado().getNombre();
            ItemInventario destino = existencias.stream()
                    .filter(item -> depositoPredeterminado.equals(item.getDeposito()))
                    .findFirst()
                    .orElse(existencias.get(0));
            destino.setStockDisponible(valor(destino.getStockDisponible()) + cantidadDeseada - cantidadActual);
        }
    }

    private Deposito obtenerDepositoPredeterminado() {
        return depositoRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("No hay un deposito predeterminado configurado"));
    }

    private ProductoResponse convertir(Producto producto, Deposito ubicacionPredeterminada) {
        List<StockDepositoResponse> stockPorDeposito = inventarioRepository.findBySku(producto.getSku()).stream()
                .map(item -> new StockDepositoResponse(
                        item.getDeposito(),
                        valor(item.getStockDisponible()),
                        valor(item.getStockBloqueado())))
                .toList();
        int disponible = stockPorDeposito.stream().mapToInt(StockDepositoResponse::disponible).sum();
        int bloqueado = stockPorDeposito.stream().mapToInt(StockDepositoResponse::bloqueado).sum();
        VendedorResponse vendedor = new VendedorResponse(
                producto.getVendedor().getId(),
                producto.getVendedor().getNombre(),
                producto.getVendedor().getEmail(),
                producto.getVendedor().getDescripcion());

        return new ProductoResponse(
                producto.getId(),
                producto.getSku(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getCategoria(),
                producto.getPrecio(),
                "ARS",
                vendedor,
                disponible,
                bloqueado,
                stockPorDeposito,
                new UbicacionResponse(
                    ubicacionPredeterminada.getId(),
                    ubicacionPredeterminada.getNombre(),
                    ubicacionPredeterminada.getDireccion(),
                    ubicacionPredeterminada.getLatitud(),
                    ubicacionPredeterminada.getLongitud(),
                    ubicacionPredeterminada.getOpenStreetMapUrl()));
    }

    private int valor(Integer stock) {
        return stock == null ? 0 : stock;
    }

    private void validar(ProductoCatalogoRequest request) {
        if (request == null || request.sku() == null || request.sku().isBlank()
                || request.nombre() == null || request.nombre().isBlank()
                || request.precio() == null || request.stockDisponible() == null) {
            throw new IllegalArgumentException("SKU, nombre, precio y stock disponible son obligatorios.");
        }
        if (request.precio().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor que cero.");
        }
        if (request.stockDisponible() < 0 || valor(request.stockBloqueado()) < 0) {
            throw new IllegalArgumentException("Las cantidades de stock no pueden ser negativas.");
        }
        if (request.moneda() != null && !request.moneda().isBlank()
                && !"ARS".equalsIgnoreCase(request.moneda().trim())) {
            throw new IllegalArgumentException("El catálogo admite precios solamente en ARS.");
        }
    }

    private String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}