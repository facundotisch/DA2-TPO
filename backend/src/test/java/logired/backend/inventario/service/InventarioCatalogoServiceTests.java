package logired.backend.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.inventario.dto.ProductoStockRequest;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.producto.entity.Producto;
import logired.backend.producto.entity.Vendedor;
import logired.backend.producto.repository.ProductoRepository;
import logired.backend.producto.repository.VendedorRepository;

@ExtendWith(MockitoExtension.class)
class InventarioCatalogoServiceTests {

    @Mock
    private InventarioRepository inventarioRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private VendedorRepository vendedorRepository;

    @Mock
    private DepositoRepository depositoRepository;

    @InjectMocks
    private InventarioCatalogoService service;

    @Test
    void crearProductoRegistraPrecioYCantidadInicial() {
        ProductoStockRequest request = new ProductoStockRequest(
                "ZAP-001", "Zapatillas urbanas", "Calzado deportivo", "Calzado",
                new BigDecimal("85000.00"), 10, "Central");
        when(depositoRepository.findByNombre("Central"))
                .thenReturn(Optional.of(new Deposito("Central", "Av. Central", -34.6, -58.4)));
        when(inventarioRepository.findBySkuAndDeposito("ZAP-001", "Central")).thenReturn(Optional.empty());
        when(productoRepository.findBySku("ZAP-001")).thenReturn(Optional.empty());
        when(vendedorRepository.findByNombre("Comercios LogiRed")).thenReturn(Optional.empty());
        when(vendedorRepository.save(any(Vendedor.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventarioRepository.save(any(ItemInventario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemInventario creado = service.crear(request);

        assertEquals("ZAP-001", creado.getSku());
        assertEquals(10, creado.getStockDisponible());
        assertEquals(new BigDecimal("85000.00"), creado.getProducto().getPrecio());
    }

    @Test
    void actualizarNoPermiteReducirCantidadPorDebajoDelStockReservado() {
        Vendedor vendedor = new Vendedor("Comercios LogiRed", "catalogo@logired.demo", "Comercio");
        Producto producto = new Producto("ZAP-001", "Zapatillas", "Calzado", "Calzado",
                new BigDecimal("85000.00"), vendedor);
        ItemInventario item = new ItemInventario("ZAP-001", "Zapatillas", "Central", 4, 3);
        item.setProducto(producto);
        when(inventarioRepository.findById(1L)).thenReturn(Optional.of(item));
        when(depositoRepository.findByNombre("Central"))
                .thenReturn(Optional.of(new Deposito("Central", "Av. Central", -34.6, -58.4)));

        ProductoStockRequest request = new ProductoStockRequest(
                "ZAP-001", "Zapatillas", "Calzado", "Calzado",
                new BigDecimal("90000.00"), 2, "Central");

        assertThrows(IllegalArgumentException.class, () -> service.actualizar(1L, request));
        verify(inventarioRepository, never()).save(any(ItemInventario.class));
    }

    @Test
    void crearRechazaSolicitudesYValoresInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> service.crear(null));
        assertThrows(IllegalArgumentException.class, () -> service.crear(
                new ProductoStockRequest(null, "Nombre", null, null,
                        new BigDecimal("1.00"), 1, "Central")));
        assertThrows(IllegalArgumentException.class, () -> service.crear(
                new ProductoStockRequest("SKU", "Nombre", null, null,
                        BigDecimal.ZERO, 1, "Central")));
        assertThrows(IllegalArgumentException.class, () -> service.crear(
                new ProductoStockRequest("SKU", "Nombre", null, null,
                        new BigDecimal("1.00"), -1, "Central")));
    }

    @Test
    void crearRechazaDepositoInexistenteOSkuDuplicado() {
        ProductoStockRequest missingDepotRequest = new ProductoStockRequest(
                "SKU", "Producto", null, null, new BigDecimal("10.00"), 1, "Ausente");
        when(depositoRepository.findByNombre("Ausente")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.crear(missingDepotRequest));

        ProductoStockRequest duplicateRequest = new ProductoStockRequest(
                "SKU", "Producto", null, null, new BigDecimal("10.00"), 1, "Central");
        when(depositoRepository.findByNombre("Central"))
                .thenReturn(Optional.of(new Deposito("Central", "Dirección", -34.6, -58.4)));
        when(inventarioRepository.findBySkuAndDeposito("SKU", "Central"))
                .thenReturn(Optional.of(new ItemInventario("SKU", "Producto", "Central", 1, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.crear(duplicateRequest));
    }

    @Test
    void crearUsaVendedorExistenteYActualizaElProductoDelCatalogo() {
        Vendedor vendedor = new Vendedor("Comercios LogiRed", "catalogo@logired.demo", "Comercio");
        Producto producto = new Producto("SKU", "Anterior", null, null,
                new BigDecimal("5.00"), vendedor);
        Deposito deposito = new Deposito("Central", "Dirección", -34.6, -58.4);
        ProductoStockRequest request = new ProductoStockRequest(
                "SKU", "Actualizado", "Desc", "General", new BigDecimal("10.00"), 4, "Central");
        when(depositoRepository.findByNombre("Central")).thenReturn(Optional.of(deposito));
        when(inventarioRepository.findBySkuAndDeposito("SKU", "Central")).thenReturn(Optional.empty());
        when(productoRepository.findBySku("SKU")).thenReturn(Optional.of(producto));
        when(productoRepository.save(producto)).thenReturn(producto);
        when(inventarioRepository.findBySku("SKU")).thenReturn(List.of());
        when(inventarioRepository.save(any(ItemInventario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ItemInventario created = service.crear(request);

        assertEquals("Actualizado", producto.getNombre());
        assertEquals(new BigDecimal("10.00"), producto.getPrecio());
        assertEquals(4, created.getStockDisponible());
        assertTrue(created.getProducto() == producto);
    }

    @Test
    void actualizarDevuelveVacioParaIdDesconocidoYValidaDestinoSkuYDuplicados() {
        ProductoStockRequest request = new ProductoStockRequest(
                "SKU", "Producto", null, null, new BigDecimal("10.00"), 4, "Central");
        when(inventarioRepository.findById(404L)).thenReturn(Optional.empty());
        assertTrue(service.actualizar(404L, request).isEmpty());

        ItemInventario item = new ItemInventario("SKU", "Producto", "Central", 4, 0);
        when(inventarioRepository.findById(1L)).thenReturn(Optional.of(item));
        when(depositoRepository.findByNombre("Central")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.actualizar(1L, request));

        when(depositoRepository.findByNombre("Central"))
                .thenReturn(Optional.of(new Deposito("Central", "Dirección", -34.6, -58.4)));
        ProductoStockRequest changedSku = new ProductoStockRequest(
                "OTHER", "Producto", null, null, new BigDecimal("10.00"), 4, "Central");
        assertThrows(IllegalArgumentException.class, () -> service.actualizar(1L, changedSku));

        ItemInventario other = new ItemInventario("SKU", "Producto", "Munro", 4, 0);
        org.springframework.test.util.ReflectionTestUtils.setField(other, "id", 2L);
        when(inventarioRepository.findBySkuAndDeposito("SKU", "Central"))
                .thenReturn(Optional.of(other));
        assertThrows(IllegalArgumentException.class, () -> service.actualizar(1L, request));
    }

    @Test
    void actualizarRequiereProductoSiLaFilaNoEstaEnlazada() {
        ItemInventario item = new ItemInventario("SKU", "Producto", "Central", 4, 0);
        ProductoStockRequest request = new ProductoStockRequest(
                "SKU", "Producto", null, null, new BigDecimal("10.00"), 4, "Central");
        when(inventarioRepository.findById(1L)).thenReturn(Optional.of(item));
        when(depositoRepository.findByNombre("Central"))
                .thenReturn(Optional.of(new Deposito("Central", "Dirección", -34.6, -58.4)));
        when(inventarioRepository.findBySkuAndDeposito("SKU", "Central")).thenReturn(Optional.empty());
        when(productoRepository.findBySku("SKU")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.actualizar(1L, request));
    }

    @Test
    void actualizarFilaEnlazadaAjustaDisponibleConStockBloqueadoNulo() {
        Vendedor vendedor = new Vendedor("Comercios LogiRed", "catalogo@logired.demo", "Comercio");
        Producto producto = new Producto("SKU", "Producto", null, null,
                new BigDecimal("10.00"), vendedor);
        ItemInventario item = new ItemInventario("SKU", "Anterior", "Central", 2, null);
        item.setProducto(producto);
        ProductoStockRequest request = new ProductoStockRequest(
                "SKU", "Actualizado", null, null, new BigDecimal("12.00"), 5, "Central");
        when(inventarioRepository.findById(2L)).thenReturn(Optional.of(item));
        when(depositoRepository.findByNombre("Central"))
                .thenReturn(Optional.of(new Deposito("Central", "Dirección", -34.6, -58.4)));
        when(inventarioRepository.findBySkuAndDeposito("SKU", "Central")).thenReturn(Optional.empty());
        when(productoRepository.save(producto)).thenReturn(producto);
        when(inventarioRepository.findBySku("SKU")).thenReturn(List.of(item));
        when(inventarioRepository.saveAll(List.of(item))).thenReturn(List.of(item));
        when(inventarioRepository.save(item)).thenReturn(item);

        assertTrue(service.actualizar(2L, request).isPresent());

        assertEquals("Actualizado", item.getNombre());
        assertEquals(5, item.getStockDisponible());
        assertNull(item.getStockBloqueado());
    }

    @Test
    void eliminarDevuelveFalsoSiNoExisteYRechazaStockBloqueado() {
        when(inventarioRepository.findById(404L)).thenReturn(Optional.empty());
        assertFalse(service.eliminar(404L));

        when(inventarioRepository.findById(1L))
                .thenReturn(Optional.of(new ItemInventario("SKU", "Producto", "Central", 0, 1)));
        assertThrows(IllegalStateException.class, () -> service.eliminar(1L));
        verify(inventarioRepository, never()).delete(any(ItemInventario.class));
    }
}
