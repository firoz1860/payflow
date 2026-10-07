package com.payflow.ai.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.payflow.ai.llm.AiProvider;
import com.payflow.ai.llm.LlmCredential;
import com.payflow.ai.llm.LlmMessage;
import com.payflow.ai.llm.LlmRequest;
import com.payflow.ai.llm.LlmResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The OpenAI {@code /chat/completions} exchange, shared by OpenAI itself, xAI,
 * and any custom OpenAI-compatible endpoint. Stateless; the base URL and provider
 * are supplied per call so no credential or endpoint is retained.
 */
final class OpenAiCompatibleCompletions {

    private OpenAiCompatibleCompletions() {
    }

    static LlmResponse complete(AiProvider provider, String baseUrl, LlmCredential credential, LlmRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        if (request.messages() != null) {
            for (LlmMessage message : request.messages()) {
                messages.add(Map.of(
                        "role", message.role().name().toLowerCase(Locale.ROOT),
                        "content", message.content() == null ? "" : message.content()));
            }
        }

        Map<String, Object> body = Map.of(
                "model", request.model(),
                "max_tokens", request.maxTokens(),
                "messages", messages);

        URI uri = URI.create(stripTrailingSlash(baseUrl) + "/chat/completions");
        RestClient client = LlmHttp.client(request.timeout());
        JsonNode root = LlmHttp.postJson(client, uri, body, provider, spec -> spec
                .header("Authorization", "Bearer " + credential.apiKey())
                .contentType(MediaType.APPLICATION_JSON));

        JsonNode message = root.path("choices").path(0).path("message").path("content");
        String content = LlmHttp.requireText(message, provider);
        JsonNode usage = root.path("usage");
        String model = root.path("model").isTextual() ? root.path("model").asText() : request.model();
        return new LlmResponse(
                content,
                LlmHttp.intOrNull(usage.get("prompt_tokens")),
                LlmHttp.intOrNull(usage.get("completion_tokens")),
                model);
    }

    static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
