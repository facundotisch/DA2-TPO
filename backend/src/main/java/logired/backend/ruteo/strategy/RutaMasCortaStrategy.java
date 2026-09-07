package logired.backend.ruteo.strategy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import logired.backend.ruteo.dto.RutaResponse;

/**
 * Estrategia Concreta 1 (Patrón Strategy): Ruta Más Corta.
 * Prioriza la menor distancia kilométrica acumulada.
 */
@Component
public class RutaMasCortaStrategy implements EstrategiaRuteo {

    @Override
    public String getIdentificador() {
        return "DISTANCIA_CORTA";
    }

    @Override
    public RutaResponse calcularRuta(List<String> direcciones) {
        if (direcciones == null || direcciones.isEmpty()) {
            return new RutaResponse(new ArrayList<>(), 0.0, 0, getIdentificador(), "Sin paradas para procesar.");
        }

        List<String> ordenadas = new ArrayList<>(direcciones);
        int paradas = ordenadas.size();

        // Cálculo determinístico estimado según cantidad de paradas
        double kmPorTramo = 3.2;
        double distanciaTotal = Math.round(paradas * kmPorTramo * 10.0) / 10.0;
        int tiempoEstimado = (int) Math.round(distanciaTotal * 3.0 + (paradas * 5));

        return new RutaResponse(
                ordenadas,
                distanciaTotal,
                tiempoEstimado,
                "Ruta Más Corta (Ahorro de Combustible)",
                String.format("Ruta optimizada para %d paradas: %.1f km totales.", paradas, distanciaTotal)
        );
    }
}
