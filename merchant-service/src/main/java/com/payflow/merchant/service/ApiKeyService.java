package com.payflow.merchant.service;
import com.payflow.common.crypto.Hashing;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.event.Topics;
import com.payflow.common.outbox.OutboxRecorder;
import com.payflow.merchant.config.ApiKeyProperties;
import com.payflow.merchant.domain.ApiKey;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.dto.MerchantDtos;
import com.payflow.merchant.repository.ApiKeyRepository;
import com.payflow.merchant.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
@Service
public class ApiKeyService {
    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String CACHE_PREFIX = "payflow:apikey:v1:";
    private static final Set<String> PUBLISHABLE_ALLOWED_SCOPES =
            Set.of("payments:create", "payments:read");
    private static final Set<String> SECRET_DEFAULT_SCOPES = Set.of(
            "payments:create", "payments:read",
            "refunds:create", "refunds:read",
            "customers:manage", "customers:read",
            "settlements:read");
    private final ApiKeyRepository apiKeyRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyProperties properties;
    private final StringRedisTemplate redis;
    private final OutboxRecorder outbox;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    public ApiKeyService(ApiKeyRepository apiKeyRepository, MerchantRepository merchantRepository,
                         PasswordEncoder passwordEncoder, ApiKeyProperties properties,
                         StringRedisTemplate redis, OutboxRecorder outbox,
                         com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.apiKeyRepository = apiKeyRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.redis = redis;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }
    @Transactional
    public MerchantDtos.CreateApiKeyResponse create(UUID merchantId, UUID actorId,
                                                    MerchantDtos.CreateApiKeyRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> PayFlowException.notFound("Merchant not found"));
        if (request.environment() == ApiKey.Environment.LIVE && !merchant.isLiveModeEnabled()) {
            throw PayFlowException.unprocessable(ErrorCode.MERCHANT_NOT_ACTIVE,
                    "Live mode is not enabled for this merchant");
        }
        long activeKeys = apiKeyRepository.countByMerchantIdAndStatus(merchantId, ApiKey.Status.ACTIVE);
        if (activeKeys >= properties.getMaxActiveKeysPerMerchant()) {
            throw PayFlowException.conflict(ErrorCode.CONFLICT,
                    "Maximum of " + properties.getMaxActiveKeysPerMerchant() + " active keys reached");
        }
        Set<String> scopes = resolveScopes(request.keyType(), request.scopes());
        String prefix = (request.keyType() == ApiKey.KeyType.SECRET ? "sk" : "pk")
                + "_" + request.environment().name().toLowerCase() + "_";
        String rawSecret = prefix + Hashing.randomToken(properties.getSecretBytes());
        String keyId = "key_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String lookupHash = lookupHash(rawSecret);
        String secretHash = passwordEncoder.encode(rawSecret);
        String maskedKey = prefix + "..." + rawSecret.substring(rawSecret.length() - 4);
        ApiKey apiKey = new ApiKey(keyId, lookupHash, secretHash, maskedKey, merchantId,
                request.environment(), request.keyType(), request.label(), scopes,
                request.expiresAt(), actorId);
        apiKeyRepository.save(apiKey);
        outbox.record("ApiKey", keyId, Topics.AUDIT_EVENT, 1, merchantId.toString(),
                Map.of("actorType", "USER",
                        "actorId", actorId == null ? "system" : actorId.toString(),
                        "merchantId", merchantId.toString(),
                        "action", "API_KEY_CREATED",
                        "entityType", "ApiKey",
                        "entityId", keyId,
                        "metadata", Map.of("environment", request.environment().name(),
                                "keyType", request.keyType().name(),
                                "maskedKey", maskedKey)));
        log.info("Issued {} {} key {} for merchant {}",
                request.environment(), request.keyType(), keyId, merchantId);
        return new MerchantDtos.CreateApiKeyResponse(keyId, rawSecret, maskedKey,
                request.environment().name(), request.keyType().name(), scopes, request.expiresAt(),
                "Store this secret now. It cannot be retrieved again.");
    }
    private Set<String> resolveScopes(ApiKey.KeyType keyType, Set<String> requested) {
        if (keyType == ApiKey.KeyType.PUBLISHABLE) {
            if (requested == null || requested.isEmpty()) {
                return Set.of("payments:create");
            }
            Set<String> invalid = new LinkedHashSet<>(requested);
            invalid.removeAll(PUBLISHABLE_ALLOWED_SCOPES);
            if (!invalid.isEmpty()) {
                throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Publishable keys cannot hold scopes: " + invalid);
            }
            return new LinkedHashSet<>(requested);
        }
        if (requested == null || requested.isEmpty()) {
            return SECRET_DEFAULT_SCOPES;
        }
        Set<String> invalid = new LinkedHashSet<>(requested);
        invalid.removeAll(SECRET_DEFAULT_SCOPES);
        if (!invalid.isEmpty()) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Unknown or disallowed scopes: " + invalid);
        }
        return new LinkedHashSet<>(requested);
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MerchantDtos.ApiKeyVerificationResponse verify(String rawKey) {
        if (rawKey == null || rawKey.length() < 16
                || !(rawKey.startsWith("sk_") || rawKey.startsWith("pk_"))) {
            return MerchantDtos.ApiKeyVerificationResponse.invalid("Malformed API key");
        }
        String lookupHash = lookupHash(rawKey);
        String cacheKey = CACHE_PREFIX + lookupHash;
        MerchantDtos.ApiKeyVerificationResponse cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        ApiKey apiKey = apiKeyRepository.findByLookupHash(lookupHash).orElse(null);
        if (apiKey == null) {
            passwordEncoder.matches(rawKey, "$2a$12$xxxxxxxxxxxxxxxxxxxxxuGZQ8pCkZ5Jh0HgYQ0h8xVJ2y7lYqBK");
            return MerchantDtos.ApiKeyVerificationResponse.invalid("Invalid API key");
        }
        if (!passwordEncoder.matches(rawKey, apiKey.getSecretHash())) {
            log.warn("Lookup hash matched key {} but secret verification failed", apiKey.getKeyId());
            return MerchantDtos.ApiKeyVerificationResponse.invalid("Invalid API key");
        }
        if (!apiKey.isUsable()) {
            return MerchantDtos.ApiKeyVerificationResponse.invalid(
                    "API key is " + apiKey.getStatus().name().toLowerCase());
        }
        Merchant merchant = merchantRepository.findById(apiKey.getMerchantId()).orElse(null);
        if (merchant == null) {
            return MerchantDtos.ApiKeyVerificationResponse.invalid("Merchant not found");
        }
        boolean canProcess = apiKey.getEnvironment() == ApiKey.Environment.LIVE
                ? merchant.canProcessLivePayments()
                : merchant.canProcessPayments();
        MerchantDtos.ApiKeyVerificationResponse result = new MerchantDtos.ApiKeyVerificationResponse(
                true, merchant.getId(), merchant.getMerchantCode(), apiKey.getKeyId(),
                apiKey.getEnvironment().name(), apiKey.getKeyType().name(), apiKey.getScopes(),
                merchant.getStatus().name(), canProcess, merchant.getDefaultCurrency(),
                merchant.getFeePercentage(), merchant.getFixedFee(), null);
        writeCache(cacheKey, result);
        recordUsageAsync(apiKey.getId());
        return result;
    }
    @Async
    @Transactional
    public void recordUsageAsync(UUID apiKeyId) {
        try {
            apiKeyRepository.updateLastUsedAt(apiKeyId, Instant.now());
        } catch (RuntimeException ex) {
            log.debug("Could not stamp last_used_at for key {}: {}", apiKeyId, ex.getMessage());
        }
    }
    @Transactional(readOnly = true)
    public java.util.List<MerchantDtos.ApiKeyResponse> list(UUID merchantId) {
        return apiKeyRepository.findAllByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(this::toResponse)
                .toList();
    }
    @Transactional
    public void revoke(UUID merchantId, String keyId, UUID actorId) {
        ApiKey apiKey = apiKeyRepository.findByKeyId(keyId)
                .orElseThrow(() -> PayFlowException.notFound("API key not found"));
        if (!apiKey.getMerchantId().equals(merchantId)) {
            throw PayFlowException.notFound("API key not found");
        }
        apiKey.revoke();
        redis.delete(CACHE_PREFIX + apiKey.getLookupHash());
        outbox.record("ApiKey", keyId, Topics.AUDIT_EVENT, 1, merchantId.toString(),
                Map.of("actorType", "USER",
                        "actorId", actorId == null ? "system" : actorId.toString(),
                        "merchantId", merchantId.toString(),
                        "action", "API_KEY_REVOKED",
                        "entityType", "ApiKey",
                        "entityId", keyId));
        log.info("Revoked API key {} for merchant {}", keyId, merchantId);
    }
    @Transactional
    public int revokeAllForMerchant(UUID merchantId) {
        apiKeyRepository.findAllByMerchantIdOrderByCreatedAtDesc(merchantId)
                .forEach(k -> redis.delete(CACHE_PREFIX + k.getLookupHash()));
        return apiKeyRepository.revokeAllForMerchant(merchantId,
                ApiKey.Status.ACTIVE, ApiKey.Status.REVOKED, Instant.now());
    }
    private String lookupHash(String rawKey) {
        return Hashing.sha256Hex(properties.getPepper() + ":" + rawKey);
    }
    private MerchantDtos.ApiKeyVerificationResponse readCache(String cacheKey) {
        try {
            String json = redis.opsForValue().get(cacheKey);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, MerchantDtos.ApiKeyVerificationResponse.class);
        } catch (Exception ex) {
            log.debug("API key cache read failed: {}", ex.getMessage());
            return null;
        }
    }
    private void writeCache(String cacheKey, MerchantDtos.ApiKeyVerificationResponse result) {
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result),
                    properties.getVerificationCacheTtl());
        } catch (Exception ex) {
            log.debug("API key cache write failed: {}", ex.getMessage());
        }
    }
    private MerchantDtos.ApiKeyResponse toResponse(ApiKey key) {
        return new MerchantDtos.ApiKeyResponse(key.getKeyId(), key.getMaskedKey(),
                key.getEnvironment().name(), key.getKeyType().name(), key.getStatus().name(),
                key.getLabel(), key.getScopes(), key.getCreatedAt(), key.getExpiresAt(),
                key.getLastUsedAt());
    }
}
