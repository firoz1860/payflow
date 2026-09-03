package com.payflow.common.money;
import com.payflow.common.error.PayFlowException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class MoneyTest {
    @Test
    @DisplayName("amounts are normalised to the currency scale")
    void normalisesToCurrencyScale() {
        assertThat(Money.normalize(new BigDecimal("100"), "INR")).isEqualByComparingTo("100.00");
        assertThat(Money.normalize(new BigDecimal("100.5"), "USD")).isEqualByComparingTo("100.50");
    }
    @Test
    @DisplayName("zero-decimal currencies keep zero decimals")
    void handlesZeroDecimalCurrencies() {
        assertThat(Money.normalize(new BigDecimal("1000"), "JPY").scale()).isEqualTo(0);
    }
    @Test
    @DisplayName("excess precision is rejected rather than silently rounded")
    void rejectsExcessPrecision() {
        assertThatThrownBy(() -> Money.normalize(new BigDecimal("100.005"), "INR"))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("more precision");
    }
    @Test
    @DisplayName("percentage fees round half-up at the currency scale")
    void percentageFee() {
        assertThat(Money.percentageOf(new BigDecimal("1000.00"), new BigDecimal("2.00"), "INR"))
                .isEqualByComparingTo("20.00");
        assertThat(Money.percentageOf(new BigDecimal("999.99"), new BigDecimal("2.35"), "INR"))
                .isEqualByComparingTo("23.50");
    }
    @Test
    @DisplayName("unknown currencies are rejected")
    void rejectsUnknownCurrency() {
        assertThatThrownBy(() -> Money.normalize(BigDecimal.ONE, "XXQ"))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("Unsupported currency");
    }
    @Test
    @DisplayName("non-positive amounts are rejected")
    void rejectsNonPositive() {
        assertThatThrownBy(() -> Money.requirePositive(BigDecimal.ZERO, "amount"))
                .isInstanceOf(PayFlowException.class);
        assertThatThrownBy(() -> Money.requirePositive(new BigDecimal("-1"), "amount"))
                .isInstanceOf(PayFlowException.class);
    }
}
