package logired.backend.mensajeria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Evento publicado al crear un pedido.
 *
 * No incluye la descripción del pedido: esa descripción contiene el nombre del cliente y la
 * dirección de entrega (datos personales, Ley 25.326) y los consumidores no la necesitan.
 * Los consumidores que precisen más datos los consultan por pedidoId con autenticación.
 * Se ignoran campos desconocidos para poder leer mensajes viejos que aún estén en cola.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PedidoCreadoEvent {
    private String eventoId;
    private String tipoEvento;
    private Long pedidoId;
    private Double total;
    private String estado;
    private String timestamp;

    public PedidoCreadoEvent() {}

    public PedidoCreadoEvent(String eventoId, String tipoEvento, Long pedidoId, Double total, String estado) {
        this.eventoId = eventoId;
        this.tipoEvento = tipoEvento;
        this.pedidoId = pedidoId;
        this.total = total;
        this.estado = estado;
        this.timestamp = LocalDateTime.now().toString();
    }

    // Getters y Setters
    public String getEventoId() { return eventoId; }
    public void setEventoId(String eventoId) { this.eventoId = eventoId; }
    public String getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(String tipoEvento) { this.tipoEvento = tipoEvento; }
    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }
    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}