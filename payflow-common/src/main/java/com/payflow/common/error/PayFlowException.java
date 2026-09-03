package com.payflow.common.error;
import org.springframework.http.HttpStatus;
public class PayFlowException extends RuntimeException {
    private final ErrorCode code;
    private final HttpStatus status;
    public PayFlowException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
    public PayFlowException(ErrorCode code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }
    public ErrorCode getCode() {
        return code;
    }
    public HttpStatus getStatus() {
        return status;
    }
    public static PayFlowException notFound(String message) {
        return new PayFlowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
    public static PayFlowException conflict(ErrorCode code, String message) {
        return new PayFlowException(code, HttpStatus.CONFLICT, message);
    }
    public static PayFlowException badRequest(ErrorCode code, String message) {
        return new PayFlowException(code, HttpStatus.BAD_REQUEST, message);
    }
    public static PayFlowException unauthorized(String message) {
        return new PayFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
    }
    public static PayFlowException forbidden(String message) {
        return new PayFlowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }
    public static PayFlowException unprocessable(ErrorCode code, String message) {
        return new PayFlowException(code, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
