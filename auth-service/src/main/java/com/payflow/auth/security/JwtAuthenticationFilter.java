package com.payflow.auth.security;
import com.payflow.auth.repository.UserRepository;
import io.jsonwebtoken.Claims;
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
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwtService;
    private final UserRepository userRepository;
    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            Claims claims = jwtService.parse(header.substring(7).trim());
            UUID userId = UUID.fromString(claims.getSubject());
            Instant issuedAt = claims.getIssuedAt().toInstant();
            boolean stillValid = userRepository.findById(userId)
                    .map(u -> !issuedAt.isBefore(u.getCredentialsValidFrom().minusSeconds(1))
                            && u.getStatus() == com.payflow.auth.domain.User.Status.ACTIVE)
                    .orElse(false);
            if (!stillValid) {
                chain.doFilter(request, response);
                return;
            }
            Set<String> permissions = new LinkedHashSet<>(stringList(claims.get("permissions")));
            Set<String> roles = new LinkedHashSet<>(stringList(claims.get("roles")));
            String merchantIdClaim = claims.get("merchantId", String.class);
            AuthenticatedUser principal = new AuthenticatedUser(
                    userId, claims.get("email", String.class),
                    merchantIdClaim == null ? null : UUID.fromString(merchantIdClaim),
                    roles, permissions);
            var authorities = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Discarding invalid token: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
    @SuppressWarnings("unchecked")
    private List<String> stringList(Object claim) {
        if (claim instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
