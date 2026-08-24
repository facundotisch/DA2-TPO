package logired.backend.pedido.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import logired.backend.pedido.entity.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
}