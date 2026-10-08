package com.payflow.ai.llm;

import com.payflow.ai.config.AiProperties;
import com.payflow.ai.credential.ByokCredentialStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmCredentialProviderTest {

    private final UUID userId = UUID.randomUUID();

    private AiProperties propsWithServerKey(String provider, String anthropicKey) {
        AiProperties props = new AiProperties();
        props.setDefaultProvider(provider);
        props.getServerKeys().setAnthropic(anthropicKey);
        return props;
    }

    @Test
    void userCredentialBeatsServerFallback() {
        ByokCredentialStore store = mock(ByokCredentialStore.class);
        LlmCredential userCredential = new LlmCredential(AiProvider.ANTHROPIC, "sk-ant-user", null);
        when(store.get(eq(userId), eq(AiProvider.ANTHROPIC))).thenReturn(Optional.of(userCredential));

        AiProperties props = propsWithServerKey("anthropic", "sk-ant-server-fallback");
        LlmCredentialProvider provider = new LlmCredentialProvider(store, props);

        Optional<ResolvedCredential> resolved = provider.resolve(userId, AiProvider.ANTHROPIC);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().source()).isEqualTo(CredentialSource.USER);
        assertThat(resolved.get().credential().apiKey()).isEqualTo("sk-ant-user");
    }

    @Test
    void serverFallbackUsedWhenNoUserCredential() {
        ByokCredentialStore store = mock(ByokCredentialStore.class);
        when(store.get(any(), any())).thenReturn(Optional.empty());

        AiProperties props = propsWithServerKey("anthropic", "sk-ant-server-fallback");
        LlmCredentialProvider provider = new LlmCredentialProvider(store, props);

        Optional<ResolvedCredential> resolved = provider.resolve(userId, AiProvider.ANTHROPIC);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().source()).isEqualTo(CredentialSource.SERVER);
        assertThat(resolved.get().credential().apiKey()).isEqualTo("sk-ant-server-fallback");
    }

    @Test
    void emptyWhenNeitherUserNorServerCredential() {
        ByokCredentialStore store = mock(ByokCredentialStore.class);
        when(store.get(any(), any())).thenReturn(Optional.empty());

        AiProperties props = new AiProperties();
        props.setDefaultProvider("openai");
        LlmCredentialProvider provider = new LlmCredentialProvider(store, props);

        assertThat(provider.resolve(userId, AiProvider.OPENAI)).isEmpty();
    }

    @Test
    void usesDefaultProviderWhenRequestedProviderIsNull() {
        ByokCredentialStore store = mock(ByokCredentialStore.class);
        when(store.get(any(), any())).thenReturn(Optional.empty());

        AiProperties props = propsWithServerKey("anthropic", "sk-ant-server-fallback");
        LlmCredentialProvider provider = new LlmCredentialProvider(store, props);

        Optional<ResolvedCredential> resolved = provider.resolve(userId, null);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().credential().provider()).isEqualTo(AiProvider.ANTHROPIC);
        assertThat(resolved.get().source()).isEqualTo(CredentialSource.SERVER);
    }

    @Test
    void emptyWhenNoProviderRequestedAndNoDefaultConfigured() {
        ByokCredentialStore store = mock(ByokCredentialStore.class);
        when(store.get(any(), any())).thenReturn(Optional.empty());

        LlmCredentialProvider provider = new LlmCredentialProvider(store, new AiProperties());

        assertThat(provider.resolve(userId, null)).isEmpty();
    }

    @Test
    void infersSingleConfiguredServerProviderWhenNoDefault() {
        ByokCredentialStore store = mock(ByokCredentialStore.class);
        when(store.get(any(), any())).thenReturn(Optional.empty());

        AiProperties props = new AiProperties();
        props.getServerKeys().setGemini("AIza-only-one-configured");
        LlmCredentialProvider provider = new LlmCredentialProvider(store, props);

        Optional<ResolvedCredential> resolved = provider.resolve(userId, null);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().credential().provider()).isEqualTo(AiProvider.GEMINI);
    }
}
