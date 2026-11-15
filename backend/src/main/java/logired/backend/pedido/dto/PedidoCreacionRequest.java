package logired.backend.pedido.dto;

import java.util.ArrayList;
import java.util.List;

public class PedidoCreacionRequest {

    private String clienteNombre;
    private String direccionEntrega;
    private Long depositoId;
    private List<ItemPedidoRequest> items = new ArrayList<>();

    // Legacy fields remain accepted for existing clients.
    private String descripcion;
    private String estado;
    private Double total;

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

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }

    public boolean tieneItemsDeCatalogo() {
        return items != null && !items.isEmpty();
    }
}