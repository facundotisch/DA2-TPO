package logired.backend.pedido.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Cuerpo de PUT /api/pedido/{id}.
 *
 * Solo admite datos de entrega y líneas del pedido. El estado cambia únicamente por
 * PATCH /api/pedido/{id}/estado (patrón State) y el total lo calcula el backend.
 * Enviar "estado", "total", "id" o cualquier otro campo no declarado devuelve HTTP 400.
 */
public class PedidoActualizacionRequest {

    private String clienteNombre;
    private String direccionEntrega;
    private String descripcion;
    private Long depositoId;
    private List<ItemPedidoRequest> items = new ArrayList<>();

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }

    public String getDireccionEntrega() {
        return direccionEntrega;
    }

    public void setDireccionEntrega(String direccionEntrega) {
        this.direccionEntrega = direccionEntrega;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Long getDepositoId() {
        return depositoId;
    }

    public void setDepositoId(Long depositoId) {
        this.depositoId = depositoId;
    }

    public List<ItemPedidoRequest> getItems() {
        return items;
    }

    public void setItems(List<ItemPedidoRequest> items) {
        this.items = items;
    }

    public boolean tieneItemsDeCatalogo() {
        return items != null && !items.isEmpty();
    }

    /** Convierte las líneas al formato que usa la selección de reservas del servicio. */
    public PedidoCreacionRequest comoSolicitudDeReserva() {
        PedidoCreacionRequest solicitud = new PedidoCreacionRequest();
        solicitud.setClienteNombre(clienteNombre);
        solicitud.setDireccionEntrega(direccionEntrega);
        solicitud.setDescripcion(descripcion);
        solicitud.setDepositoId(depositoId);
        solicitud.setItems(items);
        return solicitud;
    }

    @JsonAnySetter
    void rechazarCampoNoPermitido(String campo, Object valor) {
        throw new IllegalArgumentException("El campo '" + campo + "' no se puede modificar por PUT.");
    }
}
