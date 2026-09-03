package com.payflow.common.security;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
public class BearerJwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(BearerJwtAuthenticationFilter.class);
    private final JwtTokenValidator validator;
    public BearerJwtAuthenticationFilter(JwtTokenValidator validator) {
        this.validator = validator;
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
        if (credential.startsWith("sk_") || credential.startsWith("pk_")) {
            chain.doFilter(request, response);   // handled by the API key filter
            return;
        }
        try {
            PayFlowPrincipal principal = validator.validate(credential);
            List<SimpleGrantedAuthority> authorities = principal.permissions().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
