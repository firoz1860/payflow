package com.payflow.merchant.domain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
@Entity
@Table(name = "merchants")
public class Merchant {
    public enum Status { PENDING, ACTIVE, SUSPENDED, BLOCKED }
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "merchant_code", nullable = false, unique = true, length = 32)
    private String merchantCode;
    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;
    @Column(name = "phone", length = 32)
    private String phone;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "country", nullable = false, length = 2)
    private String country;
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;
    @Column(name = "fee_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal feePercentage = new BigDecimal("2.00");
    @Column(name = "fixed_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal fixedFee = BigDecimal.ZERO;
    @Column(name = "settlement_delay_days", nullable = false)
    private int settlementDelayDays = 2;
    @Column(name = "live_mode_enabled", nullable = false)
    private boolean liveModeEnabled;
    @Column(name = "status_reason", length = 500)
    private String statusReason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    @Column(name = "version", nullable = false)
    private long version;
    protected Merchant() {
    }
    public Merchant(String merchantCode, String businessName, String email, String phone,
                    String country, String defaultCurrency) {
        this.id = UUID.randomUUID();
        this.merchantCode = merchantCode;
        this.businessName = businessName;
        this.email = email.toLowerCase();
        this.phone = phone;
        this.country = country.toUpperCase();
        this.defaultCurrency = defaultCurrency.toUpperCase();
    }
    public boolean canProcessPayments() {
        return status == Status.ACTIVE;
    }
    public boolean canProcessLivePayments() {
        return canProcessPayments() && liveModeEnabled;
    }
    public void changeStatus(Status newStatus, String reason) {
        this.status = newStatus;
        this.statusReason = reason;
        if (newStatus != Status.ACTIVE) {
            this.liveModeEnabled = false;
        }
        touch();
    }
    public void enableLiveMode() {
        if (status != Status.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE merchants can enable live mode");
        }
        this.liveModeEnabled = true;
        touch();
    }
    public void updateProfile(String businessName, String phone, String defaultCurrency) {
        if (businessName != null) {
            this.businessName = businessName;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (defaultCurrency != null) {
            this.defaultCurrency = defaultCurrency.toUpperCase();
        }
        touch();
    }
    public void updatePricing(BigDecimal feePercentage, BigDecimal fixedFee, Integer settlementDelayDays) {
        if (feePercentage != null) {
            this.feePercentage = feePercentage;
        }
        if (fixedFee != null) {
            this.fixedFee = fixedFee;
        }
        if (settlementDelayDays != null) {
            this.settlementDelayDays = settlementDelayDays;
        }
        touch();
    }
    private void touch() {
        this.updatedAt = Instant.now();
    }
    public UUID getId() {
        return id;
    }
    public String getMerchantCode() {
        return merchantCode;
    }
    public String getBusinessName() {
        return businessName;
    }
    public String getEmail() {
        return email;
    }
    public String getPhone() {
        return phone;
    }
    public Status getStatus() {
        return status;
    }
    public String getCountry() {
        return country;
    }
    public String getDefaultCurrency() {
        return defaultCurrency;
    }
    public BigDecimal getFeePercentage() {
        return feePercentage;
    }
    public BigDecimal getFixedFee() {
        return fixedFee;
    }
    public int getSettlementDelayDays() {
        return settlementDelayDays;
    }
    public boolean isLiveModeEnabled() {
        return liveModeEnabled;
    }
    public String getStatusReason() {
        return statusReason;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
