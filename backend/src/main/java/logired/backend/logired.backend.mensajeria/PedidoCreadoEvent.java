package logired.backend.mensajeria;

import java.time.LocalDateTime;

public class PedidoCreadoEvent {
    private String eventoId;
    private String tipoEvento;
    private Long pedidoId;
    private String descripcion;
    private Double total;
    private String estado;
    private String timestamp;

    public PedidoCreadoEvent() {}

    public PedidoCreadoEvent(String eventoId, String tipoEvento, Long pedidoId, String descripcion, Double total, String estado) {
        this.eventoId = eventoId;
        this.tipoEvento = tipoEvento;
        this.pedidoId = pedidoId;
        this.descripcion = descripcion;
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
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}