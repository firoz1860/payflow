package com.payflow.ai.llm;

import com.payflow.common.error.PayFlowException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsrfGuardTest {

    private final SsrfGuard guard = new SsrfGuard();

    @Test
    void allowsHttpsPublicHost() {
        // IP literal → no DNS lookup; 8.8.8.8 is a public, non-internal address.
        assertThatCode(() -> guard.validateBaseUrl("https://8.8.8.8/v1")).doesNotThrowAnyException();
    }

    @Test
    void rejectsHttp() {
        assertThatThrownBy(() -> guard.validateBaseUrl("http://8.8.8.8/v1"))
                .isInstanceOf(PayFlowException.class);
    }

    @Test
    void rejectsLoopbackIp() {
        assertThatThrownBy(() -> guard.validateBaseUrl("https://127.0.0.1/v1"))
                .isInstanceOf(PayFlowException.class);
    }

    @Test
    void rejectsLocalhost() {
        assertThatThrownBy(() -> guard.validateBaseUrl("https://localhost/v1"))
                .isInstanceOf(PayFlowException.class);
    }

    @Test
    void rejectsPrivateClassA() {
        assertThatThrownBy(() -> guard.validateBaseUrl("https://10.0.0.5/v1"))
                .isInstanceOf(PayFlowException.class);
    }

    @Test
    void rejectsPrivateClassC() {
        assertThatThrownBy(() -> guard.validateBaseUrl("https://192.168.1.10/v1"))
                .isInstanceOf(PayFlowException.class);
    }

    @Test
    void rejectsCloudMetadataAddress() {
        assertThatThrownBy(() -> guard.validateBaseUrl("https://169.254.169.254/latest/meta-data"))
                .isInstanceOf(PayFlowException.class);
    }

    @Test
    void rejectsGoogleMetadataHost() {
        assertThatThrownBy(() -> guard.validateBaseUrl("https://metadata.google.internal/computeMetadata/v1"))
                .isInstanceOf(PayFlowException.class);
    }

    @Test
    void rejectsBlankUrl() {
        assertThatThrownBy(() -> guard.validateBaseUrl("  ")).isInstanceOf(PayFlowException.class);
    }

    @Test
    void errorMessageNeverLeaksInternals() {
        PayFlowException ex = org.junit.jupiter.api.Assertions.assertThrows(
                PayFlowException.class, () -> guard.validateBaseUrl("https://10.0.0.5/v1"));
        assertThat(ex.getMessage()).isNotBlank();
    }
}
