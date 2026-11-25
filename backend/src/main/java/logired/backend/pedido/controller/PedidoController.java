package logired.backend.pedido.controller;

import java.util.List;
import java.util.Map;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import logired.backend.pedido.integracion.IdempotencyRecord;
import logired.backend.pedido.integracion.IdempotencyService;

@RestController
@RequestMapping("/api/pedido")
public class PedidoController {

    private final PedidoService pedidoService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public PedidoController(PedidoService pedidoService,
                            IdempotencyService idempotencyService,
                            ObjectMapper objectMapper) {
        this.pedidoService = pedidoService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    // GET /api/pedido
    @GetMapping
    public ResponseEntity<List<Pedido>> obtenerTodos(
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = "If-None-Match", required = false) String ifNoneMatch) {
        List<Pedido> pedidos = pedidoService.obtenerTodos();
        String etag = calcularEtag(pedidos);
        if (etag.equals(ifNoneMatch) || "\"*\"".equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok().eTag(etag).body(pedidos);
    }

    // GET /api/pedido/{id}
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = "If-None-Match", required = false) String ifNoneMatch) {

        return pedidoService.obtenerPorId(id)
                .map(pedido -> {
                    String etag = calcularEtag(pedido);
                    if (etag.equals(ifNoneMatch) || "\"*\"".equals(ifNoneMatch)) {
                        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                                .eTag(etag)
                                .build();
                    }
                    return ResponseEntity.ok().eTag(etag).body(pedido);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/pedido
    @PostMapping
    public ResponseEntity<?> crear(
            @RequestBody PedidoCreacionRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "La solicitud del pedido es obligatoria."));
        }

        try {
            String requestHash = idempotencyService.hash(request);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                var existente = idempotencyService.find(idempotencyKey);
                if (existente.isPresent()) {
                    if (!existente.get().getRequestHash().equals(requestHash)) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                                "error", "La Idempotency-Key ya fue usada con un cuerpo diferente."));
                    }
                    return replay(existente.get());
                }
            }

            Pedido creado;
            if (!request.tieneItemsDeCatalogo()
                    && request.getDescripcion() != null
                    && request.getDescripcion().contains("SKU:")) {
                Pedido legacy = new Pedido(request.getDescripcion(), request.getEstado(), request.getTotal());
                legacy.setClienteNombre(request.getClienteNombre());
                legacy.setDireccionEntrega(request.getDireccionEntrega());
                creado = pedidoService.crear(legacy);
            } else {
                creado = pedidoService.crearDesdeCatalogo(request);
            }
            String etag = calcularEtag(creado);
            idempotencyService.save(
                    idempotencyKey, requestHash, HttpStatus.OK.value(),
                    creado, etag);
            return ResponseEntity.ok().eTag(etag).body(creado);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    // PUT /api/pedido/{id}
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @PathVariable Long id,
            @RequestBody PedidoCreacionRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = "Idempotency-Key", required = false) String idempotencyKey) {

        try {
            String requestHash = idempotencyService.hash(request);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                var existente = idempotencyService.find(idempotencyKey);
                if (existente.isPresent()) {
                    if (!existente.get().getRequestHash().equals(requestHash)) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                                "error", "La Idempotency-Key ya fue usada con un cuerpo diferente."));
                    }
                    return replay(existente.get());
                }
            }

            var actualizado = pedidoService.actualizarDesdeCatalogo(id, request);
            if (actualizado.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Pedido pedido = actualizado.get();
            String etag = calcularEtag(pedido);
            idempotencyService.save(
                    idempotencyKey, requestHash, HttpStatus.OK.value(),
                    pedido, etag);
            return ResponseEntity.ok().eTag(etag).body(pedido);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    // DELETE /api/pedido/{id}
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and authentication.name == 'admin'")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = "Idempotency-Key", required = false) String idempotencyKey) {

        String requestHash = "DELETE:" + id;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existente = idempotencyService.find(idempotencyKey);
            if (existente.isPresent()) {
                if (!existente.get().getRequestHash().equals(requestHash)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).build();
                }
                return ResponseEntity.status(existente.get().getResponseStatus())
                        .headers(existingIdempotencyHeaders(existente.get()))
                        .build();
            }
        }

        if (!pedidoService.eliminar(id)) {
            return ResponseEntity.notFound().build();
        }

        idempotencyService.save(
                idempotencyKey, requestHash, HttpStatus.NO_CONTENT.value(), null, null);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<Pedido> actualizarEstado(
            @PathVariable Long id,
            @RequestParam String accion,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = "Idempotency-Key", required = false) String idempotencyKey) {
        String requestHash = "PATCH:" + id + ":" + accion;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existente = idempotencyService.find(idempotencyKey);
            if (existente.isPresent()) {
                if (!existente.get().getRequestHash().equals(requestHash)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).build();
                }
                try {
                    Pedido pedido = objectMapper.readValue(
                            existente.get().getResponseBody(), Pedido.class);
                    return ResponseEntity.ok()
                            .eTag(existente.get().getResponseEtag())
                            .header("Idempotency-Replayed", "true")
                            .body(pedido);
                } catch (JsonProcessingException exception) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            }
        }
        Pedido actualizado = pedidoService.cambiarEstado(id, accion);
        String etag = calcularEtag(actualizado);
        idempotencyService.save(idempotencyKey, requestHash,
                HttpStatus.OK.value(), actualizado, etag);
        return ResponseEntity.ok().eTag(etag).body(actualizado);
    }

    private ResponseEntity<?> replay(IdempotencyRecord record) {
        try {
            JsonNode body = record.getResponseBody() == null
                    ? null
                    : objectMapper.readTree(record.getResponseBody());
            HttpHeaders headers = new HttpHeaders();
            if (record.getResponseEtag() != null) {
                headers.setETag(record.getResponseEtag());
            }
            headers.set("Idempotency-Replayed", "true");
            return ResponseEntity.status(record.getResponseStatus())
                    .headers(headers)
                    .body(body);
        } catch (JsonProcessingException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("error", "No fue posible reproducir la respuesta idempotente."));
        }
    }

    private HttpHeaders existingIdempotencyHeaders(IdempotencyRecord record) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Replayed", "true");
        if (record.getResponseEtag() != null) {
            headers.setETag(record.getResponseEtag());
        }
        return headers;
    }

    private String calcularEtag(Object value) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(value);
            String hash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload));
            return "\"" + hash + "\"";
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No fue posible calcular el ETag.", exception);
        }
    }
}