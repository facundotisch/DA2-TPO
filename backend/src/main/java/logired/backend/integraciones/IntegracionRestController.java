package logired.backend.integraciones;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import logired.backend.ruteo.dto.RutaRequest;
import logired.backend.ruteo.dto.RutaResponse;

@RestController
@RequestMapping("/api/integraciones/rest")
public class IntegracionRestController {

    private final ModernRuteoRestClient restClient;

    public IntegracionRestController(ModernRuteoRestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Siempre responde 200 con una ruta: la del sistema moderno o, si no respondió, la local
     * (el campo "mensaje" empieza con "Fallback local:" e indica el motivo).
     * Datos inválidos: 400 en formato Problem Details.
     */
    @PostMapping("/ruteo")
    public ResponseEntity<RutaResponse> calcularRuta(@RequestBody RutaRequest request) {
        return ResponseEntity.ok(restClient.calcularRuta(request));
    }
}
