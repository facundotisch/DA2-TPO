package logired.backend.producto.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.producto.entity.Producto;
import logired.backend.producto.entity.Vendedor;
import logired.backend.producto.repository.ProductoRepository;
import logired.backend.producto.repository.VendedorRepository;

@Component
@Order(3)
public class CatalogoDataInitializer implements CommandLineRunner {

    private final InventarioRepository inventarioRepository;
    private final ProductoRepository productoRepository;
    private final VendedorRepository vendedorRepository;

    public CatalogoDataInitializer(
            InventarioRepository inventarioRepository,
            ProductoRepository productoRepository,
            VendedorRepository vendedorRepository) {
        this.inventarioRepository = inventarioRepository;
        this.productoRepository = productoRepository;
        this.vendedorRepository = vendedorRepository;
    }

    @Override
    public void run(String... args) {
        List<ItemInventario> inventario = inventarioRepository.findAll();
        for (ItemInventario item : inventario) {
            if (item.getSku() == null || item.getNombre() == null) {
                continue;
            }

            Producto producto = productoRepository.findBySku(item.getSku())
                    .orElseGet(() -> crearProducto(item));
            if (item.getProducto() == null || !item.getProducto().getId().equals(producto.getId())) {
                item.setProducto(producto);
                inventarioRepository.save(item);
            }
        }
    }

    private Producto crearProducto(ItemInventario item) {
        String sku = item.getSku();
        String vendedorNombre = sku.startsWith("IND-") ? "Tienda Urbana"
                : sku.startsWith("ELEC-") ? "Tecnología Munro" : "LogiRed Demo";
        String email = sku.startsWith("IND-") ? "ventas@tiendaurbana.demo"
                : sku.startsWith("ELEC-") ? "ventas@tecnologiamunro.demo" : "demos@logired.demo";
        String categoria = sku.startsWith("IND-") ? "Indumentaria"
                : sku.startsWith("ELEC-") ? "Electrónica" : "Productos de demostración";
        String descripcion = switch (sku) {
            case "IND-001" -> "Calzado urbano con amortiguación para running cotidiano.";
            case "IND-002" -> "Campera térmica impermeable para uso urbano y actividades al aire libre.";
            case "ELEC-010" -> "Auriculares inalámbricos Bluetooth con estuche de carga.";
            default -> item.getNombre() + " del catálogo de demostración LogiRed.";
        };

        Vendedor vendedor = vendedorRepository.findByNombre(vendedorNombre)
                .orElseGet(() -> vendedorRepository.save(new Vendedor(vendedorNombre, email,
                        "Vendedor de " + categoria.toLowerCase() + " en LogiRed.")));
        return productoRepository.save(new Producto(sku, item.getNombre(), descripcion, categoria, precioPara(sku), vendedor));
    }

    private BigDecimal precioPara(String sku) {
        return switch (sku) {
            case "IND-001" -> new BigDecimal("59990.00");
            case "IND-002" -> new BigDecimal("89990.00");
            case "ELEC-010" -> new BigDecimal("42990.00");
            default -> new BigDecimal("15990.00");
        };
    }
}