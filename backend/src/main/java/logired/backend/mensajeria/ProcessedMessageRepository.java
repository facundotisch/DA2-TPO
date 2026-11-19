package logired.backend.mensajeria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {
    Optional<ProcessedMessage> findByEventoIdAndConsumerName(String eventoId, String consumerName);
}
