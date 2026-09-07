package logired.backend.inventario.dto;

import java.util.Map;

public class ReservaStockResponse {

    private boolean exitoso;
    private String sku;
    private String deposito;
    private Integer cantidadReservada;
    private Integer stockDisponibleRestante;
    private Integer stockBloqueadoTotal;
    private String mensaje;
    private String idSesionConversacional;
    private Map<String, Integer> reservasActivasEnSesion;

    public ReservaStockResponse() {
    }

    public ReservaStockResponse(boolean exitoso, String sku, String deposito, Integer cantidadReservada,
                                Integer stockDisponibleRestante, Integer stockBloqueadoTotal, String mensaje,
                                String idSesionConversacional, Map<String, Integer> reservasActivasEnSesion) {
        this.exitoso = exitoso;
        this.sku = sku;
        this.deposito = deposito;
        this.cantidadReservada = cantidadReservada;
        this.stockDisponibleRestante = stockDisponibleRestante;
        this.stockBloqueadoTotal = stockBloqueadoTotal;
        this.mensaje = mensaje;
        this.idSesionConversacional = idSesionConversacional;
        this.reservasActivasEnSesion = reservasActivasEnSesion;
    }

    public boolean isExitoso() {
        return exitoso;
    }

    public void setExitoso(boolean exitoso) {
        this.exitoso = exitoso;
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

    public Integer getCantidadReservada() {
        return cantidadReservada;
    }

    public void setCantidadReservada(Integer cantidadReservada) {
        this.cantidadReservada = cantidadReservada;
    }

    public Integer getStockDisponibleRestante() {
        return stockDisponibleRestante;
    }

    public void setStockDisponibleRestante(Integer stockDisponibleRestante) {
        this.stockDisponibleRestante = stockDisponibleRestante;
    }

    public Integer getStockBloqueadoTotal() {
        return stockBloqueadoTotal;
    }

    public void setStockBloqueadoTotal(Integer stockBloqueadoTotal) {
        this.stockBloqueadoTotal = stockBloqueadoTotal;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getIdSesionConversacional() {
        return idSesionConversacional;
    }

    public void setIdSesionConversacional(String idSesionConversacional) {
        this.idSesionConversacional = idSesionConversacional;
    }

    public Map<String, Integer> getReservasActivasEnSesion() {
        return reservasActivasEnSesion;
    }

    public void setReservasActivasEnSesion(Map<String, Integer> reservasActivasEnSesion) {
        this.reservasActivasEnSesion = reservasActivasEnSesion;
    }
}
