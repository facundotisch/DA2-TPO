package logired.backend.inventario.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import logired.backend.inventario.dto.ReservaStockRequest;
import logired.backend.inventario.dto.ReservaStockResponse;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.service.InventarioService;

/**
 * Controlador REST para el Componente Stateful (InventarioService).
 */
@RestController
@RequestMapping("/api/inventario")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class InventarioController {

    private final InventarioService inventarioService;

    public InventarioController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping
    public ResponseEntity<List<ItemInventario>> obtenerTodos() {
        return ResponseEntity.ok(inventarioService.obtenerTodos());
    }

    @PostMapping("/reservar")
    public ResponseEntity<ReservaStockResponse> reservar(@RequestBody ReservaStockRequest request) {
        int cant = (request.getCantidad() != null) ? request.getCantidad() : 1;
        ReservaStockResponse response = inventarioService.reservarStock(request.getSku(), request.getDeposito(), cant);
        if (!response.isExitoso()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/liberar")
    public ResponseEntity<ReservaStockResponse> liberar(@RequestBody ReservaStockRequest request) {
        int cant = (request.getCantidad() != null) ? request.getCantidad() : 1;
        ReservaStockResponse response = inventarioService.liberarStock(request.getSku(), request.getDeposito(), cant);
        if (!response.isExitoso()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sesion")
    public ResponseEntity<Map<String, Object>> estadoSesion() {
        Map<String, Object> data = new HashMap<>();
        data.put("idSesionConversacional", inventarioService.getIdSesionConversacional());
        data.put("reservasActivas", inventarioService.obtenerReservasActivasSesion());
        data.put("tipoComponente", "Stateful (@SessionScope)");
        return ResponseEntity.ok(data);
    }
}
