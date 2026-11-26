package logired.backend.integraciones;

import java.net.SocketTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import logired.backend.ruteo.dto.RutaRequest;
import logired.backend.ruteo.dto.RutaResponse;
import logired.backend.ruteo.service.RuteoService;

/**
 * Cliente del sistema moderno de ruteo (REST) con timeout y fallback local.
 *
 * Antes el timeout se medía con CompletableFuture.get(): el hilo que esperaba se liberaba,
 * pero la llamada HTTP seguía abierta en el pool común sin límite de tiempo (hilos colgados).
 * Ahora los timeouts de conexión y lectura están en el propio cliente HTTP, así que la
 * conexión se corta de verdad. Si el sistema externo no responde, falla o devuelve algo
 * vacío, se calcula la ruta localmente y el mensaje lo informa ("Fallback local: ...").
 */
@Component
public class ModernRuteoRestClient {

    private static final Logger log = LoggerFactory.getLogger(ModernRuteoRestClient.class);

    private final RestClient restClient;
    private final String ruteoUrl;
    private final RuteoService ruteoService;
    private final long timeoutMillis;

    public ModernRuteoRestClient(
            @Value("${logired.integraciones.rest.ruteo-url:}") String ruteoUrl,
            RuteoService ruteoService,
            @Value("${logired.integraciones.rest.timeout-millis:5000}") long timeoutMillis) {
        this.ruteoUrl = ruteoUrl;
        this.ruteoService = ruteoService;
        this.timeoutMillis = Math.max(1, timeoutMillis);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) this.timeoutMillis);
        requestFactory.setReadTimeout((int) this.timeoutMillis);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public RutaResponse calcularRuta(RutaRequest request) {
        if (request == null || request.getDirecciones() == null || request.getDirecciones().isEmpty()) {
            throw new IllegalArgumentException("Debe incluir al menos una dirección.");
        }
        if (ruteoUrl == null || ruteoUrl.isBlank()) {
            return calcularFallback(request, "La integración REST no tiene una URL configurada.");
        }

        try {
            RutaResponse response = restClient.post()
                    .uri(ruteoUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RutaResponse.class);
            if (response == null || response.getParadasOrdenadas() == null || response.getParadasOrdenadas().isEmpty()) {
                return calcularFallback(request, "El sistema moderno devolvió una respuesta vacía.");
            }
            return response;
        } catch (RestClientException exception) {
            if (esTimeout(exception)) {
                return calcularFallback(request,
                        "Timeout de " + timeoutMillis + " ms consultando el sistema moderno.");
            }
            log.warn("[REST] Falla consultando el sistema moderno de ruteo: {}", exception.getMessage());
            return calcularFallback(request,
                    "No fue posible comunicarse con el sistema moderno de ruteo.");
        }
    }

    private boolean esTimeout(Throwable exception) {
        for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
            if (causa instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private RutaResponse calcularFallback(RutaRequest request, String motivo) {
        RutaResponse fallback = ruteoService.calcularRutaConEstrategia(request);
        fallback.setMensaje("Fallback local: " + motivo);
        return fallback;
    }
}
