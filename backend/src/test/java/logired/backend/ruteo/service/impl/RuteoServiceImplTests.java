package logired.backend.ruteo.service.impl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import logired.backend.ruteo.dto.RutaRequest;
import logired.backend.ruteo.dto.RutaResponse;
import logired.backend.ruteo.strategy.RutaMasCortaStrategy;
import logired.backend.ruteo.strategy.RutaMasRapidaStrategy;

class RuteoServiceImplTests {

    private final RuteoServiceImpl ruteoService = new RuteoServiceImpl(
            List.of(new RutaMasCortaStrategy(), new RutaMasRapidaStrategy()));

    @Test
    void seleccionaLaEstrategiaSolicitada() {
        RutaResponse response = ruteoService.calcularRutaConEstrategia(
                new RutaRequest(List.of("Barracas", "Microcentro"), "TIEMPO_RAPIDO"));

        assertEquals("Ruta Más Rápida (Circunvalación / Menor Tráfico)", response.getEstrategiaAplicada());
        assertEquals(7.6, response.getDistanciaTotalKm());
        assertEquals(List.of("Barracas", "Microcentro"), response.getParadasOrdenadas());
    }

    @Test
    void rutaSinParadasDevuelveResultadoVacio() {
        RutaResponse response = ruteoService.calcularRutaConEstrategia(
                new RutaRequest(List.of(), "DISTANCIA_CORTA"));

        assertTrue(response.getParadasOrdenadas().isEmpty());
        assertEquals(0.0, response.getDistanciaTotalKm());
    }
}