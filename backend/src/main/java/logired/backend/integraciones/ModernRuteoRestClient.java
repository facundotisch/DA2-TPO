package logired.backend.integraciones;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import logired.backend.ruteo.dto.RutaRequest;
import logired.backend.ruteo.dto.RutaResponse;
import logired.backend.ruteo.service.RuteoService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ModernRuteoRestClient {

    private final RestClient restClient;
    private final String ruteoUrl;
    private final RuteoService ruteoService;
    private final long timeoutMillis;

    public ModernRuteoRestClient(
            @Value("${logired.integraciones.rest.ruteo-url:}") String ruteoUrl,
            RuteoService ruteoService,
            @Value("${logired.integraciones.rest.timeout-millis:5000}") long timeoutMillis) {
        this.restClient = RestClient.create();
        this.ruteoUrl = ruteoUrl;
        this.ruteoService = ruteoService;
        this.timeoutMillis = Math.max(1, timeoutMillis);
    }

    public RutaResponse calcularRuta(RutaRequest request) {
        if (ruteoUrl == null || ruteoUrl.isBlank()) {
            return calcularFallback(request, "La integración REST no tiene una URL configurada.");
        }

        try {
            CompletableFuture<RutaResponse> respuesta = CompletableFuture.supplyAsync(() ->
                    restClient.post()
                            .uri(ruteoUrl)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .toEntity(RutaResponse.class)
                            .getBody());
            RutaResponse response = respuesta.get(timeoutMillis, TimeUnit.MILLISECONDS);
            if (response == null) {
                return calcularFallback(request, "El sistema moderno devolvió una respuesta vacía.");
            }
            return response;
        } catch (TimeoutException exception) {
            return calcularFallback(request,
                    "Timeout de " + timeoutMillis + " ms consultando el sistema moderno.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return calcularFallback(request, "La consulta REST fue interrumpida.");
        } catch (ExecutionException exception) {
            return calcularFallback(request,
                    "No fue posible comunicarse con el sistema moderno de ruteo.");
        }
    }

    private RutaResponse calcularFallback(RutaRequest request, String motivo) {
        RutaResponse fallback = ruteoService.calcularRutaConEstrategia(request);
        fallback.setMensaje("Fallback local: " + motivo);
        return fallback;
    }
}
