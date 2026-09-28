package logired.backend.pedido.service;

import java.util.Optional;

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

import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.repository.PedidoRepository;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTests {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private DepositoRepository depositoRepository;

    @InjectMocks
    private PedidoService pedidoService;

    @Test
    void crearEstableceEstadoYAsignaDepositoPredeterminado() {
        Pedido pedido = new Pedido("Paquete", null, 1200.0);
        Deposito deposito = new Deposito("Central", "Av. 9 de Julio, CABA", -34.6037, -58.3816);
        when(depositoRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(deposito));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pedido creado = pedidoService.crear(pedido);

        assertEquals("CREADO", creado.getEstado());
        assertSame(deposito, creado.getDeposito());
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
        when(pedidoRepository.existsById(404L)).thenReturn(false);

        assertFalse(pedidoService.eliminar(404L));
        verify(pedidoRepository, never()).deleteById(404L);
    }
}