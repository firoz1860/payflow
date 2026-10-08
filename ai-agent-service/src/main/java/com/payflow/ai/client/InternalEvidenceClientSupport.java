package com.payflow.ai.client;

import com.payflow.ai.config.AiProperties;
import com.payflow.common.client.ServiceClientFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * Shared plumbing for the typed internal-evidence clients. Each client gets a
 * {@link WebClient} built from the auto-provided {@link ServiceClientFactory}
 * (which attaches {@code X-Internal-Token} and propagates the correlation id),
 * capped to ~256KB of in-memory body so a hostile/huge response cannot exhaust
 * memory, and every call is bounded by {@link AiProperties#getRequestTimeoutSeconds()}.
 *
 * <p>A {@code 404} is mapped to {@link Optional#empty()} ("absent"), never an
 * exception; any other error status is raised so the circuit breaker counts it and
 * the per-client fallback returns an absent result instead of leaking a failure.
 */
abstract class InternalEvidenceClientSupport {

    private static final int MAX_IN_MEMORY_BYTES = 256 * 1024;

    private final WebClient webClient;
    private final Duration timeout;

    protected InternalEvidenceClientSupport(ServiceClientFactory factory, String baseUrl, AiProperties properties) {
        this.webClient = factory.create(baseUrl).mutate()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
        this.timeout = Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds()));
    }

    /** GET returning the typed body, or empty on 404. Blocks, bounded by the request timeout. */
    protected <T> Optional<T> getOptional(Class<T> type, String uriTemplate, Object... uriVariables) {
        return webClient.get()
                .uri(uriTemplate, uriVariables)
                .exchangeToMono(response -> {
                    int code = response.statusCode().value();
                    if (code == 404) {
                        return response.releaseBody().then(Mono.empty());
                    }
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return response.bodyToMono(type);
                })
                .blockOptional(timeout);
    }

    /**
     * GET with one required query parameter returning the typed body, or empty on 404.
     */
    protected <T> Optional<T> getOptional(Class<T> type, String uriTemplate, String queryName, String queryValue,
                                          Object... uriVariables) {
        return webClient.get()
                .uri(builder -> {
                    if (queryValue != null && !queryValue.isBlank()) {
                        builder.queryParam(queryName, queryValue);
                    }
                    return builder.path(uriTemplate).build(uriVariables);
                })
                .exchangeToMono(response -> {
                    int code = response.statusCode().value();
                    if (code == 404) {
                        return response.releaseBody().then(Mono.empty());
                    }
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return response.bodyToMono(type);
                })
                .blockOptional(timeout);
    }
}
