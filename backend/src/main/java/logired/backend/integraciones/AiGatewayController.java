package logired.backend.integraciones;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ia")
public class AiGatewayController {

    private final AiGatewayClient aiGatewayClient;

    public AiGatewayController(AiGatewayClient aiGatewayClient) {
        this.aiGatewayClient = aiGatewayClient;
    }

    @PostMapping("/generar")
    public ResponseEntity<Map<String, Object>> generar(@RequestBody PromptRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            throw new IllegalArgumentException("El prompt de IA es obligatorio.");
        }
        return ResponseEntity.ok(aiGatewayClient.generarRespuesta(request.prompt()));
    }

    public record PromptRequest(String prompt) {
    }
}
