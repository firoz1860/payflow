package com.payflow.provider.gateway.qr;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
/**
 * Builds a UPI deep-link / QR payload of the form
 * {@code upi://pay?pa=...&pn=...&am=...&cu=INR&tn=...&tr=...}.
 *
 * <p>This is the exact string any UPI app (GPay, PhonePe, Paytm, a bank app) parses when
 * it scans the QR. The receiver's VPA ({@code pa}), payee name ({@code pn}) and amount
 * ({@code am}) are fixed by us so the customer cannot alter what they are paying; the
 * transaction reference ({@code tr}) ties the scan back to a specific PayFlow payment.
 */
public final class UpiIntent {
    private UpiIntent() {
    }
    public static String build(String payeeVpa, String payeeName, BigDecimal amount,
                               String currency, String transactionRef, String note) {
        StringBuilder sb = new StringBuilder("upi://pay");
        sb.append("?pa=").append(encode(payeeVpa));
        sb.append("&pn=").append(encode(payeeName));
        // UPI amounts are always two decimal places, dot-separated, no thousands separators.
        sb.append("&am=").append(encode(amount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        sb.append("&cu=").append(encode(currency == null ? "INR" : currency.toUpperCase()));
        if (transactionRef != null && !transactionRef.isBlank()) {
            sb.append("&tr=").append(encode(transactionRef));
        }
        if (note != null && !note.isBlank()) {
            sb.append("&tn=").append(encode(truncate(note, 50)));
        }
        return sb.toString();
    }
    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
