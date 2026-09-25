package com.payflow.auth.service;
import com.payflow.auth.client.MerchantRegistrationClient;
import com.payflow.auth.config.JwtProperties;
import com.payflow.auth.domain.OneTimeToken;
import com.payflow.auth.domain.RefreshToken;
import com.payflow.auth.domain.Role;
import com.payflow.auth.domain.RoleName;
import com.payflow.auth.domain.User;
import com.payflow.auth.dto.AuthDtos;
import com.payflow.auth.repository.OneTimeTokenRepository;
import com.payflow.auth.repository.RefreshTokenRepository;
import com.payflow.auth.repository.RoleRepository;
import com.payflow.auth.repository.UserRepository;
import com.payflow.auth.security.JwtService;
import com.payflow.common.crypto.Hashing;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.event.Topics;
import com.payflow.common.outbox.OutboxRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final OutboxRecorder outbox;
    private final MerchantRegistrationClient merchantRegistrationClient;
    private final boolean autoVerifyEmail;
    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       OneTimeTokenRepository oneTimeTokenRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       JwtProperties jwtProperties, OutboxRecorder outbox,
                       MerchantRegistrationClient merchantRegistrationClient,
                       @org.springframework.beans.factory.annotation.Value("${payflow.bootstrap.auto-verify-email:false}") boolean autoVerifyEmail) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.oneTimeTokenRepository = oneTimeTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.outbox = outbox;
        this.merchantRegistrationClient = merchantRegistrationClient;
        this.autoVerifyEmail = autoVerifyEmail;
    }
    @Transactional
    public AuthDtos.UserResponse register(AuthDtos.RegisterRequest request) {
        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw PayFlowException.conflict(ErrorCode.CONFLICT, "Unable to register with the details provided");
        }
        if (request.merchantId() != null || request.role() != null) {
            throw PayFlowException.forbidden(
                    "Public registration cannot self-assign a merchant or role");
        }
        RoleName roleName = RoleName.MERCHANT_OWNER;
        MerchantRegistrationClient.CreatedMerchant merchant = merchantRegistrationClient.create(
                request.fullName().trim(), email, null, "IN", "INR");
        UUID merchantId = merchant.id();
        User user = new User(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), merchantId);
        user.addRole(loadRole(roleName));
        if (autoVerifyEmail) {
            user.verifyEmail();
        }
        userRepository.save(user);
        if (!autoVerifyEmail) {
            String rawToken = issueOneTimeToken(user, OneTimeToken.Purpose.EMAIL_VERIFICATION,
                    jwtProperties.getEmailVerificationTtl());
            outbox.record("User", user.getId().toString(), Topics.NOTIFICATION_REQUESTED, 1,
                    Map.of("template", "EMAIL_VERIFICATION",
                            "channel", "EMAIL",
                            "recipient", user.getEmail(),
                            "variables", Map.of("fullName", user.getFullName(), "token", rawToken)));
        }
        recordAudit(user.getId(), "USER_REGISTERED", user.getId().toString());
        log.info("Registered user {} with role {} merchantId {}", user.getId(), roleName, merchantId);
        return toResponse(user);
    }
    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request, String userAgent, String ip) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElse(null);
        if (user == null) {
            passwordEncoder.encode(request.password());
            throw PayFlowException.unauthorized("Invalid email or password");
        }
        if (user.isLocked()) {
            throw new PayFlowException(ErrorCode.UNAUTHORIZED,
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Account temporarily locked due to failed sign-in attempts");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.registerFailedLogin(jwtProperties.getMaxFailedLogins(), jwtProperties.getLockoutDuration());
            recordAudit(user.getId(), "LOGIN_FAILED", user.getId().toString());
            throw PayFlowException.unauthorized("Invalid email or password");
        }
        if (user.getStatus() == User.Status.SUSPENDED || user.getStatus() == User.Status.DISABLED) {
            throw PayFlowException.forbidden("This account is not permitted to sign in");
        }
        if (!user.isEmailVerified()) {
            throw PayFlowException.forbidden("Email address has not been verified");
        }
        user.registerSuccessfulLogin();
        recordAudit(user.getId(), "LOGIN_SUCCEEDED", user.getId().toString());
        return issueTokens(user, UUID.randomUUID(), userAgent, ip);
    }
    @Transactional
    public AuthDtos.TokenResponse refresh(String rawRefreshToken, String userAgent, String ip) {
        String hash = Hashing.sha256Hex(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> PayFlowException.unauthorized("Invalid refresh token"));
        if (stored.getStatus() == RefreshToken.Status.ROTATED) {
            log.warn("Refresh token reuse detected for user {} - revoking family {}",
                    stored.getUserId(), stored.getFamilyId());
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), RefreshToken.Status.REVOKED, Instant.now());
            recordAudit(stored.getUserId(), "REFRESH_TOKEN_REUSE_DETECTED", stored.getFamilyId().toString());
            throw PayFlowException.unauthorized("Refresh token has already been used");
        }
        if (!stored.isUsable()) {
            throw PayFlowException.unauthorized("Refresh token is expired or revoked");
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> PayFlowException.unauthorized("Invalid refresh token"));
        if (user.getStatus() != User.Status.ACTIVE) {
            throw PayFlowException.forbidden("This account is not permitted to sign in");
        }
        stored.markRotated();
        return issueTokens(user, stored.getFamilyId(), userAgent, ip);
    }
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(Hashing.sha256Hex(rawRefreshToken))
                .ifPresent(token -> {
                    refreshTokenRepository.revokeFamily(token.getFamilyId(),
                            RefreshToken.Status.REVOKED, Instant.now());
                    recordAudit(token.getUserId(), "LOGOUT", token.getFamilyId().toString());
                });
    }
    @Transactional
    public void logoutEverywhere(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, RefreshToken.Status.REVOKED, Instant.now());
        userRepository.findById(userId).ifPresent(u -> u.changePassword(u.getPasswordHash()));
        recordAudit(userId, "LOGOUT_ALL_SESSIONS", userId.toString());
    }
    @Transactional
    public void verifyEmail(String rawToken) {
        OneTimeToken token = consumeOneTimeToken(rawToken, OneTimeToken.Purpose.EMAIL_VERIFICATION);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> PayFlowException.notFound("User not found"));
        user.verifyEmail();
        recordAudit(user.getId(), "EMAIL_VERIFIED", user.getId().toString());
    }
    @Transactional
    public void initiatePasswordReset(String email) {
        userRepository.findByEmailIgnoreCase(email.trim()).ifPresent(user -> {
            String rawToken = issueOneTimeToken(user, OneTimeToken.Purpose.PASSWORD_RESET,
                    jwtProperties.getPasswordResetTtl());
            outbox.record("User", user.getId().toString(), Topics.NOTIFICATION_REQUESTED, 1,
                    Map.of("template", "PASSWORD_RESET",
                            "channel", "EMAIL",
                            "recipient", user.getEmail(),
                            "variables", Map.of("fullName", user.getFullName(), "token", rawToken)));
            recordAudit(user.getId(), "PASSWORD_RESET_REQUESTED", user.getId().toString());
        });
    }
    @Transactional
    public void completePasswordReset(String rawToken, String newPassword) {
        OneTimeToken token = consumeOneTimeToken(rawToken, OneTimeToken.Purpose.PASSWORD_RESET);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> PayFlowException.notFound("User not found"));
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllForUser(user.getId(), RefreshToken.Status.REVOKED, Instant.now());
        outbox.record("User", user.getId().toString(), Topics.NOTIFICATION_REQUESTED, 1,
                Map.of("template", "PASSWORD_CHANGED", "channel", "EMAIL", "recipient", user.getEmail()));
        recordAudit(user.getId(), "PASSWORD_RESET_COMPLETED", user.getId().toString());
    }
    @Transactional
    public AuthDtos.UserResponse assignRole(UUID actorId, UUID actorMerchantId,
                                             boolean platformAdmin,
                                             UUID userId, RoleName roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> PayFlowException.notFound("User not found"));
        if (!platformAdmin) {
            if (actorMerchantId == null || user.getMerchantId() == null
                    || !actorMerchantId.equals(user.getMerchantId())) {
                throw PayFlowException.forbidden("Team roles can only be managed within your merchant");
            }
            if (roleName == RoleName.PAYFLOW_ADMIN) {
                throw PayFlowException.forbidden("PAYFLOW_ADMIN can only be assigned by a platform admin");
            }
        }
        user.addRole(loadRole(roleName));
        recordAudit(actorId, "ROLE_ASSIGNED", userId.toString());
        return toResponse(user);
    }
    @Transactional(readOnly = true)
    public AuthDtos.UserResponse currentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> PayFlowException.notFound("User not found"));
    }
    private AuthDtos.TokenResponse issueTokens(User user, UUID familyId, String userAgent, String ip) {
        String rawRefresh = Hashing.randomToken(48);
        refreshTokenRepository.save(new RefreshToken(
                Hashing.sha256Hex(rawRefresh), user.getId(), familyId,
                Instant.now().plus(jwtProperties.getRefreshTokenTtl()), userAgent, ip));
        return new AuthDtos.TokenResponse(
                jwtService.issueAccessToken(user), rawRefresh, "Bearer",
                jwtService.accessTokenTtlSeconds(), toResponse(user));
    }
    private String issueOneTimeToken(User user, OneTimeToken.Purpose purpose, java.time.Duration ttl) {
        String rawToken = Hashing.randomToken(32);
        oneTimeTokenRepository.save(new OneTimeToken(
                Hashing.sha256Hex(rawToken), user.getId(), purpose, Instant.now().plus(ttl)));
        return rawToken;
    }
    private OneTimeToken consumeOneTimeToken(String rawToken, OneTimeToken.Purpose purpose) {
        OneTimeToken token = oneTimeTokenRepository
                .findByTokenHashAndPurpose(Hashing.sha256Hex(rawToken), purpose)
                .orElseThrow(() -> PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Invalid or expired token"));
        if (!token.isUsable()) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED, "Invalid or expired token");
        }
        token.consume();
        return token;
    }
    private Role loadRole(RoleName roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
    }
    private void recordAudit(UUID actorId, String action, String entityId) {
        outbox.record("User", entityId, Topics.AUDIT_EVENT, 1,
                Map.of("actorType", "USER",
                        "actorId", actorId == null ? "system" : actorId.toString(),
                        "action", action,
                        "entityType", "User",
                        "entityId", entityId));
    }
    private AuthDtos.UserResponse toResponse(User user) {
        return new AuthDtos.UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getMerchantId(), user.getStatus().name(), user.isEmailVerified(),
                user.roleNames(), user.permissionValues(), user.getCreatedAt());
    }
}
