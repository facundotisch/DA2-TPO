package logired.backend.integraciones;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import logired.backend.ruteo.dto.RutaRequest;

import java.util.Map;

@RestController
@RequestMapping("/api/integraciones/rest")
public class IntegracionRestController {

    private final ModernRuteoRestClient restClient;

    public IntegracionRestController(ModernRuteoRestClient restClient) {
        this.restClient = restClient;
    }

    @PostMapping("/ruteo")
    public ResponseEntity<?> calcularRuta(@RequestBody RutaRequest request) {
        try {
            return ResponseEntity.ok(restClient.calcularRuta(request));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", exception.getMessage()));
        }
    }
}
