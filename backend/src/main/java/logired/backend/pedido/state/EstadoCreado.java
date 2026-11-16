package logired.backend.pedido.state;

import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.exception.TransicionInvalidaException;

public class EstadoCreado implements EstadoPedido {
    @Override
    public void asignar(Pedido pedido) {
        pedido.setEstadoInstancia(new EstadoAsignado());
    }

    @Override
    public void ponerEnRuta(Pedido pedido) { 
        throw new TransicionInvalidaException("Un pedido CREADO debe ser ASIGNADO antes de salir a ruta."); 
    }

    @Override
    public void entregar(Pedido pedido) { 
        throw new TransicionInvalidaException("No se puede entregar un pedido recién CREADO."); 
    }

    @Override
    public void fallar(Pedido pedido) { 
        throw new TransicionInvalidaException("Un pedido CREADO no puede fallar sin haber salido."); 
    }

    @Override
    public String getNombreEstado() { return "CREADO"; }
}