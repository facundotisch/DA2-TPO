package logired.backend.ruteo.strategy;

import java.util.List;

import logired.backend.ruteo.dto.RutaResponse;

/**
 * Patrón Strategy (Unidad III y Sección 2 del Informe Técnico de LogiRed)
 * Define la interfaz común para los distintos algoritmos de optimización de rutas.
 */
public interface EstrategiaRuteo {

    String getIdentificador();

    RutaResponse calcularRuta(List<String> direcciones);
}
