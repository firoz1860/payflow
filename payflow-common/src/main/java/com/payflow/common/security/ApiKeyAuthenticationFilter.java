package com.payflow.common.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.ApiErrorResponse;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.web.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private final ApiKeyVerifier verifier;
    private final ObjectMapper objectMapper;
    private final boolean allowPublishableKeys;
    public ApiKeyAuthenticationFilter(ApiKeyVerifier verifier, ObjectMapper objectMapper,
                                      boolean allowPublishableKeys) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
        this.allowPublishableKeys = allowPublishableKeys;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String credential = header.substring(7).trim();
        if (!credential.startsWith("sk_") && !credential.startsWith("pk_")) {
            chain.doFilter(request, response);   // a JWT; handled elsewhere
            return;
        }
        if (credential.startsWith("pk_") && !allowPublishableKeys) {
            reject(response, "Publishable keys cannot be used for this operation");
            return;
        }
        ApiKeyVerifier.Verification verification = verifier.verify(credential);
        if (!verification.valid()) {
            log.debug("API key rejected: {}", verification.reason());
            reject(response, verification.reason() == null ? "Invalid API key" : verification.reason());
            return;
        }
        PayFlowPrincipal principal = PayFlowPrincipal.forApiKey(
                verification.merchantId(), verification.keyId(),
                PayFlowPrincipal.Environment.valueOf(verification.environment()),
                verification.scopes());
        List<SimpleGrantedAuthority> authorities = verification.scopes().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
        request.setAttribute(ApiKeyVerifier.REQUEST_ATTRIBUTE, verification);
        chain.doFilter(request, response);
    }
    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(ErrorCode.UNAUTHORIZED, message, CorrelationId.get()));
    }
}
