package logired.backend.pedido.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Cuerpo de POST /api/pedido.
 *
 * No tiene "estado" ni "total": el estado inicial siempre es CREADO y el total lo calcula
 * el backend con los precios del catálogo. Cualquier campo que no esté declarado acá
 * se rechaza con HTTP 400 (protección contra mass assignment).
 */
public class PedidoCreacionRequest {

    private String clienteNombre;
    private String direccionEntrega;
    private Double latitudEntrega;
    private Double longitudEntrega;
    private Long depositoId;
    private List<ItemPedidoRequest> items = new ArrayList<>();

    // Se acepta solo para clientes legacy que envían "SKU:<codigo>" en la descripción.
    private String descripcion;

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

    public Double getLatitudEntrega() {
        return latitudEntrega;
    }

    public void setLatitudEntrega(Double latitudEntrega) {
        this.latitudEntrega = latitudEntrega;
    }

    public Double getLongitudEntrega() {
        return longitudEntrega;
    }

    public void setLongitudEntrega(Double longitudEntrega) {
        this.longitudEntrega = longitudEntrega;
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

    public boolean tieneItemsDeCatalogo() {
        return items != null && !items.isEmpty();
    }

    @JsonAnySetter
    void rechazarCampoNoPermitido(String campo, Object valor) {
        throw new IllegalArgumentException("El campo '" + campo + "' no se puede enviar al crear un pedido.");
    }
}
