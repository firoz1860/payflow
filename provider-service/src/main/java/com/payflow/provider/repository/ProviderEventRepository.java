package com.payflow.provider.repository;
import com.payflow.provider.domain.ProviderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface ProviderEventRepository extends JpaRepository<ProviderEvent, UUID> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "INSERT INTO provider_events(id,provider,provider_event_id,event_type,provider_payment_id,payload,processing_status,received_at,processed_at) VALUES (:id,:provider,:eventId,:eventType,:paymentId,CAST(:payload AS jsonb),'PROCESSED',now(),now()) ON CONFLICT (provider,provider_event_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@org.springframework.data.repository.query.Param("id") UUID id,
        @org.springframework.data.repository.query.Param("provider") String provider,
        @org.springframework.data.repository.query.Param("eventId") String eventId,
        @org.springframework.data.repository.query.Param("eventType") String eventType,
        @org.springframework.data.repository.query.Param("paymentId") String paymentId,
        @org.springframework.data.repository.query.Param("payload") String payload);
    Optional<ProviderEvent> findByProviderAndProviderEventId(String provider, String providerEventId);
    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
    List<ProviderEvent> findByProviderAndProviderPaymentIdOrderByReceivedAtDesc(String provider,
                                                                                String providerPaymentId);
    List<ProviderEvent> findByProviderPaymentIdOrderByReceivedAtDesc(String providerPaymentId);
}
