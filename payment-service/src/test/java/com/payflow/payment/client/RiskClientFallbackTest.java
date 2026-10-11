package com.payflow.payment.client;

import com.payflow.common.client.ServiceClientFactory;
import io.github.resilience4j.spring6.fallback.FallbackMethod;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.web.reactive.function.client.WebClient;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RiskClientFallbackTest {
    @Test
    void unavailableRiskKeepsConfiguredCeilingThroughClassProxies() throws Throwable {
        ServiceClientFactory factory = mock(ServiceClientFactory.class);
        when(factory.create("http://risk")).thenReturn(WebClient.builder().build());
        RiskClient target = new RiskClient(factory, "http://risk", new BigDecimal("5000"));
        RiskClient inner = proxy(target);
        RiskClient outer = proxy(inner);
        assertDecision(inner, outer, "1", RiskClient.Decision.ALLOW);
        assertDecision(inner, outer, "5000", RiskClient.Decision.ALLOW);
        assertDecision(inner, outer, "5000.01", RiskClient.Decision.REVIEW);
    }
    private RiskClient proxy(RiskClient target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        return (RiskClient) factory.getProxy();
    }
    private void assertDecision(RiskClient original, RiskClient proxy, String amount,
                                RiskClient.Decision expected) throws Throwable {
        var request = new RiskClient.RiskRequest(UUID.randomUUID(), null, "qa",
                new BigDecimal(amount), "INR", null, null, "TEST");
        var fallback = FallbackMethod.create("evaluateFallback",
                RiskClient.class.getMethod("evaluate", RiskClient.RiskRequest.class),
                new Object[]{request}, original, proxy);
        var response = (RiskClient.RiskResponse) fallback.fallback(new IllegalStateException("unavailable"));
        assertEquals(expected, response.decision());
        assertEquals(java.util.List.of("RISK_SERVICE_UNAVAILABLE"), response.triggeredRules());
    }
}
