package com.payflow.auth.domain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
@Entity
@Table(name = "users")
public class User {
    public enum Status { PENDING_VERIFICATION, ACTIVE, SUSPENDED, DISABLED }
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;
    @Column(name = "merchant_id")
    private UUID merchantId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status = Status.PENDING_VERIFICATION;
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    @Column(name = "credentials_valid_from", nullable = false)
    private Instant credentialsValidFrom = Instant.now();
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    @Column(name = "version", nullable = false)
    private long version;
    protected User() {
    }
    public User(String email, String passwordHash, String fullName, UUID merchantId) {
        this.id = UUID.randomUUID();
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.merchantId = merchantId;
    }
    public Set<String> permissionValues() {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::value)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    public Set<String> roleNames() {
        return roles.stream().map(r -> r.getName().name())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
    public void registerFailedLogin(int maxAttempts, java.time.Duration lockDuration) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plus(lockDuration);
            this.failedLoginAttempts = 0;
        }
        touch();
    }
    public void registerSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
        touch();
    }
    public void verifyEmail() {
        this.emailVerified = true;
        if (this.status == Status.PENDING_VERIFICATION) {
            this.status = Status.ACTIVE;
        }
        touch();
    }
    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.credentialsValidFrom = Instant.now();
        touch();
    }
    public void addRole(Role role) {
        this.roles.add(role);
        touch();
    }
    public void removeRole(Role role) {
        this.roles.remove(role);
        touch();
    }
    private void touch() {
        this.updatedAt = Instant.now();
    }
    public UUID getId() {
        return id;
    }
    public String getEmail() {
        return email;
    }
    public String getPasswordHash() {
        return passwordHash;
    }
    public String getFullName() {
        return fullName;
    }
    public UUID getMerchantId() {
        return merchantId;
    }
    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
        touch();
    }
    public Status getStatus() {
        return status;
    }
    public void setStatus(Status status) {
        this.status = status;
        touch();
    }
    public boolean isEmailVerified() {
        return emailVerified;
    }
    public Instant getLockedUntil() {
        return lockedUntil;
    }
    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
    public Instant getCredentialsValidFrom() {
        return credentialsValidFrom;
    }
    public Set<Role> getRoles() {
        return roles;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
}
