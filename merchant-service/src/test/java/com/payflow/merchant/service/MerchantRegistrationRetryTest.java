package com.payflow.merchant.service;

import com.payflow.common.error.PayFlowException;
import com.payflow.common.outbox.OutboxRecorder;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.dto.MerchantDtos;
import com.payflow.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantRegistrationRetryTest {
    private final MerchantRepository repository = mock(MerchantRepository.class);
    private final OutboxRecorder outbox = mock(OutboxRecorder.class);
    private final MerchantService service = new MerchantService(repository, mock(ApiKeyService.class), outbox);
    private final MerchantDtos.CreateMerchantRequest request = new MerchantDtos.CreateMerchantRequest(
            "QA", "qa@example.com", "", "IN", "INR");
    private final String key = "a".repeat(64);

    @Test
    void retryReturnsSameMerchantWithoutDuplicatingEvents() {
        Merchant merchant = new Merchant("MRC_QA", "QA", "qa@example.com", "", "IN", "INR");
        merchant.setRegistrationKey(key);
        when(repository.findByRegistrationKey(key)).thenReturn(Optional.of(merchant));
        assertThat(service.createForRegistration(request, key).id()).isEqualTo(merchant.getId());
        verify(repository, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void differentRegistrationKeyCannotClaimExistingEmail() {
        when(repository.findByRegistrationKey(key)).thenReturn(Optional.empty());
        when(repository.existsByEmailIgnoreCase(request.email())).thenReturn(true);
        assertThatThrownBy(() -> service.createForRegistration(request, key)).isInstanceOf(PayFlowException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void missingKeyIsRejected() {
        assertThatThrownBy(() -> service.createForRegistration(request, null)).isInstanceOf(PayFlowException.class);
        verifyNoInteractions(repository);
    }
}
