package logired.backend.pedido.service;

import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.pedido.dto.ItemPedidoRequest;
import logired.backend.pedido.dto.PedidoCreacionRequest;
import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.repository.PedidoRepository;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceBranchCoverageTests {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private DepositoRepository depositoRepository;
    @Mock
    private InventarioRepository inventarioRepository;
    @Mock
    private ProductoRepository productoRepository;

    private PedidoService service;

    @BeforeEach
    void setUp() {
        service = new PedidoService(
                pedidoRepository, depositoRepository, inventarioRepository,
                productoRepository, null, null, null);
    }

    @Test
    void rechazaSolicitudesVaciasYLineasInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> service.crearDesdeCatalogo(null));

        PedidoCreacionRequest request = new PedidoCreacionRequest();
        assertThrows(IllegalArgumentException.class, () -> service.crearDesdeCatalogo(request));
        request.setItems(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.crearDesdeCatalogo(request));

        request.setItems(List.of(linea(null, 1)));
        assertThrows(IllegalArgumentException.class, () -> service.crearDesdeCatalogo(request));
        request.setItems(List.of(linea(1L, null)));
        assertThrows(IllegalArgumentException.class, () -> service.crearDesdeCatalogo(request));
        request.setItems(List.of(linea(1L, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.crearDesdeCatalogo(request));

        verify(productoRepository, never()).findAllById(any());
    }

    @Test
    void rechazaDepositoInexistenteYProductoFueraDelCatalogo() {
        PedidoCreacionRequest request = request(1L, 1);
        request.setDepositoId(99L);
        when(depositoRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.crearDesdeCatalogo(request));

        request.setDepositoId(null);
        when(productoRepository.findAllById(Set.of(1L))).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.crearDesdeCatalogo(request));
    }

    @Test
    void pruebaDepositosAlternativosHastaEncontrarStockValido() {
        Producto producto = producto("SKU-1", 1L);
        ItemInventario primerStock = new ItemInventario("SKU-1", "Producto", "Munro", 5, 0);
        ItemInventario segundoStock = new ItemInventario("SKU-1", "Producto", "Barracas", 2, 0);
        Deposito barracas = new Deposito("Barracas", "Av. Iriarte", -34.6, -58.4);
        PedidoCreacionRequest request = request(1L, 2);

        when(productoRepository.findAllById(Set.of(1L))).thenReturn(List.of(producto));
        when(inventarioRepository.findBySku("SKU-1"))
                .thenReturn(List.of(segundoStock, primerStock));
        when(inventarioRepository.findBySkuAndDeposito("SKU-1", "Munro"))
                .thenReturn(Optional.empty());
        when(inventarioRepository.findBySkuAndDeposito("SKU-1", "Barracas"))
                .thenReturn(Optional.of(segundoStock));
        when(depositoRepository.findByNombre("Munro")).thenReturn(Optional.empty());
        when(depositoRepository.findByNombre("Barracas")).thenReturn(Optional.of(barracas));
        when(inventarioRepository.save(any(ItemInventario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(pedidoRepository.save(any(Pedido.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Pedido pedido = service.crearDesdeCatalogo(request);

        assertEquals("Barracas", pedido.getDeposito().getNombre());
        assertEquals(0, segundoStock.getStockDisponible());
        assertEquals(2, segundoStock.getStockBloqueado());
    }

    @Test
    void completarPedidosSinItemsDevuelveCeroCuandoNoHayPendientes() {
        when(pedidoRepository.buscarPedidosSinItems()).thenReturn(List.of());

        assertEquals(0, service.completarPedidosSinItems());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void pedidoHistoricoSinStockEnSuDepositoBuscaEnOtroDeposito() {
        Producto producto = producto("SKU-2", 2L);
        Deposito central = new Deposito("Central", "Av. Central", -34.6, -58.4);
        ReflectionTestUtils.setField(central, "id", 1L);
        Deposito munro = new Deposito("Munro", "Av. Mitre", -34.5, -58.5);
        ItemInventario stock = new ItemInventario("SKU-2", "Producto", "Munro", 4, 0);
        stock.setProducto(producto);
        Pedido historico = new Pedido(" ", "CREADO", null);
        historico.setDeposito(central);

        when(pedidoRepository.buscarPedidosSinItems()).thenReturn(List.of(historico));
        when(productoRepository.findAllById(Set.of(2L))).thenReturn(List.of(producto));
        when(depositoRepository.findById(1L)).thenReturn(Optional.of(central));
        when(inventarioRepository.findAll()).thenReturn(List.of(stock));
        when(inventarioRepository.findBySku("SKU-2")).thenReturn(List.of(stock));
        when(inventarioRepository.findBySkuAndDeposito("SKU-2", "Munro"))
                .thenReturn(Optional.of(stock));
        when(depositoRepository.findByNombre("Munro")).thenReturn(Optional.of(munro));
        when(inventarioRepository.save(any(ItemInventario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(pedidoRepository.save(historico)).thenReturn(historico);

        assertEquals(1, service.completarPedidosSinItems());

        assertEquals("Munro", historico.getDeposito().getNombre());
        assertEquals("Pedido completado automáticamente con productos del inventario.",
                historico.getDescripcion());
        assertEquals(1, historico.getItems().size());
    }

    @Test
    void eliminarPedidoLegacyLiberaStockEncontradoPorSku() {
        ItemInventario stock = new ItemInventario("SKU-3", "Producto", "Central", 0, 1);
        Pedido pedido = new Pedido("Pedido antiguo SKU:SKU-3", "CREADO", 10.0);
        when(pedidoRepository.findById(3L)).thenReturn(Optional.of(pedido));
        when(inventarioRepository.findBySkuAndDeposito("SKU-3", "Depósito Central Barracas"))
                .thenReturn(Optional.empty());
        when(inventarioRepository.findAll()).thenReturn(List.of(stock));

        assertTrue(service.eliminar(3L));

        assertEquals(1, stock.getStockDisponible());
        assertEquals(0, stock.getStockBloqueado());
        verify(pedidoRepository).deleteById(3L);
    }

    @Test
    void actualizarDesdeCatalogoRechazaNuloYDevuelveVacioParaPedidoInexistente() {
        assertThrows(IllegalArgumentException.class,
                () -> service.actualizarDesdeCatalogo(404L, null));
        when(pedidoRepository.findById(404L)).thenReturn(Optional.empty());
        assertTrue(service.actualizarDesdeCatalogo(404L, new PedidoCreacionRequest()).isEmpty());
    }

    @Test
    void actualizarPedidoLegacyConservaTotalCuandoTieneItems() {
        Pedido existente = new Pedido("Antes", "CREADO", 20.0);
        existente.agregarItem(new logired.backend.pedido.entity.PedidoItem(
                producto("SKU-4", 4L), null, 1, new BigDecimal("20.00")));
        Pedido cambios = new Pedido("Después", "EN_RUTA", 999.0);
        when(pedidoRepository.findById(4L)).thenReturn(Optional.of(existente));
        when(pedidoRepository.save(existente)).thenReturn(existente);

        assertEquals(Optional.of(existente), service.actualizar(4L, cambios));
        assertEquals(20.0, existente.getTotal());
        assertEquals("Después", existente.getDescripcion());
    }

    @Test
    void cambiarEstadoAplicaAccionesYRechazaAccionDesconocida() {
        Pedido pedido = new Pedido("Pedido", "CREADO", 10.0);
        when(pedidoRepository.findById(5L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(pedido)).thenReturn(pedido);

        assertEquals("ASIGNADO", service.cambiarEstado(5L, "ASIGNAR").getEstado());
        assertEquals("EN_RUTA", service.cambiarEstado(5L, "EN_RUTA").getEstado());
        assertEquals("ENTREGADO", service.cambiarEstado(5L, "ENTREGAR").getEstado());
        assertThrows(IllegalArgumentException.class,
                () -> service.cambiarEstado(5L, "NO_EXISTE"));
    }

    private PedidoCreacionRequest request(Long productId, Integer quantity) {
        PedidoCreacionRequest request = new PedidoCreacionRequest();
        request.setItems(List.of(linea(productId, quantity)));
        return request;
    }

    private ItemPedidoRequest linea(Long productId, Integer quantity) {
        ItemPedidoRequest item = new ItemPedidoRequest();
        item.setProductoId(productId);
        item.setCantidad(quantity);
        return item;
    }

    private Producto producto(String sku, Long id) {
        Vendedor vendedor = new Vendedor("Tienda", "ventas@tienda.test", "Catálogo");
        Producto producto = new Producto(
                sku, "Producto", "Descripción", "General", new BigDecimal("10.00"), vendedor);
        ReflectionTestUtils.setField(producto, "id", id);
        return producto;
    }
}
