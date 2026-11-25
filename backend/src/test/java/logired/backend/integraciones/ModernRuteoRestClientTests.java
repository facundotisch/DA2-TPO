package logired.backend.integraciones;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import logired.backend.ruteo.dto.RutaRequest;
import logired.backend.ruteo.dto.RutaResponse;
import logired.backend.ruteo.service.RuteoService;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModernRuteoRestClientTests {

    @Test
    void usaFallbackLocalCuandoNoHayUrlRest() {
        RuteoService ruteoService = mock(RuteoService.class);
        RutaResponse fallback = new RutaResponse(
                List.of("Central", "Destino"), 2.0, 10,
                "DISTANCIA_CORTA", "Ruta local");
        when(ruteoService.calcularRutaConEstrategia(any(RutaRequest.class)))
                .thenReturn(fallback);

        ModernRuteoRestClient client =
                new ModernRuteoRestClient("", ruteoService, 5000);
        RutaResponse response = client.calcularRuta(
                new RutaRequest(List.of("Central", "Destino"), "DISTANCIA_CORTA"));

        assertTrue(response.getMensaje().startsWith("Fallback local:"));
        assertEquals("Central", response.getParadasOrdenadas().get(0));
    }

    @Test
    void timeoutDeCincoSegundosOmenorUsaFallback() throws Exception {
        RuteoService ruteoService = mock(RuteoService.class);
        RutaResponse fallback = new RutaResponse(
                List.of("Central"), 1.0, 5,
                "TIEMPO_RAPIDO", "Ruta local");
        when(ruteoService.calcularRutaConEstrategia(any(RutaRequest.class)))
                .thenReturn(fallback);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ruteo", exchange -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ModernRuteoRestClient client = new ModernRuteoRestClient(
                    "http://localhost:" + server.getAddress().getPort() + "/ruteo",
                    ruteoService,
                    50);

            long inicio = System.nanoTime();
            RutaResponse response = client.calcularRuta(
                    new RutaRequest(List.of("Central"), "TIEMPO_RAPIDO"));
            long transcurridoMs = (System.nanoTime() - inicio) / 1_000_000;

            assertTrue(response.getMensaje().startsWith("Fallback local:"));
            assertTrue(response.getMensaje().contains("Timeout"));
            assertTrue(transcurridoMs < 500);
        } finally {
            server.stop(0);
        }
    }
}
