package com.payflow.common.money;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
public final class Money {
    private Money() {
    }
    public static int scaleOf(String currencyCode) {
        try {
            int digits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
            return digits < 0 ? 2 : digits;
        } catch (IllegalArgumentException ex) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Unsupported currency: " + currencyCode);
        }
    }
    public static BigDecimal normalize(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED, "Amount is required");
        }
        int scale = scaleOf(currencyCode);
        try {
            return amount.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Amount has more precision than " + currencyCode + " allows (max " + scale + " decimals)");
        }
    }
    public static void requirePositive(BigDecimal amount, String field) {
        if (amount == null || amount.signum() <= 0) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED, field + " must be greater than zero");
        }
    }
    public static void requireSameCurrency(String a, String b) {
        if (a == null || !a.equalsIgnoreCase(b)) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Currency mismatch: " + a + " vs " + b);
        }
    }
    public static BigDecimal percentageOf(BigDecimal amount, BigDecimal percent, String currencyCode) {
        return amount.multiply(percent)
                .divide(BigDecimal.valueOf(100), scaleOf(currencyCode), RoundingMode.HALF_UP);
    }
    public static boolean isZero(BigDecimal amount) {
        return amount == null || amount.signum() == 0;
    }
}
