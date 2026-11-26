package logired.backend.pedido.controller;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import logired.backend.common.exception.ConflictoException;
import logired.backend.common.exception.PrecondicionFallidaException;
import logired.backend.pedido.dto.PedidoActualizacionRequest;
import logired.backend.pedido.dto.PedidoCreacionRequest;
import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.integracion.IdempotencyRecord;
import logired.backend.pedido.integracion.IdempotencyService;
import logired.backend.pedido.service.PedidoService;

/**
 * API REST de pedidos.
 *
 * - Errores: se lanzan excepciones y ProblemDetailsHandler las traduce a Problem Details.
 * - Idempotency-Key (POST, PUT, PATCH, DELETE): misma clave y mismo cuerpo devuelve la
 *   respuesta original; otro cuerpo devuelve 409; una solicitud simultánea con la misma
 *   clave devuelve 409 sin crear un segundo recurso.
 * - If-Match (PUT, PATCH): si el ETag enviado no coincide con el actual devuelve 412.
 */
@RestController
@RequestMapping("/api/pedido")
public class PedidoController {

    private final PedidoService pedidoService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaccion;

    public PedidoController(PedidoService pedidoService,
                            IdempotencyService idempotencyService,
                            ObjectMapper objectMapper,
                            PlatformTransactionManager transactionManager) {
        this.pedidoService = pedidoService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    // GET /api/pedido
    @GetMapping
    public ResponseEntity<List<Pedido>> obtenerTodos(
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        List<Pedido> pedidos = pedidoService.obtenerTodos();
        String etag = calcularEtag(pedidos);
        if (coincideEtag(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(pedidos);
    }

    // GET /api/pedido/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Pedido> obtenerPorId(
            @PathVariable Long id,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return pedidoService.obtenerPorId(id)
                .map(pedido -> {
                    String etag = calcularEtag(pedido);
                    if (coincideEtag(ifNoneMatch, etag)) {
                        return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).<Pedido>build();
                    }
                    return ResponseEntity.ok().eTag(etag).body(pedido);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/pedido
    @PostMapping
    public ResponseEntity<?> crear(
            @RequestBody PedidoCreacionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Principal principal,
            HttpServletRequest httpRequest) {
        return ejecutarIdempotente(idempotencyKey, principal, httpRequest, idempotencyService.hash(request), () -> {
            Pedido creado = esPedidoLegacy(request)
                    ? pedidoService.crear(pedidoLegacy(request))
                    : pedidoService.crearDesdeCatalogo(request);
            return ResponseEntity.ok().eTag(calcularEtag(creado)).body(creado);
        });
    }

    // PUT /api/pedido/{id}
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @PathVariable Long id,
            @RequestBody PedidoActualizacionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            Principal principal,
            HttpServletRequest httpRequest) {
        return ejecutarIdempotente(idempotencyKey, principal, httpRequest, idempotencyService.hash(request), () ->
                pedidoService.actualizarDesdeCatalogo(id, request, exigirIfMatch(ifMatch))
                        .<ResponseEntity<?>>map(pedido -> ResponseEntity.ok().eTag(calcularEtag(pedido)).body(pedido))
                        .orElseGet(() -> ResponseEntity.notFound().build()));
    }

    // DELETE /api/pedido/{id}
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and authentication.name == 'admin'")
    public ResponseEntity<?> eliminar(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Principal principal,
            HttpServletRequest httpRequest) {
        return ejecutarIdempotente(idempotencyKey, principal, httpRequest, "DELETE:" + id, () ->
                pedidoService.eliminar(id)
                        ? ResponseEntity.noContent().build()
                        : ResponseEntity.notFound().build());
    }

    // PATCH /api/pedido/{id}/estado?accion=ASIGNAR|EN_RUTA|ENTREGAR|FALLAR
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<?> actualizarEstado(
            @PathVariable Long id,
            @RequestParam String accion,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            Principal principal,
            HttpServletRequest httpRequest) {
        return ejecutarIdempotente(idempotencyKey, principal, httpRequest, "PATCH:" + id + ":" + accion, () -> {
            Pedido actualizado = pedidoService.cambiarEstado(id, accion, exigirIfMatch(ifMatch));
            return ResponseEntity.ok().eTag(calcularEtag(actualizado)).body(actualizado);
        });
    }

    /**
     * Ejecuta una operación mutante dentro de una transacción. Si llega Idempotency-Key,
     * reserva la clave antes de operar y guarda la respuesta en la misma transacción.
     */
    private ResponseEntity<?> ejecutarIdempotente(String idempotencyKey, Principal principal,
                                                  HttpServletRequest httpRequest, String requestHash,
                                                  Supplier<ResponseEntity<?>> operacion) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return transaccion.execute(status -> operacion.get());
        }

        String clave = idempotencyService.claveConAlcance(
                principal == null ? null : principal.getName(),
                httpRequest.getMethod(), httpRequest.getRequestURI(), idempotencyKey);
        IdempotencyService.Reserva reserva = idempotencyService.reservar(clave, requestHash);
        switch (reserva.estado()) {
            case CONFLICTO:
                throw new ConflictoException("La Idempotency-Key ya fue usada con un cuerpo diferente.");
            case EN_CURSO:
                throw new ConflictoException("Ya hay una solicitud en curso con esta Idempotency-Key. Reintentá en unos segundos.");
            case REPETIDA:
                return reproducir(reserva.registro());
            default:
                break;
        }

        try {
            return transaccion.execute(status -> {
                ResponseEntity<?> respuesta = operacion.get();
                idempotencyService.completar(clave, respuesta);
                return respuesta;
            });
        } catch (RuntimeException exception) {
            idempotencyService.liberar(clave);
            throw exception;
        }
    }

    private ResponseEntity<?> reproducir(IdempotencyRecord record) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Replayed", "true");
        if (record.getResponseEtag() != null) {
            headers.setETag(record.getResponseEtag());
        }
        if (record.getResponseBody() == null) {
            return ResponseEntity.status(record.getResponseStatus()).headers(headers).build();
        }
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return ResponseEntity.status(record.getResponseStatus()).headers(headers).body(record.getResponseBody());
    }

