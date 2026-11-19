package logired.backend.mensajeria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_processed_event_consumer",
                columnNames = {"evento_id", "consumer_name"}))
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private String eventoId;

    @Column(name = "consumer_name", nullable = false)
    private String consumerName;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    protected ProcessedMessage() {
    }

    public ProcessedMessage(String eventoId, String consumerName) {
        this.eventoId = eventoId;
        this.consumerName = consumerName;
        this.processedAt = LocalDateTime.now();
    }

    public String getEventoId() {
        return eventoId;
    }
}
