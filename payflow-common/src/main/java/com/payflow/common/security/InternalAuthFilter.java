package com.payflow.common.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.crypto.Hashing;
import com.payflow.common.error.ApiErrorResponse;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.web.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
public class InternalAuthFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Internal-Token";
    private final String expectedToken;
    private final ObjectMapper objectMapper;
    public InternalAuthFilter(String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken;
        this.objectMapper = objectMapper;
    }
    private static final String WEBHOOK_PREFIX = "/internal/webhooks/providers/";
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/internal/") || uri.startsWith(WEBHOOK_PREFIX);
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!Hashing.constantTimeEquals(expectedToken, request.getHeader(HEADER))) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                    ErrorCode.UNAUTHORIZED, "Invalid internal service token", CorrelationId.get()));
            return;
        }
        chain.doFilter(request, response);
    }
}
