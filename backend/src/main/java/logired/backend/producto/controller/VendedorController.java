package logired.backend.producto.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import logired.backend.producto.dto.VendedorResponse;
import logired.backend.producto.service.VendedorService;

@RestController
@RequestMapping("/api/vendedores")
public class VendedorController {

    private final VendedorService vendedorService;

    public VendedorController(VendedorService vendedorService) {
        this.vendedorService = vendedorService;
    }

    @GetMapping
    public ResponseEntity<List<VendedorResponse>> obtenerTodos() {
        return ResponseEntity.ok(vendedorService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendedorResponse> obtenerPorId(@PathVariable Long id) {
        return vendedorService.obtenerPorId(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}