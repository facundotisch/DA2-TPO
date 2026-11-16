package logired.backend.pedido.state;

import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.exception.TransicionInvalidaException;

public class EstadoEntregado implements EstadoPedido {
    @Override public void asignar(Pedido pedido) { throw new TransicionInvalidaException("Estado final: ya fue ENTREGADO."); }
    @Override public void ponerEnRuta(Pedido pedido) { throw new TransicionInvalidaException("Estado final: ya fue ENTREGADO."); }
    @Override public void entregar(Pedido pedido) { throw new TransicionInvalidaException("Estado final: ya fue ENTREGADO."); }
    @Override public void fallar(Pedido pedido) { throw new TransicionInvalidaException("Estado final: ya está FALLIDO."); }
    @Override public String getNombreEstado() { return "FALLIDO"; }
}