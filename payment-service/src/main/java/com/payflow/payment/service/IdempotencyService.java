package com.payflow.payment.service;
import com.payflow.common.crypto.Hashing;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.payment.domain.IdempotencyRecord;
import com.payflow.payment.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
@Service
public class IdempotencyService {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration STALE_AFTER = Duration.ofMinutes(2);
    private static final Duration RETENTION = Duration.ofHours(24);
    private final IdempotencyRecordRepository repository;
    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }
    public sealed interface Claim permits Claim.Acquired, Claim.Replay {
        record Acquired(UUID recordId) implements Claim {
        }
        record Replay(int responseCode, String responseBody, String resourceId) implements Claim {
        }
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(UUID merchantId, String idempotencyKey, String endpoint, String requestBody) {
        validateKey(idempotencyKey);
        String requestHash = Hashing.sha256Hex(endpoint + "|" + (requestBody == null ? "" : requestBody));
        Optional<IdempotencyRecord> existing =
                repository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
        if (existing.isPresent()) {
            return evaluate(existing.get(), requestHash);
        }
        try {
            IdempotencyRecord record = new IdempotencyRecord(merchantId, idempotencyKey, endpoint,
                    requestHash, Instant.now().plus(RETENTION));
            repository.saveAndFlush(record);
            return new Claim.Acquired(record.getId());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Idempotency key {} lost the insert race for merchant {}", idempotencyKey, merchantId);
            IdempotencyRecord winner = repository
                    .findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                    .orElseThrow(() -> PayFlowException.conflict(ErrorCode.CONFLICT,
                            "Concurrent request conflict, please retry"));
            return evaluate(winner, requestHash);
        }
    }
    private Claim evaluate(IdempotencyRecord record, String requestHash) {
        if (!Hashing.constantTimeEquals(record.getRequestHash(), requestHash)) {
            throw PayFlowException.conflict(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used with a different request body");
        }
        return switch (record.getStatus()) {
            case COMPLETED -> new Claim.Replay(
                    record.getResponseCode() == null ? 200 : record.getResponseCode(),
                    record.getResponseBody(), record.getResourceId());
            case FAILED -> {
                record.renew(requestHash, Instant.now().plus(RETENTION));
                yield new Claim.Acquired(record.getId());
            }
            case IN_PROGRESS -> {
                if (record.isStale(STALE_AFTER)) {
                    log.warn("Taking over stale idempotency claim {}", record.getId());
                    record.renew(requestHash, Instant.now().plus(RETENTION));
                    yield new Claim.Acquired(record.getId());
                }
                throw PayFlowException.conflict(ErrorCode.CONFLICT,
                        "A request with this Idempotency-Key is currently being processed");
            }
        };
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID recordId, String resourceId, int responseCode, String responseBody) {
        repository.findById(recordId)
                .ifPresent(record -> record.complete(resourceId, responseCode, responseBody));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID recordId) {
        repository.findById(recordId).ifPresent(IdempotencyRecord::fail);
    }
    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "The Idempotency-Key header is required for this operation");
        }
        if (key.length() > 255) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key must be 255 characters or fewer");
        }
    }
    @Scheduled(cron = "${payflow.idempotency.cleanup-cron:0 15 * * * *}")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteExpired(Instant.now());
        if (removed > 0) {
            log.info("Purged {} expired idempotency records", removed);
        }
    }
}
