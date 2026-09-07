package logired.backend.inventario.dto;

public class ReservaStockRequest {

    private String sku;
    private String deposito;
    private Integer cantidad;

    public ReservaStockRequest() {
    }

    public ReservaStockRequest(String sku, String deposito, Integer cantidad) {
        this.sku = sku;
        this.deposito = deposito;
        this.cantidad = cantidad;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getDeposito() {
        return deposito;
    }

    public void setDeposito(String deposito) {
        this.deposito = deposito;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }
}
