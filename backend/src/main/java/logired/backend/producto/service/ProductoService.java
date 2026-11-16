package logired.backend.producto.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
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

    public ProductoService(
            ProductoRepository productoRepository,
            InventarioRepository inventarioRepository,
            DepositoRepository depositoRepository) {
        this.productoRepository = productoRepository;
        this.inventarioRepository = inventarioRepository;
        this.depositoRepository = depositoRepository;
    }

    public List<ProductoResponse> obtenerTodos() {
        Deposito ubicacionPredeterminada = obtenerDepositoPredeterminado();
        return productoRepository.findAll().stream()
                .map(producto -> convertir(producto, ubicacionPredeterminada))
                .toList();
    }

    public Optional<ProductoResponse> obtenerPorId(Long id) {
        Deposito ubicacionPredeterminada = obtenerDepositoPredeterminado();
        return productoRepository.findById(id).map(producto -> convertir(producto, ubicacionPredeterminada));
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
}