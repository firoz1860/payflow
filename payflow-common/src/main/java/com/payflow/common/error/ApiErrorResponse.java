package com.payflow.common.error;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        ErrorCode code,
        String message,
        String correlationId,
        Instant timestamp,
        List<FieldViolation> violations,
        Map<String, Object> details
) {
    public record FieldViolation(String field, String message) {
    }
    public static ApiErrorResponse of(ErrorCode code, String message, String correlationId) {
        return new ApiErrorResponse(code, message, correlationId, Instant.now(), null, null);
    }
}
