package logired.backend.integraciones;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class AiGatewayClient {

    private final RestClient restClient;
    private final String gatewayUrl;
    private final String apiKey;
    private final String model;
    private final long timeoutMillis;

    public AiGatewayClient(
            @Value("${logired.integraciones.ia.gateway-url:}") String gatewayUrl,
            @Value("${logired.integraciones.ia.api-key:}") String apiKey,
            @Value("${logired.integraciones.ia.model:logired-gateway}") String model,
            @Value("${logired.integraciones.ia.timeout-millis:5000}") long timeoutMillis) {
        this.gatewayUrl = gatewayUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMillis = Math.max(1, timeoutMillis);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeoutMillis);
        requestFactory.setReadTimeout((int) timeoutMillis);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public Map<String, Object> generarRespuesta(String prompt) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            throw new IllegalStateException(
                    "El gateway de IA no tiene logired.integraciones.ia.gateway-url configurada.");
        }

        RestClient.RequestHeadersSpec<?> request = restClient.post()
                .uri(gatewayUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", model,
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "temperature", 0.2));

        if (apiKey != null && !apiKey.isBlank()) {
            request = request.header("Authorization", "Bearer " + apiKey);
        }

        try {
            return request.retrieve().toEntity(Map.class).getBody();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "No fue posible obtener una respuesta del gateway de IA en "
                            + timeoutMillis + " ms.", exception);
        }
    }
}
