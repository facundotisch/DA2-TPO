package logired.backend.pedido.integracion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Manejo de la cabecera Idempotency-Key.
 *
 * Flujo para una clave nueva:
 *   1. reservar(): inserta la clave con estado EN_CURSO en una transacción propia que se
 *      confirma enseguida. La restricción única garantiza que, si llegan dos solicitudes
 *      simultáneas con la misma clave, solo una pueda reservarla.
 *   2. El controlador ejecuta la operación y llama a completar() dentro de la misma
 *      transacción que la operación: o se guardan ambas cosas o ninguna.
 *   3. Si la operación falla, liberar() borra la reserva para que el cliente pueda reintentar.
 *
 * La clave se guarda con alcance: hash de (usuario, método, ruta, clave). Así otro usuario
 * que reutilice la misma clave no recibe la respuesta de un pedido ajeno.
 */
@Service
public class IdempotencyService {

    /** Código interno que marca una reserva sin respuesta todavía. */
    static final int EN_CURSO = 0;

    public enum Estado { NUEVA, REPETIDA, CONFLICTO, EN_CURSO }

    public record Reserva(Estado estado, IdempotencyRecord registro) {
    }

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaccionNueva;

    public IdempotencyService(IdempotencyRecordRepository repository,
                              ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.transaccionNueva = new TransactionTemplate(transactionManager);
        this.transaccionNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String hash(Object value) {
        try {
            return sha256(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No fue posible calcular el hash de idempotencia.", exception);
        }
    }

    public String claveConAlcance(String usuario, String metodo, String ruta, String idempotencyKey) {
        String compuesta = String.join("\n",
                usuario == null ? "anonimo" : usuario, metodo, ruta, idempotencyKey.trim());
        return sha256(compuesta.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<IdempotencyRecord> find(String claveConAlcance) {
        if (claveConAlcance == null || claveConAlcance.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIdempotencyKey(claveConAlcance);
    }

    public Reserva reservar(String claveConAlcance, String requestHash) {
        try {
            return transaccionNueva.execute(status -> {
                Optional<IdempotencyRecord> existente = repository.findByIdempotencyKey(claveConAlcance);
                if (existente.isPresent()) {
                    return clasificar(existente.get(), requestHash);
                }
                IdempotencyRecord reserva = repository.saveAndFlush(
                        new IdempotencyRecord(claveConAlcance, requestHash, EN_CURSO, null, null));
                return new Reserva(Estado.NUEVA, reserva);
            });
        } catch (DataIntegrityViolationException carrera) {
            // Otra solicitud con la misma clave la reservó entre nuestra consulta y el insert.
            return new Reserva(Estado.EN_CURSO, null);
        }
    }

    /** Guarda la respuesta. Se llama dentro de la transacción de la operación. */
    @Transactional
    public void completar(String claveConAlcance, ResponseEntity<?> respuesta) {
        IdempotencyRecord registro = repository.findByIdempotencyKey(claveConAlcance)
                .orElseThrow(() -> new IllegalStateException("La reserva de idempotencia no existe."));
        try {
            String cuerpo = respuesta.getBody() == null ? null : objectMapper.writeValueAsString(respuesta.getBody());
            registro.completar(respuesta.getStatusCode().value(), cuerpo, respuesta.getHeaders().getETag());
            repository.save(registro);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No fue posible guardar la respuesta idempotente.", exception);
        }
    }

    /** Borra una reserva cuya operación falló, para permitir reintentar con la misma clave. */
    public void liberar(String claveConAlcance) {
        transaccionNueva.executeWithoutResult(status -> repository.findByIdempotencyKey(claveConAlcance)
                .filter(registro -> registro.getResponseStatus() == EN_CURSO)
                .ifPresent(repository::delete));
    }

    private Reserva clasificar(IdempotencyRecord registro, String requestHash) {
        if (!registro.getRequestHash().equals(requestHash)) {
            return new Reserva(Estado.CONFLICTO, registro);
        }
        if (registro.getResponseStatus() == EN_CURSO) {
            return new Reserva(Estado.EN_CURSO, registro);
        }
        return new Reserva(Estado.REPETIDA, registro);
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.", exception);
        }
    }
}
