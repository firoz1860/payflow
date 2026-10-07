package com.payflow.ai.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.payflow.ai.llm.AiProvider;
import com.payflow.ai.llm.LlmClient;
import com.payflow.ai.llm.LlmCredential;
import com.payflow.ai.llm.LlmMessage;
import com.payflow.ai.llm.LlmRequest;
import com.payflow.ai.llm.LlmResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Anthropic Messages API client. The system prompt and any SYSTEM-role messages
 * map to the top-level {@code system} field; USER/ASSISTANT turns map to the
 * {@code messages} array.
 */
@Component
public class AnthropicLlmClient implements LlmClient {

    private static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    public AiProvider provider() {
        return AiProvider.ANTHROPIC;
    }

    @Override
    public LlmResponse complete(LlmCredential credential, LlmRequest request) {
        StringBuilder system = new StringBuilder();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            system.append(request.systemPrompt());
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.messages() != null) {
            for (LlmMessage message : request.messages()) {
                if (message.role() == LlmMessage.Role.SYSTEM) {
                    if (system.length() > 0) {
                        system.append("\n\n");
                    }
                    system.append(message.content() == null ? "" : message.content());
                } else {
                    messages.add(Map.of(
                            "role", message.role().name().toLowerCase(Locale.ROOT),
                            "content", message.content() == null ? "" : message.content()));
                }
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("max_tokens", request.maxTokens());
        if (system.length() > 0) {
            body.put("system", system.toString());
        }
        body.put("messages", messages);

        RestClient client = LlmHttp.client(request.timeout());
        JsonNode root = LlmHttp.postJson(client, ENDPOINT, body, AiProvider.ANTHROPIC, spec -> spec
                .header("x-api-key", credential.apiKey())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .contentType(MediaType.APPLICATION_JSON));

        JsonNode text = root.path("content").path(0).path("text");
        String content = LlmHttp.requireText(text, AiProvider.ANTHROPIC);
        JsonNode usage = root.path("usage");
        String model = root.path("model").isTextual() ? root.path("model").asText() : request.model();
        return new LlmResponse(
                content,
                LlmHttp.intOrNull(usage.get("input_tokens")),
                LlmHttp.intOrNull(usage.get("output_tokens")),
                model);
    }
}
