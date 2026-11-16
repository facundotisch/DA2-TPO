package logired.backend.pedido.service;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.repository.PedidoRepository;
import logired.backend.pedido.dto.ItemPedidoRequest;
import logired.backend.pedido.dto.PedidoCreacionRequest;
import logired.backend.producto.entity.Producto;
import logired.backend.producto.entity.Vendedor;
import logired.backend.producto.repository.ProductoRepository;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTests {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private DepositoRepository depositoRepository;

    @Mock
    private InventarioRepository inventarioRepository;

    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private PedidoService pedidoService;

    @Test
    void crearSinLineasAgregaUnProductoYReservaStockDelInventario() {
        Vendedor vendedor = new Vendedor("Tienda", "ventas@tienda.demo", "Catálogo");
        Producto producto = new Producto("SKU-001", "Producto", "Producto demo", "General",
                new BigDecimal("1200.00"), vendedor);
        org.springframework.test.util.ReflectionTestUtils.setField(producto, "id", 1L);
        Deposito deposito = new Deposito("Central", "Av. 9 de Julio, CABA", -34.6037, -58.3816);
        ItemInventario stock = new ItemInventario("SKU-001", "Producto", "Central", 5, 0);
        stock.setProducto(producto);
        Pedido pedido = new Pedido("Pedido de prueba", null, null);
        when(inventarioRepository.findAll()).thenReturn(List.of(stock));
        when(productoRepository.findAllById(Set.of(1L))).thenReturn(List.of(producto));
        when(inventarioRepository.findBySku("SKU-001")).thenReturn(List.of(stock));
        when(inventarioRepository.findBySkuAndDeposito("SKU-001", "Central")).thenReturn(Optional.of(stock));
        when(depositoRepository.findByNombre("Central")).thenReturn(Optional.of(deposito));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pedido creado = pedidoService.crear(pedido);

        assertEquals("CREADO", creado.getEstado());
        assertEquals(1, creado.getItems().size());
        assertEquals(1, creado.getItems().get(0).getCantidad());
        assertEquals("SKU-001", creado.getItems().get(0).getProducto().getSku());
        assertEquals(4, stock.getStockDisponible());
        assertEquals(1, stock.getStockBloqueado());
        assertEquals(1200.0, creado.getTotal());
        assertSame(deposito, creado.getItems().get(0).getDeposito());
    }

    @Test
    void actualizarModificaLosCamposDelPedidoExistente() {
        Pedido existente = new Pedido("Antes", "CREADO", 100.0);
        Pedido datos = new Pedido("Despues", "EN_RUTA", 250.0);
        when(pedidoRepository.findById(3L)).thenReturn(Optional.of(existente));
        when(pedidoRepository.save(existente)).thenReturn(existente);

        Optional<Pedido> resultado = pedidoService.actualizar(3L, datos);

        assertTrue(resultado.isPresent());
        assertEquals("Despues", existente.getDescripcion());
        assertEquals("EN_RUTA", existente.getEstado());
        assertEquals(250.0, existente.getTotal());
    }

    @Test
    void actualizarPedidoInexistenteNoGuardaCambios() {
        when(pedidoRepository.findById(404L)).thenReturn(Optional.empty());

        assertTrue(pedidoService.actualizar(404L, new Pedido("No existe", "CREADO", 10.0)).isEmpty());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void eliminarDevuelveFalseSiNoExiste() {
        when(pedidoRepository.findById(404L)).thenReturn(Optional.empty());

        assertFalse(pedidoService.eliminar(404L));
        verify(pedidoRepository, never()).deleteById(404L);
    }

    @Test
    void crearReservaStockAunqueElSkuNoCoincidaConElNombreDelDeposito() {
        Deposito deposito = new Deposito("Deposito central (demo)", "Av. 9 de Julio s/n, junto al Obelisco, CABA", -34.6037, -58.3816);
        ItemInventario item = new ItemInventario("SESSION-abc", "Item de sesión", "Deposito por sesion", 10, 0);
        Vendedor vendedor = new Vendedor("Tienda", "ventas@tienda.demo", "Catálogo");
        Producto producto = new Producto("SESSION-abc", "Item de sesión", "Producto demo", "General",
            new BigDecimal("1200.00"), vendedor);
        org.springframework.test.util.ReflectionTestUtils.setField(producto, "id", 2L);
        item.setProducto(producto);
        Pedido pedido = new Pedido("Pedido automático | Usuario: Usuario 3 | SKU:SESSION-abc | Producto: Item de sesión | Dirección: Calle Falsa 123", "CREADO", 1200.0);
        pedido.setDeposito(deposito);

        when(productoRepository.findBySku("SESSION-abc")).thenReturn(Optional.of(producto));
        when(productoRepository.findAllById(Set.of(2L))).thenReturn(List.of(producto));
        when(inventarioRepository.findBySku("SESSION-abc")).thenReturn(List.of(item));
        when(inventarioRepository.findBySkuAndDeposito("SESSION-abc", "Deposito por sesion"))
            .thenReturn(Optional.of(item));
        when(depositoRepository.findByNombre("Deposito por sesion"))
            .thenReturn(Optional.of(new Deposito("Deposito por sesion", "Direccion demo", -34.6, -58.4)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pedido creado = pedidoService.crear(pedido);

        assertEquals("CREADO", creado.getEstado());
        assertEquals(1, creado.getItems().size());
        assertEquals(9, item.getStockDisponible());
        assertEquals(1, item.getStockBloqueado());
    }

    @Test
    void completarPedidosHistoricosSinItemsAgregaProductoYReservaStock() {
        Vendedor vendedor = new Vendedor("Tienda", "ventas@tienda.demo", "Catálogo");
        Producto producto = new Producto("HIST-001", "Producto histórico", "Producto demo", "General",
                new BigDecimal("250.00"), vendedor);
        ReflectionTestUtils.setField(producto, "id", 5L);
        Deposito deposito = new Deposito("Central", "Av. 9 de Julio", -34.6037, -58.3816);
        ItemInventario stock = new ItemInventario("HIST-001", "Producto histórico", "Central", 3, 0);
        stock.setProducto(producto);
        Pedido pedidoExistente = new Pedido("Pedido #29", "CREADO", 1500.0);

        when(pedidoRepository.buscarPedidosSinItems()).thenReturn(List.of(pedidoExistente));
        when(inventarioRepository.findAll()).thenReturn(List.of(stock));
        when(productoRepository.findAllById(Set.of(5L))).thenReturn(List.of(producto));
        when(inventarioRepository.findBySku("HIST-001")).thenReturn(List.of(stock));
        when(inventarioRepository.findBySkuAndDeposito("HIST-001", "Central")).thenReturn(Optional.of(stock));
        when(depositoRepository.findByNombre("Central")).thenReturn(Optional.of(deposito));
        when(pedidoRepository.save(pedidoExistente)).thenReturn(pedidoExistente);

        int completados = pedidoService.completarPedidosSinItems();

        assertEquals(1, completados);
        assertEquals(1, pedidoExistente.getItems().size());
        assertEquals("HIST-001", pedidoExistente.getItems().get(0).getProducto().getSku());
        assertEquals(2, stock.getStockDisponible());
        assertEquals(1, stock.getStockBloqueado());
        assertEquals(250.0, pedidoExistente.getTotal());
    }

    @Test
    void crearPedidoDeCatalogoCalculaTotalYReservaLaCantidadSolicitada() {
        Vendedor vendedor = new Vendedor("Tienda Urbana", "ventas@tienda.demo", "Indumentaria");
        Producto producto = new Producto("IND-001", "Zapatillas", "Calzado running", "Indumentaria",
                new BigDecimal("125.50"), vendedor);
        ReflectionTestUtils.setField(producto, "id", 1L);
        Deposito deposito = new Deposito("Barracas", "Av. Iriarte 3500", -34.6462, -58.3858);
        ItemInventario stock = new ItemInventario("IND-001", "Zapatillas", "Barracas", 8, 0);
        ItemPedidoRequest linea = new ItemPedidoRequest();
        linea.setProductoId(1L);
        linea.setCantidad(2);
        PedidoCreacionRequest request = new PedidoCreacionRequest();
        request.setClienteNombre("Cliente Demo");
        request.setDireccionEntrega("Av. Corrientes 123");
        request.setItems(List.of(linea));

        when(productoRepository.findAllById(Set.of(1L))).thenReturn(List.of(producto));
        when(inventarioRepository.findBySku("IND-001")).thenReturn(List.of(stock));
        when(depositoRepository.findByNombre("Barracas")).thenReturn(Optional.of(deposito));
        when(inventarioRepository.findBySkuAndDeposito("IND-001", "Barracas")).thenReturn(Optional.of(stock));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pedido creado = pedidoService.crearDesdeCatalogo(request);

        assertEquals(251.0, creado.getTotal());
        assertEquals(1, creado.getItems().size());
        assertEquals(2, creado.getItems().get(0).getCantidad());
        assertEquals(new BigDecimal("125.50"), creado.getItems().get(0).getPrecioUnitario());
        assertEquals(6, stock.getStockDisponible());
        assertEquals(2, stock.getStockBloqueado());
        assertSame(deposito, creado.getDeposito());
    }

    @Test
    void crearPedidoPuedeDespacharLineasDesdeDepositosDistintos() {
        Vendedor vendedor = new Vendedor("Tienda", "ventas@tienda.demo", "Catálogo");
        Producto zapatillas = new Producto("IND-001", "Zapatillas", "Running", "Indumentaria",
                new BigDecimal("100.00"), vendedor);
        Producto auriculares = new Producto("ELEC-010", "Auriculares", "Bluetooth", "Electrónica",
                new BigDecimal("50.00"), vendedor);
        ReflectionTestUtils.setField(zapatillas, "id", 1L);
        ReflectionTestUtils.setField(auriculares, "id", 2L);
        Deposito barracas = new Deposito("Barracas", "Av. Iriarte", -34.6462, -58.3858);
        Deposito munro = new Deposito("Munro", "Av. Mitre", -34.5251, -58.5254);
        ItemInventario stockBarracas = new ItemInventario("IND-001", "Zapatillas", "Barracas", 4, 0);
        ItemInventario stockMunro = new ItemInventario("ELEC-010", "Auriculares", "Munro", 3, 0);
        ItemPedidoRequest lineaZapatillas = new ItemPedidoRequest();
        lineaZapatillas.setProductoId(1L);
        lineaZapatillas.setCantidad(2);
        ItemPedidoRequest lineaAuriculares = new ItemPedidoRequest();
        lineaAuriculares.setProductoId(2L);
        lineaAuriculares.setCantidad(1);
        PedidoCreacionRequest request = new PedidoCreacionRequest();
        request.setItems(List.of(lineaZapatillas, lineaAuriculares));

        when(productoRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(zapatillas, auriculares));
        when(inventarioRepository.findBySku("IND-001")).thenReturn(List.of(stockBarracas));
        when(inventarioRepository.findBySku("ELEC-010")).thenReturn(List.of(stockMunro));
        when(inventarioRepository.findBySkuAndDeposito("IND-001", "Barracas")).thenReturn(Optional.of(stockBarracas));
        when(inventarioRepository.findBySkuAndDeposito("ELEC-010", "Munro")).thenReturn(Optional.of(stockMunro));
        when(depositoRepository.findByNombre("Barracas")).thenReturn(Optional.of(barracas));
        when(depositoRepository.findByNombre("Munro")).thenReturn(Optional.of(munro));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pedido creado = pedidoService.crearDesdeCatalogo(request);

        assertEquals(250.0, creado.getTotal());
        assertEquals(2, creado.getItems().size());
        assertSame(barracas, creado.getItems().get(0).getDeposito());
        assertSame(munro, creado.getItems().get(1).getDeposito());
        assertEquals(2, stockBarracas.getStockBloqueado());
        assertEquals(1, stockMunro.getStockBloqueado());
    }
}