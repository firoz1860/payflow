package com.payflow.ai.credential;

import com.payflow.ai.config.AiProperties;
import com.payflow.ai.credential.dto.CredentialMetadata;
import com.payflow.ai.credential.dto.CredentialRequest;
import com.payflow.ai.llm.AiProvider;
import com.payflow.ai.llm.LlmCredential;
import com.payflow.ai.llm.SsrfGuard;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.security.PayFlowPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * BYOK credential management. The user submits an LLM key once over HTTPS; it is
 * stored ephemerally in memory, keyed by the authenticated user id (taken from the
 * verified JWT, never the body), and never returned to the browser. Every method
 * requires {@code ai:use}.
 */
@RestController
@RequestMapping("/api/v1/ai/credentials")
@Tag(name = "AI Credentials")
public class CredentialController {

    private final ByokCredentialStore store;
    private final SsrfGuard ssrfGuard;
    private final AiProperties properties;

    public CredentialController(ByokCredentialStore store, SsrfGuard ssrfGuard, AiProperties properties) {
        this.store = store;
        this.ssrfGuard = ssrfGuard;
        this.properties = properties;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "Store a BYOK LLM credential ephemerally; returns safe metadata only")
    public CredentialMetadata store(@AuthenticationPrincipal PayFlowPrincipal principal,
                                    @Valid @RequestBody CredentialRequest request) {
        UUID userId = requireUserId(principal);
        AiProvider provider = AiProvider.fromString(request.provider())
                .orElseThrow(() -> PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Unknown LLM provider: " + request.provider()));

        String baseUrl = request.baseUrl();
        if (provider == AiProvider.CUSTOM_OPENAI_COMPATIBLE) {
            ssrfGuard.validateBaseUrl(baseUrl);
        } else {
            baseUrl = null;
        }

        LlmCredential credential = new LlmCredential(provider, request.apiKey(), baseUrl);
        store.store(userId, credential);
        return new CredentialMetadata(
                providerName(provider), true, "USER",
                SecretMasker.mask(request.apiKey()), store.expiresAt(userId, provider));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "List credential status per provider (user BYOK and server fallbacks); never returns keys")
    public List<CredentialMetadata> list(@AuthenticationPrincipal PayFlowPrincipal principal) {
        UUID userId = requireUserId(principal);
        List<CredentialMetadata> result = new ArrayList<>();
        for (AiProvider provider : AiProvider.values()) {
            Optional<LlmCredential> userCredential = store.get(userId, provider);
            if (userCredential.isPresent()) {
                result.add(new CredentialMetadata(providerName(provider), true, "USER",
                        SecretMasker.mask(userCredential.get().apiKey()), store.expiresAt(userId, provider)));
            } else if (hasServerKey(provider)) {
                result.add(new CredentialMetadata(providerName(provider), true, "SERVER", "server-configured", null));
            } else {
                result.add(new CredentialMetadata(providerName(provider), false, null, null, null));
            }
        }
        return result;
    }

    @DeleteMapping("/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "Remove the user's BYOK credential for a provider")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal PayFlowPrincipal principal,
                                       @PathVariable String provider) {
        UUID userId = requireUserId(principal);
        AiProvider parsed = AiProvider.fromString(provider)
                .orElseThrow(() -> PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Unknown LLM provider: " + provider));
        store.remove(userId, parsed);
        return ResponseEntity.noContent().build();
    }

    private boolean hasServerKey(AiProvider provider) {
        AiProperties.ProviderKeys keys = properties.getServerKeys();
        String key = switch (provider) {
            case ANTHROPIC -> keys.getAnthropic();
            case OPENAI -> keys.getOpenai();
            case GEMINI -> keys.getGemini();
            case XAI -> keys.getXai();
            case CUSTOM_OPENAI_COMPATIBLE -> null;
        };
        return key != null && !key.isBlank();
    }

    private static String providerName(AiProvider provider) {
        return provider.name().toLowerCase(Locale.ROOT);
    }

    private static UUID requireUserId(PayFlowPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw PayFlowException.unauthorized("Authentication required");
        }
        return principal.userId();
    }
}
