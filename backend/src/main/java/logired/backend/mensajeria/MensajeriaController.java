package logired.backend.mensajeria;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mensajeria")
public class MensajeriaController {

    private final PedidoEventListeners eventListeners;

    public MensajeriaController(PedidoEventListeners eventListeners) {
        this.eventListeners = eventListeners;
    }

    @GetMapping("/eventos")
    public ResponseEntity<List<EventoLogDto>> obtenerEventos() {
        return ResponseEntity.ok(eventListeners.getEventosRecientes());
    }
}
