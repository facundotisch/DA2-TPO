package logired.backend.pedido.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import logired.backend.pedido.entity.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

	@Query("select pedido from Pedido pedido where pedido.deposito is null")
	List<Pedido> buscarPedidosSinDeposito();
}