package com.payflow.payment.repository;
import com.payflow.payment.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberAsc(UUID paymentId);
    Optional<PaymentAttempt> findFirstByPaymentIdOrderByAttemptNumberDesc(UUID paymentId);
    Optional<PaymentAttempt> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);
    int countByPaymentId(UUID paymentId);
}
