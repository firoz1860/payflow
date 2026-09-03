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
    @Query("delete from IdempotencyRecord r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
