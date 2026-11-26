package logired.backend.integraciones;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ia")
public class AiGatewayController {

    private final AiGatewayClient aiGatewayClient;

    public AiGatewayController(AiGatewayClient aiGatewayClient) {
        this.aiGatewayClient = aiGatewayClient;
    }

    /**
     * Devuelve 200 con {"respuesta", "fuente", "degradada"}. Si el proveedor de IA está caído
     * o lento, "degradada" es true y la respuesta es un mensaje de respaldo: el flujo no se corta.
     * Prompt vacío o de más de 2000 caracteres: 400.
     */
    @PostMapping("/generar")
    public ResponseEntity<RespuestaIa> generar(@RequestBody PromptRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El prompt de IA es obligatorio.");
        }
        return ResponseEntity.ok(aiGatewayClient.generarRespuesta(request.prompt()));
    }

    public record PromptRequest(String prompt) {
    }
}
