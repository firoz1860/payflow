package com.payflow.payment.service;
import com.payflow.common.security.PayFlowPrincipal;
import com.payflow.common.security.TenantGuard;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.money.Money;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.dto.CheckoutDtos;
import com.payflow.payment.dto.PaymentDtos;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.client.ProviderClient;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.Instant;
@Service
public class PaymentCheckoutService {
    private final PaymentRepository payments;
    private final ProviderClient provider;
    private final PaymentService service;
    public PaymentCheckoutService(PaymentRepository payments, ProviderClient provider, PaymentService service) {
        this.payments=payments;this.provider=provider;this.service=service;
    }
    public CheckoutDtos.Options checkout(PayFlowPrincipal principal, String reference) {
        Payment payment=owned(principal,reference);
        if (!java.util.Set.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentStatus.AUTHORIZED).contains(payment.getStatus())
                || (payment.getExpiresAt()!=null && payment.getExpiresAt().isBefore(Instant.now())))
            throw PayFlowException.conflict(ErrorCode.CONFLICT,"Payment cannot start checkout");
        CheckoutDtos.Options options=provider.checkout(payment.getProviderPaymentId());
        if (options==null || !"razorpay".equals(options.provider()) || !"TEST".equals(options.mode())
                || options.keyId()==null || !options.keyId().startsWith("rzp_test_") || !payment.getProviderPaymentId().equals(options.orderId())) throw unavailable();
        amount(payment,options.amountMinor(),options.currency());
        return options;
    }
    public PaymentDtos.PaymentResponse verify(PayFlowPrincipal principal,String reference,CheckoutDtos.VerificationRequest evidence) {
        Payment payment=owned(principal,reference);
        if (!payment.getProviderPaymentId().equals(evidence.orderId())) throw PayFlowException.forbidden("Checkout order does not match the stored payment");
        CheckoutDtos.VerifiedPayment verified=provider.verifyCheckout(evidence);
        if (verified==null || !evidence.paymentId().equals(verified.paymentId())) throw unavailable();
        apply(payment,verified);
        return service.get(principal,reference);
    }
    public PaymentDtos.PaymentResponse reconcile(PayFlowPrincipal principal,String reference) {
        Payment payment=owned(principal,reference);
        CheckoutDtos.VerifiedPayment verified=provider.reconcileCheckout(payment.getProviderPaymentId());
        if (verified!=null) apply(payment,verified);
        return service.get(principal,reference);
    }
    private void apply(Payment payment,CheckoutDtos.VerifiedPayment verified) {
        if (!payment.getProviderPaymentId().equals(verified.orderId())) throw PayFlowException.forbidden("Provider order mismatch");
        amount(payment,verified.amountMinor(),verified.currency());
        service.applyVerifiedProviderStatus("razorpay",verified.orderId(),verified.paymentId(),verified.amountMinor(),verified.currency(),verified.status(),null,null,null,null,null,null);
    }
    private Payment owned(PayFlowPrincipal principal,String reference) {
        Payment payment=payments.findByPaymentReferenceAndMerchantId(reference,TenantGuard.requireMerchant(principal)).orElseThrow(()->PayFlowException.notFound("Payment not found"));
        if (!"razorpay".equals(payment.getProvider()) || payment.getEnvironment()!=Payment.Environment.TEST || payment.getProviderPaymentId()==null)
            throw PayFlowException.conflict(ErrorCode.CONFLICT,"Payment does not use Razorpay TEST checkout");
        return payment;
    }
    private void amount(Payment payment,long minor,String currency) {
        if (minor<=0 || !payment.getCurrency().equals(currency) || BigDecimal.valueOf(minor).movePointLeft(Money.scaleOf(currency)).compareTo(payment.getAmount())!=0)
            throw PayFlowException.forbidden("Provider amount or currency mismatch");
    }
    private PayFlowException unavailable() { return new PayFlowException(ErrorCode.PROVIDER_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE,"Provider verification unavailable; retry verification"); }
}
