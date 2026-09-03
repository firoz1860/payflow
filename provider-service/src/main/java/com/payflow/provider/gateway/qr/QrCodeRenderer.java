package com.payflow.provider.gateway.qr;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
/**
 * Renders an arbitrary payload string (a UPI intent, a provider QR string, a URL)
 * into a scannable QR code, returned as a self-contained {@code data:image/png;base64,...}
 * URI so a client can display it with a bare {@code <img src>} and no extra round trip.
 */
@Component
public class QrCodeRenderer {
    private static final int DEFAULT_SIZE = 320;
    private static final int MARGIN = 1;
    public String toPngDataUri(String payload) {
        return toPngDataUri(payload, DEFAULT_SIZE);
    }
    public String toPngDataUri(String payload, int size) {
        if (payload == null || payload.isBlank()) {
            throw new PayFlowException(ErrorCode.PROVIDER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot render a QR code for an empty payload");
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            // Medium error correction survives a little smudging on a printed/screen QR
            // while keeping the module count (and therefore the pixel density) reasonable.
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, MARGIN);
            BitMatrix matrix = new QRCodeWriter()
                    .encode(payload, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException | java.io.IOException ex) {
            throw new PayFlowException(ErrorCode.PROVIDER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to render QR code", ex);
        }
    }
}
