package logired.backend.mensajeria;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop50ByEstadoOrderByIdAsc(String estado);

    long countByEstado(String estado);
}
