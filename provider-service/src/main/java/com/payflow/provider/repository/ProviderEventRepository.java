package com.payflow.provider.repository;
import com.payflow.provider.domain.ProviderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface ProviderEventRepository extends JpaRepository<ProviderEvent, UUID> {
    Optional<ProviderEvent> findByProviderAndProviderEventId(String provider, String providerEventId);
    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
}