    /**
     * Precondición If-Match. Se evalúa dentro de la transacción del servicio, con el pedido
     * recién leído, así que no hay ventana entre la verificación y la escritura.
     * Sin cabecera (o con "*") no se exige nada, para no romper clientes existentes.
     */
    private Consumer<Pedido> exigirIfMatch(String ifMatch) {
        return pedido -> {
            if (ifMatch == null || ifMatch.isBlank() || "*".equals(ifMatch.trim())) {
                return;
            }
            if (!coincideEtag(ifMatch, calcularEtag(pedido))) {
                throw new PrecondicionFallidaException(
                        "El pedido cambió desde que lo consultaste (ETag desactualizado). Volvé a obtenerlo.");
            }
        };
    }

    private boolean coincideEtag(String cabecera, String etagActual) {
        if (cabecera == null || cabecera.isBlank()) {
            return false;
        }
        return Arrays.stream(cabecera.split(","))
                .map(String::trim)
                .map(valor -> valor.startsWith("W/") ? valor.substring(2) : valor)
                .anyMatch(valor -> valor.equals(etagActual) || valor.equals("*"));
    }

    private boolean esPedidoLegacy(PedidoCreacionRequest request) {
        return !request.tieneItemsDeCatalogo()
                && request.getDescripcion() != null
                && request.getDescripcion().contains("SKU:");
    }

    private Pedido pedidoLegacy(PedidoCreacionRequest request) {
        Pedido legacy = new Pedido();
        legacy.setDescripcion(request.getDescripcion());
        legacy.setClienteNombre(request.getClienteNombre());
        legacy.setDireccionEntrega(request.getDireccionEntrega());
        return legacy;
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
