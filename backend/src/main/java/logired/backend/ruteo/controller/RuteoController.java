package logired.backend.ruteo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import logired.backend.ruteo.dto.RutaRequest;
import logired.backend.ruteo.dto.RutaResponse;
import logired.backend.ruteo.service.RuteoService;

/**
 * Punto de Entrada REST para RuteoService (Principio SOA 6 - Descubribilidad)
 * Expone la funcionalidad en el Hub para ser consumida por el Frontend (React)
 * y clientes autorizados.
 */
@RestController
@RequestMapping("/api/ruteo")
@CrossOrigin(origins = "*")
public class RuteoController {

    private final RuteoService ruteoService;

    public RuteoController(RuteoService ruteoService) {
        this.ruteoService = ruteoService;
    }

    @PostMapping("/optimizar")
    public ResponseEntity<RutaResponse> optimizarRuta(@RequestBody RutaRequest request) {
        RutaResponse response = ruteoService.calcularRutaConEstrategia(request);
        return ResponseEntity.ok(response);
    }
}
