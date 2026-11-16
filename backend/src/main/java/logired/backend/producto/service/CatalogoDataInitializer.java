package logired.backend.producto.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.producto.entity.Producto;
import logired.backend.producto.entity.Vendedor;
import logired.backend.producto.repository.ProductoRepository;
import logired.backend.producto.repository.VendedorRepository;

@Component
@Order(3)
public class CatalogoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogoDataInitializer.class);
    private static final Pattern MARCA_DE_PRUEBA = Pattern.compile(
            "(^|[^a-z0-9])(test(eo|ing)?|pruebas?|demos?|mocks?|fixtures?|dummy|fake|ejemplo|sample)([^a-z0-9]|$)");

    private static final List<ProductoInicial> CATALOGO_INICIAL = List.of(
            new ProductoInicial("ZAP-NIKE-001", "Zapatillas Nike Air Max SC",
                    "Zapatillas urbanas con amortiguación Air para uso diario.",
                    "Calzado", "129999.00", 8, "Tienda Urbana", "ventas@tiendaurbana.demo"),
            new ProductoInicial("ZAP-ADI-002", "Zapatillas Adidas Runfalcon 3.0",
                    "Calzado deportivo liviano para running y entrenamiento.",
                    "Calzado", "89999.00", 12, "Tienda Urbana", "ventas@tiendaurbana.demo"),
            new ProductoInicial("LIB-001", "El Principito - Antoine de Saint-Exupéry",
                    "Edición en español de tapa blanda, 96 páginas.",
                    "Libros", "12900.00", 20, "Librería Centro", "ventas@libreriacentro.demo"),
            new ProductoInicial("AUD-SONY-001", "Auriculares Sony WH-CH520",
                    "Auriculares inalámbricos Bluetooth con micrófono integrado.",
                    "Audio", "84999.00", 7, "Electrónica Munro", "ventas@electronicamunro.demo"),
            new ProductoInicial("MIC-LOGI-001", "Micrófono Logitech Blue Snowball iCE",
                    "Micrófono USB de condensador para videollamadas y streaming.",
                    "Audio", "109999.00", 5, "Electrónica Munro", "ventas@electronicamunro.demo"),
            new ProductoInicial("PC-LEN-001", "Notebook Lenovo IdeaPad Slim 3",
                    "Notebook de 15 pulgadas para estudio y trabajo.",
                    "Computación", "859999.00", 4, "Informática Palermo", "ventas@informaticapalermo.demo"),
            new ProductoInicial("MON-LG-001", "Monitor LG 24MP400-B de 24 pulgadas",
                    "Monitor Full HD IPS para oficina y entretenimiento.",
                    "Computación", "219999.00", 6, "Informática Palermo", "ventas@informaticapalermo.demo"),
            new ProductoInicial("MOU-LOGI-001", "Mouse Logitech M170 inalámbrico",
                    "Mouse inalámbrico compacto con receptor USB.",
                    "Accesorios", "18999.00", 15, "Informática Palermo", "ventas@informaticapalermo.demo"),
            new ProductoInicial("TAB-SAM-001", "Tablet Samsung Galaxy Tab A9",
                    "Tablet compacta con pantalla de 8,7 pulgadas.",
                    "Electrónica", "329999.00", 5, "Electrónica Munro", "ventas@electronicamunro.demo"));

    private final InventarioRepository inventarioRepository;
    private final ProductoRepository productoRepository;
    private final VendedorRepository vendedorRepository;
    private final DepositoRepository depositoRepository;

    public CatalogoDataInitializer(
            InventarioRepository inventarioRepository,
            ProductoRepository productoRepository,
            VendedorRepository vendedorRepository,
            DepositoRepository depositoRepository) {
        this.inventarioRepository = inventarioRepository;
        this.productoRepository = productoRepository;
        this.vendedorRepository = vendedorRepository;
        this.depositoRepository = depositoRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        limpiarStockDePrueba();
        Deposito deposito = depositoRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("No hay un depósito configurado para cargar el catálogo."));

        for (ProductoInicial inicial : CATALOGO_INICIAL) {
            if (productoRepository.findBySku(inicial.sku()).isPresent()) {
                continue;
            }
            Producto producto = productoRepository.save(crearProducto(inicial));
            List<ItemInventario> stockExistente = inventarioRepository.findBySku(inicial.sku());
            if (stockExistente.isEmpty()) {
                ItemInventario item = new ItemInventario(inicial.sku(), inicial.nombre(), deposito.getNombre(),
                        inicial.cantidad(), 0);
                item.setProducto(producto);
                inventarioRepository.save(item);
            } else {
                stockExistente.forEach(item -> item.setProducto(producto));
                inventarioRepository.saveAll(stockExistente);
            }
        }

        for (ItemInventario item : inventarioRepository.findAll()) {
            if (item.getProducto() != null || item.getSku() == null) {
                continue;
            }
            productoRepository.findBySku(item.getSku()).ifPresent(producto -> {
                item.setProducto(producto);
                inventarioRepository.save(item);
            });
        }
    }

    private void limpiarStockDePrueba() {
        List<ItemInventario> articulosDePrueba = inventarioRepository.findAll().stream()
                .filter(item -> contieneMarcaDePrueba(item.getSku()) || contieneMarcaDePrueba(item.getNombre()))
                .toList();
        if (!articulosDePrueba.isEmpty()) {
            inventarioRepository.deleteAll(articulosDePrueba);
            log.info("Se eliminaron {} registros de inventario identificados como datos de prueba.",
                    articulosDePrueba.size());
        }
    }

    private boolean contieneMarcaDePrueba(String texto) {
        if (texto == null) {
            return false;
        }
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return MARCA_DE_PRUEBA.matcher(normalizado).find();
    }

    private Producto crearProducto(ProductoInicial inicial) {
        return new Producto(inicial.sku(), inicial.nombre(), inicial.descripcion(), inicial.categoria(),
                new BigDecimal(inicial.precio()), vendedor(inicial));
    }

    private Vendedor vendedor(ProductoInicial inicial) {
        return vendedorRepository.findByNombre(inicial.vendedorNombre())
                .orElseGet(() -> vendedorRepository.save(new Vendedor(inicial.vendedorNombre(), inicial.vendedorEmail(),
                        "Comercio adherido al catálogo LogiRed.")));
    }

    private record ProductoInicial(
            String sku,
            String nombre,
            String descripcion,
            String categoria,
            String precio,
            int cantidad,
            String vendedorNombre,
            String vendedorEmail) {
    }
}
