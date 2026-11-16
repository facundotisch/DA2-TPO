package logired.backend.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

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
}
