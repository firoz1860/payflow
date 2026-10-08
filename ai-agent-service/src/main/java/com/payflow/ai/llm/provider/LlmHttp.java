package com.payflow.ai.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.payflow.ai.llm.AiProvider;
import com.payflow.ai.llm.LlmException;
import com.payflow.ai.llm.LlmProviderException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Shared HTTP plumbing for the provider clients. Builds a per-call
 * {@link RestClient} with request-scoped timeouts and translates every
 * transport/HTTP failure into an {@link LlmException} that never carries the API
 * key, the authorization header, or the request URI (some providers embed the
 * key in the URI, so the underlying exception is deliberately never chained).
 */
final class LlmHttp {

    private static final int DEFAULT_TIMEOUT_MS = 45_000;

    private LlmHttp() {
    }

    static RestClient client(Duration timeout) {
        long millis = timeout == null ? DEFAULT_TIMEOUT_MS : Math.max(1L, timeout.toMillis());
        int clamped = (int) Math.min(millis, Integer.MAX_VALUE);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(clamped);
        factory.setReadTimeout(clamped);
        return RestClient.builder().requestFactory(factory).build();
    }

    static JsonNode postJson(RestClient client, URI uri, Object body, AiProvider provider,
                             Consumer<RestClient.RequestBodySpec> headers) {
        try {
            RestClient.RequestBodySpec spec = client.post().uri(uri);
            headers.accept(spec);
            return spec.body(body).retrieve().toEntity(JsonNode.class).getBody();
        } catch (RestClientResponseException ex) {
            throw new LlmProviderException(
                    provider + " provider returned an error response", ex.getStatusCode().value(), provider);
        } catch (ResourceAccessException ex) {
            throw new LlmProviderException(
                    provider + " provider request failed (timeout or network error)", 0, provider);
        } catch (RestClientException ex) {
            throw new LlmProviderException(provider + " provider request failed", 0, provider);
        }
    }

    static String requireText(JsonNode node, AiProvider provider) {
        if (node == null || !node.isTextual()) {
            throw new LlmException(provider + " provider returned an unexpected response shape");
        }
        return node.asText();
    }

    static Integer intOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.intValue() : null;
    }
}
