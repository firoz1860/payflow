package com.payflow.auth.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.auth.repository.UserRepository;
import com.payflow.auth.security.JwtAuthenticationFilter;
import com.payflow.auth.security.JwtService;
import com.payflow.common.error.ApiErrorResponse;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.web.CorrelationId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                           UserRepository userRepository,
                                           ObjectMapper objectMapper) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())        // CORS is terminated at the gateway
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                    .frameOptions(f -> f.deny())
                    .contentTypeOptions(c -> {})
                    .httpStrictTransportSecurity(h -> h.includeSubDomains(true).maxAgeInSeconds(31536000)))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/api/v1/auth/register",
                            "/api/v1/auth/login",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/password-reset/**",
                            "/api/v1/auth/verify-email/**").permitAll()
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers("/actuator/**").hasAuthority("platform:admin")
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/internal/**").permitAll()   // guarded by InternalAuthFilter
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((req, res, e) ->
                            write(res, objectMapper, HttpStatus.UNAUTHORIZED,
                                    ErrorCode.UNAUTHORIZED, "Authentication required"))
                    .accessDeniedHandler((req, res, e) ->
                            write(res, objectMapper, HttpStatus.FORBIDDEN,
                                    ErrorCode.FORBIDDEN, "Insufficient permissions")))
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository),
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
