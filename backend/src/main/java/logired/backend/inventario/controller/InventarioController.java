package logired.backend.inventario.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import logired.backend.inventario.dto.ProductoStockRequest;
import logired.backend.inventario.dto.ReservaStockRequest;
import logired.backend.inventario.dto.ReservaStockResponse;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.service.InventarioCatalogoService;
import logired.backend.inventario.service.InventarioService;

/**
 * Controlador REST para el Componente Stateful (InventarioService).
 */
@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

    private final InventarioService inventarioService;
    private final InventarioCatalogoService catalogoService;

    public InventarioController(
            InventarioService inventarioService,
            InventarioCatalogoService catalogoService) {
        this.inventarioService = inventarioService;
        this.catalogoService = catalogoService;
    }

    @GetMapping
    public ResponseEntity<List<ItemInventario>> obtenerTodos() {
        return ResponseEntity.ok(inventarioService.obtenerTodos());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<?> crear(@RequestBody ProductoStockRequest request) {
        try {
            return ResponseEntity.status(201).body(catalogoService.crear(request));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody ProductoStockRequest request) {
        try {
            return catalogoService.actualizar(id, request)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and authentication.name == 'admin'")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        try {
            return catalogoService.eliminar(id)
                    ? ResponseEntity.noContent().build()
                    : ResponseEntity.notFound().build();
        } catch (IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/reservar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ReservaStockResponse> reservar(@RequestBody ReservaStockRequest request) {
        int cant = Objects.requireNonNullElse(request.getCantidad(), 1);
        ReservaStockResponse response = inventarioService.reservarStock(request.getSku(), request.getDeposito(), cant);
        if (!response.isExitoso()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/liberar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ReservaStockResponse> liberar(@RequestBody ReservaStockRequest request) {
        int cant = Objects.requireNonNullElse(request.getCantidad(), 1);
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
