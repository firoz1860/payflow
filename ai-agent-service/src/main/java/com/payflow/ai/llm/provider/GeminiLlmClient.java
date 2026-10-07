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
import java.util.Map;

/**
 * Google Gemini {@code generateContent} client. The key is passed as the
 * provider-required {@code key} query parameter; the request URI is never logged
 * and is never surfaced in an exception (see {@link LlmHttp}). ASSISTANT turns map
 * to Gemini's {@code model} role; the system prompt maps to {@code system_instruction}.
 */
@Component
public class GeminiLlmClient implements LlmClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    @Override
    public AiProvider provider() {
        return AiProvider.GEMINI;
    }

    @Override
    public LlmResponse complete(LlmCredential credential, LlmRequest request) {
        StringBuilder system = new StringBuilder();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            system.append(request.systemPrompt());
        }
        List<Map<String, Object>> contents = new ArrayList<>();
        if (request.messages() != null) {
            for (LlmMessage message : request.messages()) {
                if (message.role() == LlmMessage.Role.SYSTEM) {
                    if (system.length() > 0) {
                        system.append("\n\n");
                    }
                    system.append(message.content() == null ? "" : message.content());
                } else {
                    String role = message.role() == LlmMessage.Role.ASSISTANT ? "model" : "user";
                    contents.add(Map.of(
                            "role", role,
                            "parts", List.of(Map.of("text", message.content() == null ? "" : message.content()))));
                }
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (system.length() > 0) {
            body.put("system_instruction", Map.of("parts", List.of(Map.of("text", system.toString()))));
        }
        body.put("contents", contents);
        if (request.maxTokens() > 0) {
            body.put("generationConfig", Map.of("maxOutputTokens", request.maxTokens()));
        }

        URI uri = URI.create(BASE_URL + request.model() + ":generateContent?key=" + credential.apiKey());
        RestClient client = LlmHttp.client(request.timeout());
        JsonNode root = LlmHttp.postJson(client, uri, body, AiProvider.GEMINI,
                spec -> spec.contentType(MediaType.APPLICATION_JSON));

        JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        String content = LlmHttp.requireText(text, AiProvider.GEMINI);
        JsonNode usage = root.path("usageMetadata");
        return new LlmResponse(
                content,
                LlmHttp.intOrNull(usage.get("promptTokenCount")),
                LlmHttp.intOrNull(usage.get("candidatesTokenCount")),
                request.model());
    }
}
