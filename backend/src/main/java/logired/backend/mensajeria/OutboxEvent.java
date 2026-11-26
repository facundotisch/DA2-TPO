package logired.backend.mensajeria;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Evento pendiente de publicar en el broker (patrón Transactional Outbox).
 *
 * Se inserta en la misma transacción que el cambio de negocio. Un proceso programado
 * lo publica después y lo marca como ENVIADO; si el broker no responde, queda PENDIENTE
 * y se reintenta, así que ningún evento se pierde aunque ActiveMQ esté caído.
 */
@Entity
@Table(name = "outbox_eventos", indexes = @Index(name = "idx_outbox_estado", columnList = "estado, id"))
public class OutboxEvent {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ENVIADO = "ENVIADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, unique = true)
    private String eventoId;

    @Column(nullable = false)
    private String tipoEvento;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(nullable = false)
    private int intentos;

    @Column(length = 500)
    private String ultimoError;

    @Column(nullable = false)
    private LocalDateTime creadoEn;

    private LocalDateTime enviadoEn;

    protected OutboxEvent() {
    }

    public OutboxEvent(String eventoId, String tipoEvento, String payload) {
        this.eventoId = eventoId;
        this.tipoEvento = tipoEvento;
        this.payload = payload;
        this.estado = PENDIENTE;
        this.intentos = 0;
        this.creadoEn = LocalDateTime.now();
    }

    public void marcarEnviado() {
        this.estado = ENVIADO;
        this.enviadoEn = LocalDateTime.now();
        this.ultimoError = null;
    }

    public void registrarFallo(String error) {
        this.intentos++;
        String texto = error == null ? "Error desconocido" : error;
        this.ultimoError = texto.length() > 500 ? texto.substring(0, 500) : texto;
    }

    public Long getId() {
        return id;
    }

    public String getEventoId() {
        return eventoId;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public String getPayload() {
        return payload;
    }

    public String getEstado() {
        return estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public String getUltimoError() {
        return ultimoError;
    }
}
