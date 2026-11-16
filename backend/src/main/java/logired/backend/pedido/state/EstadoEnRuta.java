package logired.backend.pedido.state;

import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.exception.TransicionInvalidaException;

public class EstadoEnRuta implements EstadoPedido {
    @Override
    public void asignar(Pedido pedido) { 
        throw new TransicionInvalidaException("El pedido ya está EN_RUTA."); 
    }

    @Override
    public void ponerEnRuta(Pedido pedido) { 
        throw new TransicionInvalidaException("El pedido ya está EN_RUTA."); 
    }

    @Override
    public void entregar(Pedido pedido) {
        pedido.setEstadoInstancia(new EstadoEntregado());
    }

    @Override
    public void fallar(Pedido pedido) {
        pedido.setEstadoInstancia(new EstadoFallido());
    }

    @Override
    public String getNombreEstado() { return "EN_RUTA"; }
}