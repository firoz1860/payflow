package com.payflow.ai.credential;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretMaskerTest {

    @Test
    void masksLongKeyKeepingFirstThreeAndLastFour() {
        assertThat(SecretMasker.mask("sk-ant-api03-abcdefgh93ab")).isEqualTo("sk-...93ab");
    }

    @Test
    void returnsPlaceholderForNull() {
        assertThat(SecretMasker.mask(null)).isEqualTo("***");
    }

    @Test
    void returnsPlaceholderForShortValue() {
        assertThat(SecretMasker.mask("short")).isEqualTo("***");
        assertThat(SecretMasker.mask("1234567")).isEqualTo("***");
    }

    @Test
    void masksExactEightCharacterKey() {
        assertThat(SecretMasker.mask("12345678")).isEqualTo("123...5678");
    }

    @Test
    void neverExposesTheMiddleOfTheKey() {
        String key = "sk-live-THISMUSTNOTAPPEAR-tail";
        assertThat(SecretMasker.mask(key)).doesNotContain("THISMUSTNOTAPPEAR");
    }
}
