package com.payflow.merchant.service;
import com.payflow.common.error.PayFlowException;
import com.payflow.merchant.config.ApiKeyProperties;
import com.payflow.merchant.domain.ApiKey;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.dto.MerchantDtos;
import com.payflow.merchant.repository.ApiKeyRepository;
import com.payflow.merchant.repository.MerchantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiKeyServiceTest {
    @Mock
    private ApiKeyRepository apiKeyRepository;
    @Mock
    private MerchantRepository merchantRepository;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private com.payflow.common.outbox.OutboxRecorder outbox;
    private ApiKeyService apiKeyService;
    private Merchant merchant;
    @BeforeEach
    void setUp() {
        ApiKeyProperties properties = new ApiKeyProperties();
        properties.setPepper("unit-test-pepper");
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);
        apiKeyService = new ApiKeyService(apiKeyRepository, merchantRepository,
                new BCryptPasswordEncoder(4),   // low cost: this is a unit test, not a benchmark
                properties, redis, outbox, new ObjectMapper());
        merchant = new Merchant("MRC_TEST", "Test Co", "ops@test.co", null, "IN", "INR");
    }
    @Test
    @DisplayName("the raw secret is returned once and only its hashes are persisted")
    void secretIsReturnedOnceAndHashed() {
        UUID merchantId = merchant.getId();
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(apiKeyRepository.countByMerchantIdAndStatus(any(), any())).thenReturn(0L);
        MerchantDtos.CreateApiKeyResponse response = apiKeyService.create(merchantId, null,
                new MerchantDtos.CreateApiKeyRequest(ApiKey.Environment.TEST,
                        ApiKey.KeyType.SECRET, "server", null, null));
        assertThat(response.secret()).startsWith("sk_test_");
        assertThat(response.maskedKey()).doesNotContain(response.secret());
        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        org.mockito.Mockito.verify(apiKeyRepository).save(captor.capture());
        ApiKey saved = captor.getValue();
        assertThat(saved.getSecretHash()).doesNotContain(response.secret());
        assertThat(saved.getLookupHash()).doesNotContain(response.secret());
        assertThat(saved.getSecretHash()).startsWith("$2");   // bcrypt
        assertThat(saved.getLookupHash()).hasSize(64);        // sha-256 hex
    }
    @Test
    @DisplayName("live keys cannot be issued until live mode is enabled")
    void liveKeyRequiresLiveMode() {
        UUID merchantId = merchant.getId();
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(apiKeyRepository.countByMerchantIdAndStatus(any(), any())).thenReturn(0L);
        assertThatThrownBy(() -> apiKeyService.create(merchantId, null,
                new MerchantDtos.CreateApiKeyRequest(ApiKey.Environment.LIVE,
                        ApiKey.KeyType.SECRET, "live", null, null)))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("Live mode is not enabled");
    }
    @Test
    @DisplayName("publishable keys may never hold money-moving scopes")
    void publishableKeysCannotRefund() {
        UUID merchantId = merchant.getId();
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(apiKeyRepository.countByMerchantIdAndStatus(any(), any())).thenReturn(0L);
        assertThatThrownBy(() -> apiKeyService.create(merchantId, null,
                new MerchantDtos.CreateApiKeyRequest(ApiKey.Environment.TEST,
                        ApiKey.KeyType.PUBLISHABLE, "browser",
                        Set.of("refunds:create"), null)))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("Publishable keys cannot hold scopes");
    }
    @Test
    @DisplayName("an unknown key is rejected without leaking whether it exists")
    void unknownKeyRejected() {
        when(apiKeyRepository.findByLookupHash(any())).thenReturn(Optional.empty());
        var result = apiKeyService.verify("sk_test_totally-made-up-key-value-here");
        assertThat(result.valid()).isFalse();
        assertThat(result.merchantId()).isNull();
        assertThat(result.reason()).isEqualTo("Invalid API key");
    }
    @Test
    @DisplayName("a malformed key is rejected before any database work happens")
    void malformedKeyRejectedEarly() {
        assertThat(apiKeyService.verify("not-a-key").valid()).isFalse();
        assertThat(apiKeyService.verify(null).valid()).isFalse();
        org.mockito.Mockito.verify(apiKeyRepository, org.mockito.Mockito.never())
                .findByLookupHash(any());
    }
}
