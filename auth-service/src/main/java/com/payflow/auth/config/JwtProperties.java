package com.payflow.auth.config;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
@Validated
@ConfigurationProperties(prefix = "payflow.jwt")
public class JwtProperties {
    @NotBlank
    private String secret;
    private String issuer = "payflow-auth";
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(14);
    private Duration emailVerificationTtl = Duration.ofHours(24);
    private Duration passwordResetTtl = Duration.ofMinutes(30);
    private int maxFailedLogins = 5;
    private Duration lockoutDuration = Duration.ofMinutes(15);
    public String getSecret() {
        return secret;
    }
    public void setSecret(String secret) {
        this.secret = secret;
    }
    public String getIssuer() {
        return issuer;
    }
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }
    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }
    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }
    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }
    public Duration getEmailVerificationTtl() {
        return emailVerificationTtl;
    }
    public void setEmailVerificationTtl(Duration emailVerificationTtl) {
        this.emailVerificationTtl = emailVerificationTtl;
    }
    public Duration getPasswordResetTtl() {
        return passwordResetTtl;
    }
    public void setPasswordResetTtl(Duration passwordResetTtl) {
        this.passwordResetTtl = passwordResetTtl;
    }
    public int getMaxFailedLogins() {
        return maxFailedLogins;
    }
    public void setMaxFailedLogins(int maxFailedLogins) {
        this.maxFailedLogins = maxFailedLogins;
    }
    public Duration getLockoutDuration() {
        return lockoutDuration;
    }
    public void setLockoutDuration(Duration lockoutDuration) {
        this.lockoutDuration = lockoutDuration;
    }
}
