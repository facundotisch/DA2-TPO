package logired.backend.pedido.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import logired.backend.pedido.dto.PedidoCreacionRequest;
import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.service.PedidoService;

@RestController
@RequestMapping("/api/pedido")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    // GET /api/pedido
    @GetMapping
    public ResponseEntity<List<Pedido>> obtenerTodos() {
        return ResponseEntity.ok(pedidoService.obtenerTodos());
    }

    // GET /api/pedido/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Pedido> obtenerPorId(@PathVariable Long id) {

        return pedidoService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/pedido
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody PedidoCreacionRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "La solicitud del pedido es obligatoria."));
        }

        try {
            if (!request.tieneItemsDeCatalogo()
                    && request.getDescripcion() != null
                    && request.getDescripcion().contains("SKU:")) {
                Pedido legacy = new Pedido(request.getDescripcion(), request.getEstado(), request.getTotal());
                legacy.setClienteNombre(request.getClienteNombre());
                legacy.setDireccionEntrega(request.getDireccionEntrega());
                return ResponseEntity.ok(pedidoService.crear(legacy));
            }
            return ResponseEntity.ok(pedidoService.crearDesdeCatalogo(request));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    // PUT /api/pedido/{id}
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @PathVariable Long id,
            @RequestBody PedidoCreacionRequest request) {

        try {
            return pedidoService.actualizarDesdeCatalogo(id, request)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    // DELETE /api/pedido/{id}
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and authentication.name == 'admin'")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {

        if (!pedidoService.eliminar(id)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<Pedido> actualizarEstado(@PathVariable Long id, @RequestParam String accion) {
        Pedido actualizado = pedidoService.cambiarEstado(id, accion);
        return ResponseEntity.ok(actualizado);
    }
}