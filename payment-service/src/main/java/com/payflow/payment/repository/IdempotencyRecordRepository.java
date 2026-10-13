package com.payflow.payment.repository;
import com.payflow.payment.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records (id, merchant_id, idempotency_key, endpoint, request_hash, status, created_at, expires_at)
            VALUES (:id, :merchantId, :key, :endpoint, :hash, 'IN_PROGRESS', CURRENT_TIMESTAMP, :expiresAt)
            ON CONFLICT (merchant_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertClaim(@Param("id") UUID id, @Param("merchantId") UUID merchantId,
                    @Param("key") String key, @Param("endpoint") String endpoint,
                    @Param("hash") String hash, @Param("expiresAt") Instant expiresAt);
    @Modifying
    @Query("delete from IdempotencyRecord r where r.expiresAt < :now and r.status = :status")
    int deleteExpired(@Param("now") Instant now, @Param("status") IdempotencyRecord.Status status);
}
