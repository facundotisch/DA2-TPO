package logired.backend.pedido.state;

import logired.backend.pedido.entity.Pedido;

public interface EstadoPedido {
    void asignar(Pedido pedido);
    void ponerEnRuta(Pedido pedido);
    void entregar(Pedido pedido);
    void fallar(Pedido pedido);
    String getNombreEstado();
}