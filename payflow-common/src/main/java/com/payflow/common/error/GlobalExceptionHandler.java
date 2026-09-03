package com.payflow.common.error;
import com.payflow.common.web.CorrelationId;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.List;
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(PayFlowException.class)
    public ResponseEntity<ApiErrorResponse> handlePayFlow(PayFlowException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("PayFlow error [{}]: {}", ex.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("PayFlow error [{}]: {}", ex.getCode(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage(), CorrelationId.get()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorResponse.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiErrorResponse.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                ErrorCode.VALIDATION_FAILED, "Request validation failed",
                CorrelationId.get(), Instant.now(), violations, null));
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiErrorResponse.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiErrorResponse.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                ErrorCode.VALIDATION_FAILED, "Request validation failed",
                CorrelationId.get(), Instant.now(), violations, null));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformed(Exception ex) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                ErrorCode.VALIDATION_FAILED, ex.getMessage(), CorrelationId.get()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                ErrorCode.CONFLICT, "The request conflicts with existing data", CorrelationId.get()));
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                ErrorCode.CONFLICT, "The resource was modified concurrently, please retry",
                CorrelationId.get()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of(
                ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", CorrelationId.get()));
    }
}
