package logired.backend.pedido.integracion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public String hash(Object value) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException | java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No fue posible calcular el hash de idempotencia.", exception);
        }
    }

    public Optional<IdempotencyRecord> find(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    public void validate(String idempotencyKey, String requestHash) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        repository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IllegalArgumentException(
                        "La Idempotency-Key ya fue usada con un cuerpo de solicitud diferente.");
            }
        });
    }

    @Transactional
    public IdempotencyRecord save(String idempotencyKey, String requestHash,
                                  int status, Object body, String etag) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        try {
            String responseBody = objectMapper.writeValueAsString(body);
            return repository.saveAndFlush(new IdempotencyRecord(
                    idempotencyKey, requestHash, status, responseBody, etag));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No fue posible guardar la respuesta idempotente.", exception);
        }
    }
}
