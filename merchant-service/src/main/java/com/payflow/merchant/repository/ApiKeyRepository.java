package com.payflow.merchant.repository;
import com.payflow.merchant.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByLookupHash(String lookupHash);
    Optional<ApiKey> findByKeyId(String keyId);
    List<ApiKey> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
    long countByMerchantIdAndStatus(UUID merchantId, ApiKey.Status status);
    @Modifying
    @Query("update ApiKey k set k.lastUsedAt = :when where k.id = :id")
    void updateLastUsedAt(@Param("id") UUID id, @Param("when") Instant when);
    @Modifying
    @Query("update ApiKey k set k.status = :revoked, k.revokedAt = :now "
            + "where k.merchantId = :merchantId and k.status = :active")
    int revokeAllForMerchant(@Param("merchantId") UUID merchantId,
                             @Param("active") ApiKey.Status active,
                             @Param("revoked") ApiKey.Status revoked,
                             @Param("now") Instant now);
}
