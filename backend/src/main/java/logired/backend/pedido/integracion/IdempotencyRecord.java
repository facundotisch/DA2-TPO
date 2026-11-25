package logired.backend.pedido.integracion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "rest_idempotency_records")
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String requestHash;

    @Column(nullable = false)
    private int responseStatus;

    @Column(columnDefinition = "text")
    private String responseBody;

    private String responseEtag;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String requestHash,
                             int responseStatus, String responseBody, String responseEtag) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.responseEtag = responseEtag;
        this.createdAt = LocalDateTime.now();
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getResponseEtag() {
        return responseEtag;
    }
}
