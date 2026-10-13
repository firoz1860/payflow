package com.payflow.payment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.security.ApiKeyVerifier;
import com.payflow.common.security.PayFlowPrincipal;
import com.payflow.payment.client.MerchantClient;
import com.payflow.payment.client.ProviderClient;
import com.payflow.payment.client.RiskClient;
import com.payflow.payment.controller.PaymentController;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.dto.PaymentDtos;
import com.payflow.payment.service.IdempotencyService;
import com.payflow.payment.service.PaymentCheckoutService;
import com.payflow.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
@ActiveProfiles("test")
class UncertainPaymentCreationIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired PaymentService payments;
    @Autowired IdempotencyService idempotency;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ProviderClient provider;
    @MockitoBean RiskClient risk;

    @Test
    void failedProviderResponseCannotCreateAnotherPaymentOnTheSameKey() {
        UUID merchant = UUID.randomUUID();
        Set<String> permissions = Set.of("payments:create", "payments:read");
        var principal = PayFlowPrincipal.forUser(UUID.randomUUID(), merchant, permissions);
        var http = new MockHttpServletRequest();
        http.setAttribute(ApiKeyVerifier.REQUEST_ATTRIBUTE, new ApiKeyVerifier.Verification(
                true, merchant, "RETRY_TEST", "test-key", "TEST", "SECRET", permissions,
                "ACTIVE", true, "INR", new BigDecimal("2.00"), BigDecimal.ZERO, null));
        var controller = new PaymentController(payments, idempotency, mapper,
                mock(MerchantClient.class), mock(PaymentCheckoutService.class));
        var request = new PaymentDtos.CreatePaymentRequest(new BigDecimal("10.00"), "INR",
                null, null, "Uncertain TEST request", PaymentAttempt.PaymentMethod.CARD, null, null);
        when(risk.evaluate(any())).thenReturn(new RiskClient.RiskResponse(RiskClient.Decision.ALLOW, 0, List.of(), null));
        when(provider.createPayment(any())).thenThrow(new PayFlowException(
                ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "Provider response unavailable"));
        String key = "uncertain-" + UUID.randomUUID();
        assertThrows(PayFlowException.class, () -> controller.create(principal, key, request, http));
        var retry = assertThrows(PayFlowException.class, () -> controller.create(principal, key, request, http));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE merchant_id=?", Integer.class, merchant)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events e JOIN payments p ON e.aggregate_id=p.payment_reference WHERE p.merchant_id=? AND e.event_type='payment.created'", Integer.class, merchant)).isEqualTo(1);
        verify(provider, times(1)).createPayment(any());
        assertThat(retry.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(retry.getCode().name()).isEqualTo("PAYMENT_CREATION_UNCERTAIN");
    }
}
