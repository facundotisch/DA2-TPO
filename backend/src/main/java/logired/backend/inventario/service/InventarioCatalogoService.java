package logired.backend.inventario.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.inventario.dto.ProductoStockRequest;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.producto.entity.Producto;
import logired.backend.producto.entity.Vendedor;
import logired.backend.producto.repository.ProductoRepository;
import logired.backend.producto.repository.VendedorRepository;

@Service
public class InventarioCatalogoService {

    private final InventarioRepository inventarioRepository;
    private final ProductoRepository productoRepository;
    private final VendedorRepository vendedorRepository;
    private final DepositoRepository depositoRepository;

    public InventarioCatalogoService(
            InventarioRepository inventarioRepository,
            ProductoRepository productoRepository,
            VendedorRepository vendedorRepository,
            DepositoRepository depositoRepository) {
        this.inventarioRepository = inventarioRepository;
        this.productoRepository = productoRepository;
        this.vendedorRepository = vendedorRepository;
        this.depositoRepository = depositoRepository;
    }

    @Transactional
    public ItemInventario crear(ProductoStockRequest request) {
        validar(request);
        String sku = request.sku().trim();
        String deposito = request.deposito().trim();
        if (depositoRepository.findByNombre(deposito).isEmpty()) {
            throw new IllegalArgumentException("El depósito indicado no existe.");
        }
        if (inventarioRepository.findBySkuAndDeposito(sku, deposito).isPresent()) {
            throw new IllegalArgumentException("Ya existe stock para ese SKU en el depósito indicado.");
        }

        Producto producto = productoRepository.findBySku(sku).orElseGet(() -> crearProducto(sku, request));
        producto = actualizarProducto(producto, request);

        ItemInventario item = new ItemInventario(
                sku, request.nombre().trim(), deposito, request.cantidad(), 0);
        item.setProducto(producto);
        return inventarioRepository.save(item);
    }

    @Transactional
    public Optional<ItemInventario> actualizar(Long id, ProductoStockRequest request) {
        validar(request);
        return inventarioRepository.findById(id).map(item -> {
            String deposito = request.deposito().trim();
            if (depositoRepository.findByNombre(deposito).isEmpty()) {
                throw new IllegalArgumentException("El depósito indicado no existe.");
            }
            if (!item.getSku().equals(request.sku().trim())) {
                throw new IllegalArgumentException("No se puede cambiar el SKU de un producto existente.");
            }
            if (request.cantidad() < valor(item.getStockBloqueado())) {
                throw new IllegalArgumentException("La cantidad no puede ser menor al stock reservado.");
            }
            Optional<ItemInventario> duplicado = inventarioRepository.findBySkuAndDeposito(item.getSku(), deposito);
            if (duplicado.isPresent() && !duplicado.get().getId().equals(id)) {
                throw new IllegalArgumentException("Ya existe stock para ese SKU en el depósito indicado.");
            }

            Producto producto = item.getProducto() != null
                    ? item.getProducto()
                    : productoRepository.findBySku(item.getSku())
                            .orElseThrow(() -> new IllegalStateException("El producto asociado al stock no existe."));
            producto = actualizarProducto(producto, request);
            item.setProducto(producto);
            item.setNombre(request.nombre().trim());
            item.setDeposito(deposito);
            item.setStockDisponible(request.cantidad() - valor(item.getStockBloqueado()));
            return inventarioRepository.save(item);
        });
    }

    @Transactional
    public boolean eliminar(Long id) {
        Optional<ItemInventario> item = inventarioRepository.findById(id);
        if (item.isEmpty()) {
            return false;
        }
        if (valor(item.get().getStockBloqueado()) > 0) {
            throw new IllegalStateException("No se puede eliminar un producto con unidades reservadas.");
        }
        inventarioRepository.delete(item.get());
        return true;
    }

    private Producto crearProducto(String sku, ProductoStockRequest request) {
        Vendedor vendedor = vendedorRepository.findByNombre("Comercios LogiRed")
                .orElseGet(() -> vendedorRepository.save(new Vendedor(
                        "Comercios LogiRed",
                        "catalogo@logired.demo",
                        "Catálogo de comercios adheridos a LogiRed.")));
        return new Producto(sku, request.nombre().trim(), texto(request.descripcion()), texto(request.categoria()),
                request.precio(), vendedor);
    }

    private Producto actualizarProducto(Producto producto, ProductoStockRequest request) {
        producto.setNombre(request.nombre().trim());
        producto.setDescripcion(texto(request.descripcion()));
        producto.setCategoria(texto(request.categoria()));
        producto.setPrecio(request.precio());
        Producto actualizado = productoRepository.save(producto);
        List<ItemInventario> existencias = inventarioRepository.findBySku(producto.getSku());
        existencias.forEach(item -> item.setNombre(actualizado.getNombre()));
        if (!existencias.isEmpty()) {
            inventarioRepository.saveAll(existencias);
        }
        return actualizado;
    }

    private void validar(ProductoStockRequest request) {
        if (request == null || vacio(request.sku()) || vacio(request.nombre())
                || vacio(request.deposito()) || request.precio() == null
                || request.cantidad() == null) {
            throw new IllegalArgumentException(
                    "SKU, nombre, precio, cantidad y depósito son obligatorios.");
        }
        if (request.precio().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor que cero.");
        }
        if (request.cantidad() < 0) {
            throw new IllegalArgumentException("La cantidad no puede ser negativa.");
        }
    }

    private boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private int valor(Integer cantidad) {
        return cantidad == null ? 0 : cantidad;
    }
}
