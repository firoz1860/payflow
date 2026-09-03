package com.payflow.common.client;
import com.payflow.common.security.InternalAuthFilter;
import com.payflow.common.web.CorrelationId;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;
public class ServiceClientFactory {
    private final String internalToken;
    private final Duration responseTimeout;
    private final Duration connectTimeout;
    public ServiceClientFactory(String internalToken, Duration responseTimeout, Duration connectTimeout) {
        this.internalToken = internalToken;
        this.responseTimeout = responseTimeout;
        this.connectTimeout = connectTimeout;
    }
    public WebClient create(String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(responseTimeout)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) connectTimeout.toMillis());
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(InternalAuthFilter.HEADER, internalToken)
                .filter(correlationIdPropagation())
                .build();
    }
    private ExchangeFilterFunction correlationIdPropagation() {
        return (request, next) -> {
            String correlationId = CorrelationId.get();
            if (correlationId == null) {
                return next.exchange(request);
            }
            ClientRequest mutated = ClientRequest.from(request)
                    .header(CorrelationId.HEADER, correlationId)
                    .build();
            return next.exchange(mutated);
        };
    }
    public static <T> Mono<T> empty() {
        return Mono.empty();
    }
}
