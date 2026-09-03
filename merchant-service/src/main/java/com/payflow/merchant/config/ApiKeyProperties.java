package com.payflow.merchant.config;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
@Validated
@ConfigurationProperties(prefix = "payflow.api-key")
public class ApiKeyProperties {
    @NotBlank
    private String pepper;
    private int secretBytes = 24;
    private int maxActiveKeysPerMerchant = 20;
    private Duration verificationCacheTtl = Duration.ofSeconds(60);
    public String getPepper() {
        return pepper;
    }
    public void setPepper(String pepper) {
        this.pepper = pepper;
    }
    public int getSecretBytes() {
        return secretBytes;
    }
    public void setSecretBytes(int secretBytes) {
        this.secretBytes = secretBytes;
    }
    public int getMaxActiveKeysPerMerchant() {
        return maxActiveKeysPerMerchant;
    }
    public void setMaxActiveKeysPerMerchant(int maxActiveKeysPerMerchant) {
        this.maxActiveKeysPerMerchant = maxActiveKeysPerMerchant;
    }
    public Duration getVerificationCacheTtl() {
        return verificationCacheTtl;
    }
    public void setVerificationCacheTtl(Duration verificationCacheTtl) {
        this.verificationCacheTtl = verificationCacheTtl;
    }
}
