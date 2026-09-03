package com.payflow.auth.repository;
import com.payflow.auth.domain.OneTimeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {
    Optional<OneTimeToken> findByTokenHashAndPurpose(String tokenHash, OneTimeToken.Purpose purpose);
}
