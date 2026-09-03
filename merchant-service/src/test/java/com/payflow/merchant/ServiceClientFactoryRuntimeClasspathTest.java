package com.payflow.merchant;

import com.payflow.common.client.ServiceClientFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class ServiceClientFactoryRuntimeClasspathTest {

    @Test
    void sharedClientFactoryCanResolveItsWebClientMethodTypes() {
        assertThatCode(ServiceClientFactory.class::getDeclaredMethods)
                .doesNotThrowAnyException();
    }
}
