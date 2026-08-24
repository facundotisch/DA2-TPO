package logired.backend.pedido.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.repository.PedidoRepository;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;

    public PedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    public List<Pedido> obtenerTodos() {
        return pedidoRepository.findAll();
    }

    public Optional<Pedido> obtenerPorId(Long id) {
        return pedidoRepository.findById(id);
    }

    public Pedido crear(Pedido pedido) {
        pedido.setEstado("CREADO");
        return pedidoRepository.save(pedido);
    }

    public Optional<Pedido> actualizar(Long id, Pedido datos) {

        return pedidoRepository.findById(id)
                .map(pedido -> {

                    pedido.setDescripcion(datos.getDescripcion());
                    pedido.setEstado(datos.getEstado());
                    pedido.setTotal(datos.getTotal());

                    return pedidoRepository.save(pedido);
                });
    }

    public boolean eliminar(Long id) {

        if (!pedidoRepository.existsById(id)) {
            return false;
        }

        pedidoRepository.deleteById(id);
        return true;
    }
}