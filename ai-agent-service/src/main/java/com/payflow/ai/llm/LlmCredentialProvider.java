package com.payflow.ai.llm;

import com.payflow.ai.config.AiProperties;
import com.payflow.ai.credential.ByokCredentialStore;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves which LLM credential to use for a request, in strict priority order:
 * <ol>
 *   <li>the authenticated user's ephemeral BYOK credential, if present;</li>
 *   <li>an optional server-owned env fallback for the provider;</li>
 *   <li>none — the caller surfaces {@code AI_KEY_REQUIRED}.</li>
 * </ol>
 * When no provider is requested, the configured default provider (or, failing
 * that, the single configured server key) is used.
 */
@Component
public class LlmCredentialProvider {

    private final ByokCredentialStore byokStore;
    private final AiProperties properties;

    public LlmCredentialProvider(ByokCredentialStore byokStore, AiProperties properties) {
        this.byokStore = byokStore;
        this.properties = properties;
    }

    public Optional<ResolvedCredential> resolve(UUID userId, AiProvider requestedProvider) {
        AiProvider provider = requestedProvider != null ? requestedProvider : defaultProvider().orElse(null);
        if (provider == null) {
            return Optional.empty();
        }

        if (userId != null) {
            Optional<LlmCredential> userCredential = byokStore.get(userId, provider);
            if (userCredential.isPresent()) {
                return Optional.of(new ResolvedCredential(userCredential.get(), CredentialSource.USER));
            }
        }

        return serverFallback(provider)
                .map(key -> new ResolvedCredential(new LlmCredential(provider, key, null), CredentialSource.SERVER));
    }

    private Optional<AiProvider> defaultProvider() {
        Optional<AiProvider> configured = AiProvider.fromString(properties.getDefaultProvider());
        if (configured.isPresent()) {
            return configured;
        }
        return singleConfiguredServerProvider();
    }

    /** If exactly one server key is configured, treat it as the implicit default provider. */
    private Optional<AiProvider> singleConfiguredServerProvider() {
        AiProvider found = null;
        for (AiProvider provider : new AiProvider[]{AiProvider.ANTHROPIC, AiProvider.OPENAI, AiProvider.GEMINI, AiProvider.XAI}) {
            if (serverFallback(provider).isPresent()) {
                if (found != null) {
                    return Optional.empty();
                }
                found = provider;
            }
        }
        return Optional.ofNullable(found);
    }

    private Optional<String> serverFallback(AiProvider provider) {
        AiProperties.ProviderKeys keys = properties.getServerKeys();
        String key = switch (provider) {
            case ANTHROPIC -> keys.getAnthropic();
            case OPENAI -> keys.getOpenai();
            case GEMINI -> keys.getGemini();
            case XAI -> keys.getXai();
            case CUSTOM_OPENAI_COMPATIBLE -> null;
        };
        return key == null || key.isBlank() ? Optional.empty() : Optional.of(key);
    }
}
