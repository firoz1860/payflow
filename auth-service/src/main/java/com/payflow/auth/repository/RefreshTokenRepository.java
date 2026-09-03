package com.payflow.auth.repository;
import com.payflow.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    @Modifying
    @Query("update RefreshToken t set t.status = :revoked, t.revokedAt = :now "
            + "where t.familyId = :familyId and t.status <> :revoked")
    int revokeFamily(@Param("familyId") UUID familyId,
                     @Param("revoked") RefreshToken.Status revoked,
                     @Param("now") Instant now);
    @Modifying
    @Query("update RefreshToken t set t.status = :revoked, t.revokedAt = :now "
            + "where t.userId = :userId and t.status <> :revoked")
    int revokeAllForUser(@Param("userId") UUID userId,
                         @Param("revoked") RefreshToken.Status revoked,
                         @Param("now") Instant now);
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
