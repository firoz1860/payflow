package com.payflow.payment.domain;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class PaymentTest {
    private Payment capturedPayment(String amount) {
        Payment payment = new Payment("pay_test", UUID.randomUUID(), null, "order-1",
                new BigDecimal(amount), "INR", Payment.Environment.TEST, null, null,
                Instant.now().plus(30, ChronoUnit.MINUTES));
        payment.transitionTo(PaymentStatus.PENDING);
        payment.transitionTo(PaymentStatus.CAPTURED);
        return payment;
    }
    @Test
    @DisplayName("a captured payment cannot be dragged back to pending by a late webhook")
    void rejectsBackwardTransition() {
        Payment payment = capturedPayment("1000.00");
        assertThatThrownBy(() -> payment.transitionTo(PaymentStatus.PENDING))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("Illegal payment transition");
    }
    @Test
    @DisplayName("re-applying the current status is a no-op, so duplicate webhooks are harmless")
    void sameStatusIsNoOp() {
        Payment payment = capturedPayment("1000.00");
        payment.transitionTo(PaymentStatus.CAPTURED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }
    @Test
    @DisplayName("partial refunds accumulate and the status tracks them")
    void partialRefundsAccumulate() {
        Payment payment = capturedPayment("1000.00");
        payment.registerRefund(new BigDecimal("300.00"));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.refundableAmount()).isEqualByComparingTo("700.00");
        payment.registerRefund(new BigDecimal("700.00"));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.refundableAmount()).isEqualByComparingTo("0.00");
    }
    @Test
    @DisplayName("over-refunding is refused by the aggregate itself")
    void overRefundRejected() {
        Payment payment = capturedPayment("1000.00");
        payment.registerRefund(new BigDecimal("700.00"));
        assertThatThrownBy(() -> payment.registerRefund(new BigDecimal("700.00")))
                .isInstanceOf(PayFlowException.class)
                .extracting(ex -> ((PayFlowException) ex).getCode())
                .isEqualTo(ErrorCode.REFUND_AMOUNT_EXCEEDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("700.00");
    }
    @Test
    @DisplayName("a failed payment is not refundable")
    void failedPaymentNotRefundable() {
        Payment payment = new Payment("pay_x", UUID.randomUUID(), null, null,
                new BigDecimal("100.00"), "INR", Payment.Environment.TEST, null, null,
                Instant.now().plus(30, ChronoUnit.MINUTES));
        payment.markFailed("card_declined", "declined");
        assertThatThrownBy(() -> payment.registerRefund(new BigDecimal("10.00")))
                .isInstanceOf(PayFlowException.class)
                .extracting(ex -> ((PayFlowException) ex).getCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_REFUNDABLE);
    }
    @Test
    @DisplayName("terminal states allow no further transitions")
    void terminalStatesAreFinal() {
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.CAPTURED)).isFalse();
        assertThat(PaymentStatus.CANCELLED.canTransitionTo(PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStatus.REFUNDED.canTransitionTo(PaymentStatus.PARTIALLY_REFUNDED)).isFalse();
    }
}
