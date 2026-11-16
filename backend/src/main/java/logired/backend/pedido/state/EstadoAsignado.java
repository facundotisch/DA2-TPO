package logired.backend.pedido.state;

import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.exception.TransicionInvalidaException;

public class EstadoAsignado implements EstadoPedido {
    @Override
    public void asignar(Pedido pedido) { 
        throw new TransicionInvalidaException("El pedido ya está ASIGNADO."); 
    }

    @Override
    public void ponerEnRuta(Pedido pedido) {
        pedido.setEstadoInstancia(new EstadoEnRuta());
    }

    @Override
    public void entregar(Pedido pedido) { 
        throw new TransicionInvalidaException("El pedido debe estar EN_RUTA antes de entregarse."); 
    }

    @Override
    public void fallar(Pedido pedido) { 
        throw new TransicionInvalidaException("El pedido aún no salió a ruta para poder fallar."); 
    }

    @Override
    public String getNombreEstado() { return "ASIGNADO"; }
}