package logired.backend.ruteo.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Contrato Explícito de Salida para RuteoService (Principio SOA 1)
 */
public class RutaResponse {

    private List<String> paradasOrdenadas = new ArrayList<>();
    private Double distanciaTotalKm;
    private Integer tiempoEstimadoMinutos;
    private String estrategiaAplicada;
    private String mensaje;

    public RutaResponse() {
    }

    public RutaResponse(List<String> paradasOrdenadas, Double distanciaTotalKm, Integer tiempoEstimadoMinutos, String estrategiaAplicada, String mensaje) {
        this.paradasOrdenadas = paradasOrdenadas;
        this.distanciaTotalKm = distanciaTotalKm;
        this.tiempoEstimadoMinutos = tiempoEstimadoMinutos;
        this.estrategiaAplicada = estrategiaAplicada;
        this.mensaje = mensaje;
    }

    public List<String> getParadasOrdenadas() {
        return paradasOrdenadas;
    }

    public void setParadasOrdenadas(List<String> paradasOrdenadas) {
        this.paradasOrdenadas = paradasOrdenadas;
    }

    public Double getDistanciaTotalKm() {
        return distanciaTotalKm;
    }

    public void setDistanciaTotalKm(Double distanciaTotalKm) {
        this.distanciaTotalKm = distanciaTotalKm;
    }

    public Integer getTiempoEstimadoMinutos() {
        return tiempoEstimadoMinutos;
    }

    public void setTiempoEstimadoMinutos(Integer tiempoEstimadoMinutos) {
        this.tiempoEstimadoMinutos = tiempoEstimadoMinutos;
    }

    public String getEstrategiaAplicada() {
        return estrategiaAplicada;
    }

    public void setEstrategiaAplicada(String estrategiaAplicada) {
        this.estrategiaAplicada = estrategiaAplicada;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
