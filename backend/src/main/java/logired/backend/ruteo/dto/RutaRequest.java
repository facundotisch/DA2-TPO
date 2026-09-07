package logired.backend.ruteo.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Contrato Explícito de Entrada para RuteoService (Principio SOA 1)
 */
public class RutaRequest {

    private List<String> direcciones = new ArrayList<>();
    private String criterio; // "DISTANCIA_CORTA" o "TIEMPO_RAPIDO"

    public RutaRequest() {
    }

    public RutaRequest(List<String> direcciones, String criterio) {
        this.direcciones = direcciones;
        this.criterio = criterio;
    }

    public List<String> getDirecciones() {
        return direcciones;
    }

    public void setDirecciones(List<String> direcciones) {
        this.direcciones = direcciones;
    }

    public String getCriterio() {
        return criterio;
    }

    public void setCriterio(String criterio) {
        this.criterio = criterio;
    }
}
