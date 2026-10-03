package com.payflow.payment.client;

import com.payflow.common.client.ServiceClientFactory;
import com.payflow.common.security.ApiKeyVerifier;
import io.github.resilience4j.spring6.fallback.FallbackMethod;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class MerchantApiKeyFallbackTest {
    @Test void unavailableMerchantIsRetryableThroughSpringProxy() throws Throwable {
        var target=new MerchantApiKeyVerifier(new ServiceClientFactory("test-token",Duration.ofSeconds(1),Duration.ofSeconds(1)),"http://localhost:19999");
        var proxyFactory=new ProxyFactory(target);proxyFactory.setProxyTargetClass(true);
        var proxy=(MerchantApiKeyVerifier)proxyFactory.getProxy();
        var fallback=FallbackMethod.create("verifyFallback",MerchantApiKeyVerifier.class.getMethod("verify",String.class),
                new Object[]{"sk_test_example"},MerchantApiKeyVerifier.class,proxy);
        assertThrows(ApiKeyVerifier.UnavailableException.class,()->fallback.fallback(new IllegalStateException("dependency unavailable")));
    }
}
