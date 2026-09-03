package com.payflow.payment.repository;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByPaymentReference(String paymentReference);
    Optional<Payment> findByPaymentReferenceAndMerchantId(String paymentReference, UUID merchantId);
    Optional<Payment> findByMerchantIdAndMerchantOrderId(UUID merchantId, String merchantOrderId);
    Optional<Payment> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);
    Page<Payment> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);
    Page<Payment> findByMerchantIdAndStatusOrderByCreatedAtDesc(UUID merchantId, PaymentStatus status,
                                                                Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentReference = :reference")
    Optional<Payment> findByReferenceForUpdate(@Param("reference") String reference);
    @Query("select p from Payment p where p.status in :statuses and p.expiresAt < :now")
    List<Payment> findExpired(@Param("statuses") List<PaymentStatus> statuses,
                              @Param("now") Instant now,
                              Pageable pageable);
    long countByMerchantIdAndStatusAndCreatedAtAfter(UUID merchantId, PaymentStatus status, Instant after);
}
