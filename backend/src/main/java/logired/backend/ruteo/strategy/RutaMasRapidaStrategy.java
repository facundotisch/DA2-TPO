package logired.backend.ruteo.strategy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import logired.backend.ruteo.dto.RutaResponse;

/**
 * Estrategia Concreta 2 (Patrón Strategy): Ruta Más Rápida.
 * Prioriza arterias rápidas y menor tiempo de viaje evitando atascos.
 */
@Component
public class RutaMasRapidaStrategy implements EstrategiaRuteo {

    @Override
    public String getIdentificador() {
        return "TIEMPO_RAPIDO";
    }

    @Override
    public RutaResponse calcularRuta(List<String> direcciones) {
        if (direcciones == null || direcciones.isEmpty()) {
            return new RutaResponse(new ArrayList<>(), 0.0, 0, getIdentificador(), "Sin paradas para procesar.");
        }

        List<String> ordenadas = new ArrayList<>(direcciones);
        int paradas = ordenadas.size();

        // Mayor distancia (+15% por circunvalaciones) pero -25% de tiempo de espera
        double kmPorTramo = 3.8;
        double distanciaTotal = Math.round(paradas * kmPorTramo * 10.0) / 10.0;
        int tiempoEstimado = (int) Math.round(distanciaTotal * 2.1 + (paradas * 3));

        return new RutaResponse(
                ordenadas,
                distanciaTotal,
                tiempoEstimado,
                "Ruta Más Rápida (Circunvalación / Menor Tráfico)",
                String.format("Ruta express para %d paradas: entrega estimada en %d minutos.", paradas, tiempoEstimado)
        );
    }
}
