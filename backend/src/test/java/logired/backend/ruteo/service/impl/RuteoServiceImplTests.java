package logired.backend.ruteo.service.impl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void criterioNuloUsaLaEstrategiaPorDefecto() {
        RutaResponse response = ruteoService.calcularRutaConEstrategia(
                new RutaRequest(List.of("A", "B"), null));

        assertEquals("Ruta Más Corta (Ahorro de Combustible)", response.getEstrategiaAplicada());
    }

    @Test
    void criterioDesconocidoUsaLaEstrategiaPorDefecto() {
        RutaResponse response = ruteoService.calcularRutaConEstrategia(
                new RutaRequest(List.of("A", "B"), "NO_EXISTE"));

        assertEquals("Ruta Más Corta (Ahorro de Combustible)", response.getEstrategiaAplicada());
    }

    @Test
    void solicitudNulaODireccionesNulasSonRechazadas() {
        assertThrows(IllegalArgumentException.class,
                () -> ruteoService.calcularRutaConEstrategia(null));
        assertThrows(IllegalArgumentException.class,
                () -> ruteoService.calcularRutaConEstrategia(new RutaRequest(null, null)));
    }

    @Test
    void usaLaPrimeraEstrategiaCuandoNoExisteLaPredeterminada() {
        RuteoServiceImpl service = new RuteoServiceImpl(List.of(new RutaMasRapidaStrategy()));

        RutaResponse response = service.calcularRutaOptima(List.of("A", "B"));

        assertEquals("Ruta Más Rápida (Circunvalación / Menor Tráfico)", response.getEstrategiaAplicada());
    }

    @Test
    void rechazaElCalculoSiNoHayEstrategias() {
        RuteoServiceImpl service = new RuteoServiceImpl(List.of());

        assertThrows(IllegalStateException.class,
                () -> service.calcularRutaOptima(List.of("A", "B")));
        assertThrows(IllegalStateException.class,
                () -> service.calcularRutaConEstrategia(new RutaRequest(List.of("A"), null)));
    }
}