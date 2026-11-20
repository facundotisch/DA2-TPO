package logired.backend.producto.service;

import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.inventario.service.InventarioCatalogoService;
import logired.backend.producto.dto.ProductoCatalogoRequest;
import logired.backend.producto.entity.Producto;
import logired.backend.producto.entity.Vendedor;
import logired.backend.producto.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTests {

    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private InventarioRepository inventarioRepository;
    @Mock
    private DepositoRepository depositoRepository;
    @Mock
    private InventarioCatalogoService inventarioCatalogoService;

    private ProductoService service;

    @BeforeEach
    void setUp() {
        service = new ProductoService(
                productoRepository, inventarioRepository, depositoRepository, inventarioCatalogoService);
    }

    @Test
    void validaCamposPrecioStockYMoneda() {
        assertThrows(IllegalArgumentException.class, () -> service.crear(null));
        assertThrows(IllegalArgumentException.class, () -> service.crear(request(null, "Producto", 1, 0, "ARS")));
        assertThrows(IllegalArgumentException.class, () -> service.crear(request("SKU", " ", 1, 0, "ARS")));
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(new ProductoCatalogoRequest("SKU", "Producto", null, null, null, "ARS", 1, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(request("SKU", "Producto", 1, 0, "USD")));
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(new ProductoCatalogoRequest("SKU", "Producto", null, null,
                        BigDecimal.ZERO, "ARS", 1, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(request("SKU", "Producto", -1, 0, "ARS")));
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(request("SKU", "Producto", 1, -1, "ARS")));
    }

    @Test
    void crearRechazaStockBloqueadoSkuDuplicadoYDepositoAusente() {
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(request("SKU", "Producto", 1, 1, "ARS")));

        when(productoRepository.findBySku("SKU")).thenReturn(Optional.of(producto("SKU", 1L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(request("SKU", "Producto", 1, 0, "ARS")));

        when(productoRepository.findBySku("OTRO")).thenReturn(Optional.empty());
        when(depositoRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> service.crear(request("OTRO", "Producto", 1, 0, "ARS")));
    }

    @Test
    void actualizarDevuelveVacioParaIdDesconocidoYRechazaCambioDeSku() {
        when(productoRepository.findById(404L)).thenReturn(Optional.empty());
        assertTrue(service.actualizar(404L, request("SKU", "Producto", 1, 0, "ARS")).isEmpty());

        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto("SKU", 1L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.actualizar(1L, request("OTRO", "Producto", 1, 0, "ARS")));
    }

    @Test
    void aumentarStockPrefiereElDepositoPredeterminado() {
        Producto producto = producto("SKU", 1L);
        Deposito deposito = deposito("Munro");
        ItemInventario barracas = new ItemInventario("SKU", "Producto", "Barracas", 3, 0);
        ItemInventario munro = new ItemInventario("SKU", "Producto", "Munro", 2, 0);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(producto)).thenReturn(producto);
        when(inventarioRepository.findAllForUpdateBySku("SKU")).thenReturn(List.of(barracas, munro));
        when(depositoRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(deposito));
        when(inventarioRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventarioRepository.findBySku("SKU")).thenReturn(List.of(barracas, munro));

        var actualizado = service.actualizar(1L, request("SKU", "Actualizado", 8, 0, "ARS"));

        assertTrue(actualizado.isPresent());
        assertEquals(3, barracas.getStockDisponible());
        assertEquals(5, munro.getStockDisponible());
        assertEquals("Actualizado", barracas.getNombre());
    }

    @Test
    void reducirStockLoDistribuyeHastaAlcanzarElTotalDeseado() {
        Producto producto = producto("SKU", 1L);
        ItemInventario primero = new ItemInventario("SKU", "Producto", "Barracas", 3, 0);
        ItemInventario segundo = new ItemInventario("SKU", "Producto", "Munro", 2, 0);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(producto)).thenReturn(producto);
        when(inventarioRepository.findAllForUpdateBySku("SKU")).thenReturn(List.of(primero, segundo));
        when(inventarioRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(depositoRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(deposito("Central")));
        when(inventarioRepository.findBySku("SKU")).thenReturn(List.of(primero, segundo));

        service.actualizar(1L, request("SKU", "Producto", 3, 0, "ARS"));

        assertEquals(1, primero.getStockDisponible());
        assertEquals(2, segundo.getStockDisponible());
    }

    @Test
    void stockDeseadoIgualAlExistenteNoModificaLasUnidades() {
        Producto producto = producto("SKU", 1L);
        ItemInventario barracas = new ItemInventario("SKU", "Producto", "Barracas", 3, 0);
        ItemInventario munro = new ItemInventario("SKU", "Producto", "Munro", 2, 0);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(producto)).thenReturn(producto);
        when(inventarioRepository.findAllForUpdateBySku("SKU")).thenReturn(List.of(barracas, munro));
        when(inventarioRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(depositoRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(deposito("Central")));
        when(inventarioRepository.findBySku("SKU")).thenReturn(List.of(barracas, munro));

        assertTrue(service.actualizar(1L, request("SKU", "Producto", 5, 0, "ARS")).isPresent());

        assertEquals(3, barracas.getStockDisponible());
        assertEquals(2, munro.getStockDisponible());
    }

    @Test
    void obtenerTodosOmiteProductosSinStockDisponible() {
        Producto conStock = producto("SKU-1", 1L);
        Producto agotado = producto("SKU-2", 2L);
        when(depositoRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(deposito("Central")));
        when(productoRepository.findAll()).thenReturn(List.of(conStock, agotado));
        when(inventarioRepository.findBySku("SKU-1"))
                .thenReturn(List.of(new ItemInventario("SKU-1", "Disponible", "Central", 2, 0)));
        when(inventarioRepository.findBySku("SKU-2"))
                .thenReturn(List.of(new ItemInventario("SKU-2", "Agotado", "Central", 0, 3)));

        var productos = service.obtenerTodos();

        assertEquals(1, productos.size());
        assertEquals("SKU-1", productos.get(0).sku());
        assertEquals(2, productos.get(0).stockDisponible());
        assertEquals(0, productos.get(0).stockBloqueado());
    }

    @Test
    void actualizarCreaExistenciaSiNoHayFilasDeInventario() {
        Producto producto = producto("SKU", 1L);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(producto)).thenReturn(producto);
        when(inventarioRepository.findAllForUpdateBySku("SKU")).thenReturn(List.of());
        when(depositoRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(deposito("Central")));
        when(inventarioRepository.findBySku("SKU")).thenReturn(List.of());

        assertTrue(service.actualizar(1L, request("SKU", "Producto", 5, 0, "ARS")).isPresent());
        verify(inventarioCatalogoService).crear(any());
    }

    @Test
    void eliminarRechazaStockReservadoYEliminaExistenciasLibres() {
        Producto producto = producto("SKU", 1L);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(inventarioRepository.findAllForUpdateBySku("SKU"))
                .thenReturn(List.of(new ItemInventario("SKU", "Producto", "Central", 1, 2)));
        assertThrows(IllegalStateException.class, () -> service.eliminar(1L));

        when(productoRepository.findById(2L)).thenReturn(Optional.of(producto("SKU-2", 2L)));
        ItemInventario libre = new ItemInventario("SKU-2", "Producto", "Central", 1, 0);
        when(inventarioRepository.findAllForUpdateBySku("SKU-2")).thenReturn(List.of(libre));
        assertTrue(service.eliminar(2L));
        verify(inventarioRepository).deleteAll(List.of(libre));

        when(productoRepository.findById(404L)).thenReturn(Optional.empty());
        assertFalse(service.eliminar(404L));
    }

    private ProductoCatalogoRequest request(String sku, String name, int available,
                                            int locked, String currency) {
        return new ProductoCatalogoRequest(
                sku, name, "Descripción", "General", new BigDecimal("10.00"),
                currency, available, locked);
    }

    private Producto producto(String sku, Long id) {
        Producto product = new Producto(sku, "Producto", "Descripción", "General",
                new BigDecimal("10.00"), new Vendedor("Tienda", "tienda@test", "Catálogo"));
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private Deposito deposito(String nombre) {
        return new Deposito(nombre, "Dirección", -34.6, -58.4);
    }
}
