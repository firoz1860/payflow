package com.payflow.ai.credential;

import com.payflow.ai.config.AiProperties;
import com.payflow.ai.llm.AiProvider;
import com.payflow.ai.llm.LlmCredential;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ByokCredentialStoreTest {

    /** A clock the test can advance deterministically. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            this.now = this.now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private AiProperties propsWithTtl(int minutes) {
        AiProperties props = new AiProperties();
        props.setByokTtlMinutes(minutes);
        return props;
    }

    @Test
    void storeThenGetReturnsCredential() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-07T10:00:00Z"));
        ByokCredentialStore store = new ByokCredentialStore(propsWithTtl(60), clock);
        UUID userId = UUID.randomUUID();
        LlmCredential credential = new LlmCredential(AiProvider.ANTHROPIC, "sk-ant-secret", null);

        store.store(userId, credential);

        assertThat(store.get(userId, AiProvider.ANTHROPIC)).contains(credential);
        assertThat(store.providers(userId)).containsExactly(AiProvider.ANTHROPIC);
        assertThat(store.expiresAt(userId, AiProvider.ANTHROPIC))
                .isEqualTo(Instant.parse("2026-10-07T11:00:00Z"));
    }

    @Test
    void getReturnsEmptyAfterTtlExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-07T10:00:00Z"));
        ByokCredentialStore store = new ByokCredentialStore(propsWithTtl(60), clock);
        UUID userId = UUID.randomUUID();
        store.store(userId, new LlmCredential(AiProvider.OPENAI, "sk-secret", null));

        clock.advance(Duration.ofMinutes(61));

        assertThat(store.get(userId, AiProvider.OPENAI)).isEmpty();
        assertThat(store.providers(userId)).isEmpty();
        assertThat(store.expiresAt(userId, AiProvider.OPENAI)).isNull();
    }

    @Test
    void removeErasesCredential() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-07T10:00:00Z"));
        ByokCredentialStore store = new ByokCredentialStore(propsWithTtl(60), clock);
        UUID userId = UUID.randomUUID();
        store.store(userId, new LlmCredential(AiProvider.GEMINI, "AIzaSecretValue1234", null));

        store.remove(userId, AiProvider.GEMINI);

        assertThat(store.get(userId, AiProvider.GEMINI)).isEmpty();
    }

    @Test
    void sweepEvictsExpiredEntries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-07T10:00:00Z"));
        ByokCredentialStore store = new ByokCredentialStore(propsWithTtl(30), clock);
        UUID userId = UUID.randomUUID();
        store.store(userId, new LlmCredential(AiProvider.XAI, "xai-secret", null));

        clock.advance(Duration.ofMinutes(31));
        store.sweep();

        assertThat(store.providers(userId)).isEmpty();
    }

    @Test
    void credentialToStringNeverLeaksKey() {
        LlmCredential credential = new LlmCredential(AiProvider.ANTHROPIC, "sk-ant-super-secret-value", null);
        assertThat(credential.toString()).doesNotContain("sk-ant-super-secret-value");
        assertThat(credential.toString()).contains("apiKey=***");
    }
}
