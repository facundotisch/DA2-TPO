package logired.backend.pedido.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.repository.PedidoRepository;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final DepositoRepository depositoRepository;

    public PedidoService(PedidoRepository pedidoRepository, DepositoRepository depositoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.depositoRepository = depositoRepository;
    }

    @Transactional(readOnly = true)
    public List<Pedido> obtenerTodos() {
        return pedidoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Pedido> obtenerPorId(Long id) {
        return pedidoRepository.findById(id);
    }

    @Transactional
    public Pedido crear(Pedido pedido) {
        pedido.setEstado("CREADO");
        if (pedido.getDeposito() == null) {
            Deposito deposito = depositoRepository.findFirstByOrderByIdAsc()
                    .orElseThrow(() -> new IllegalStateException("No hay un deposito configurado"));
            pedido.setDeposito(deposito);
        }
        return pedidoRepository.save(pedido);
    }

    @Transactional
    public Optional<Pedido> actualizar(Long id, Pedido datos) {

        return pedidoRepository.findById(id)
                .map(pedido -> {

                    pedido.setDescripcion(datos.getDescripcion());
                    pedido.setEstado(datos.getEstado());
                    pedido.setTotal(datos.getTotal());

                    return pedidoRepository.save(pedido);
                });
    }

    @Transactional
    public boolean eliminar(Long id) {

        if (!pedidoRepository.existsById(id)) {
            return false;
        }

        pedidoRepository.deleteById(id);
        return true;
    }
}