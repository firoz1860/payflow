package com.payflow.auth.service;
import com.payflow.auth.client.MerchantRegistrationClient;
import com.payflow.auth.config.JwtProperties;
import com.payflow.auth.domain.RefreshToken;
import com.payflow.auth.domain.User;
import com.payflow.auth.dto.AuthDtos;
import com.payflow.auth.repository.OneTimeTokenRepository;
import com.payflow.auth.repository.RefreshTokenRepository;
import com.payflow.auth.repository.RoleRepository;
import com.payflow.auth.repository.UserRepository;
import com.payflow.auth.security.JwtService;
import com.payflow.common.crypto.Hashing;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.outbox.OutboxRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private OneTimeTokenRepository oneTimeTokenRepository;
    @Mock private JwtService jwtService;
    @Mock private OutboxRecorder outbox;
    @Mock private MerchantRegistrationClient merchantRegistrationClient;
    private AuthService authService;
    private PasswordEncoder passwordEncoder;
    private JwtProperties jwtProperties;
    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);   // unit test, not a benchmark
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("x".repeat(64));
        authService = new AuthService(userRepository, roleRepository, refreshTokenRepository,
                oneTimeTokenRepository, passwordEncoder, jwtService, jwtProperties, outbox,
                merchantRegistrationClient, false);
    }
    private User activeUser(String password) {
        User user = new User("owner@test.local", passwordEncoder.encode(password),
                "Owner", UUID.randomUUID());
        user.verifyEmail();
        return user;
    }
    @Test
    @DisplayName("a wrong password and an unknown email give the same response")
    void doesNotRevealWhetherAnAccountExists() {
        when(userRepository.findByEmailIgnoreCase("nobody@test.local")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("owner@test.local"))
                .thenReturn(Optional.of(activeUser("CorrectPassw0rd")));
        String unknown = catchMessage(() -> authService.login(
                new AuthDtos.LoginRequest("nobody@test.local", "whatever"), null, null));
        String wrongPassword = catchMessage(() -> authService.login(
                new AuthDtos.LoginRequest("owner@test.local", "WrongPassw0rd"), null, null));
        assertThat(unknown).isEqualTo(wrongPassword).isEqualTo("Invalid email or password");
    }
    @Test
    @DisplayName("an unverified email cannot sign in")
    void unverifiedEmailBlocked() {
        User user = new User("new@test.local", passwordEncoder.encode("CorrectPassw0rd"),
                "New", UUID.randomUUID());
        when(userRepository.findByEmailIgnoreCase("new@test.local")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> authService.login(
                new AuthDtos.LoginRequest("new@test.local", "CorrectPassw0rd"), null, null))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("not been verified");
    }
    @Test
    @DisplayName("repeated failures lock the account")
    void lockoutAfterRepeatedFailures() {
        User user = activeUser("CorrectPassw0rd");
        when(userRepository.findByEmailIgnoreCase("owner@test.local")).thenReturn(Optional.of(user));
        for (int i = 0; i < jwtProperties.getMaxFailedLogins(); i++) {
            catchMessage(() -> authService.login(
                    new AuthDtos.LoginRequest("owner@test.local", "WrongPassw0rd"), null, null));
        }
        assertThat(user.isLocked()).isTrue();
        assertThatThrownBy(() -> authService.login(
                new AuthDtos.LoginRequest("owner@test.local", "CorrectPassw0rd"), null, null))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("temporarily locked");
    }
    @Test
    @DisplayName("reusing a rotated refresh token revokes the whole family")
    void refreshTokenReuseRevokesFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshToken rotated = new RefreshToken(Hashing.sha256Hex("leaked-token"),
                UUID.randomUUID(), familyId, Instant.now().plus(Duration.ofDays(1)), null, null);
        rotated.markRotated();
        when(refreshTokenRepository.findByTokenHash(Hashing.sha256Hex("leaked-token")))
                .thenReturn(Optional.of(rotated));
        assertThatThrownBy(() -> authService.refresh("leaked-token", null, null))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("already been used");
        verify(refreshTokenRepository).revokeFamily(eq(familyId),
                eq(RefreshToken.Status.REVOKED), any());
    }
    @Test
    @DisplayName("a password reset for an unknown address still reports success")
    void passwordResetDoesNotLeakAddresses() {
        when(userRepository.findByEmailIgnoreCase("ghost@test.local")).thenReturn(Optional.empty());
        authService.initiatePasswordReset("ghost@test.local");   // must not throw
        verify(oneTimeTokenRepository, never()).save(any());
    }
    @Test
    @DisplayName("PAYFLOW_ADMIN cannot be self-assigned at registration")
    void adminRoleCannotBeSelfAssigned() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        assertThatThrownBy(() -> authService.register(new AuthDtos.RegisterRequest(
                "attacker@test.local", "Passw0rdPassw0rd", "Attacker", null,
                com.payflow.auth.domain.RoleName.PAYFLOW_ADMIN)))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("cannot be self-assigned");
        verify(userRepository, never()).save(any());
    }
    private String catchMessage(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException ex) {
            return ex.getMessage();
        }
    }
}
