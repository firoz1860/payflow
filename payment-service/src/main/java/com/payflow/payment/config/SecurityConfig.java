package com.payflow.payment.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.ApiErrorResponse;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.security.ApiKeyAuthenticationFilter;
import com.payflow.common.security.ApiKeyVerifier;
import com.payflow.common.security.BearerJwtAuthenticationFilter;
import com.payflow.common.security.JwtTokenValidator;
import com.payflow.common.web.CorrelationId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public JwtTokenValidator jwtTokenValidator(@Value("${payflow.jwt.secret}") String secret,
                                               @Value("${payflow.jwt.issuer:payflow-auth}") String issuer) {
        return new JwtTokenValidator(secret, issuer);
    }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenValidator validator,
                                           ApiKeyVerifier apiKeyVerifier,
                                           ObjectMapper objectMapper) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h.frameOptions(f -> f.deny()))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers("/actuator/**").hasAuthority("platform:admin")
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/internal/**").permitAll()   // InternalAuthFilter guards these
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((req, res, e) -> write(res, objectMapper,
                            HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Authentication required"))
                    .accessDeniedHandler((req, res, e) -> write(res, objectMapper,
                            HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Insufficient permissions")))
            .addFilterBefore(new BearerJwtAuthenticationFilter(validator),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyVerifier, objectMapper, false),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    private void write(jakarta.servlet.http.HttpServletResponse response, ObjectMapper mapper,
                       HttpStatus status, ErrorCode code, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(code, message, CorrelationId.get()));
    }
}
