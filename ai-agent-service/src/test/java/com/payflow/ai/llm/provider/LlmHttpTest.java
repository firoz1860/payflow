package com.payflow.ai.llm.provider;

import com.payflow.ai.llm.AiProvider;
import com.payflow.ai.llm.LlmProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LlmHttpTest {
    @Test
    void decodesProviderJsonWithDefaultSpringSevenConverters() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://provider.example/chat"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"hello\"}}]}",
                        MediaType.APPLICATION_JSON));
        var response = LlmHttp.postJson(builder.build(), URI.create("https://provider.example/chat"),
                Map.of("model", "test"), AiProvider.OPENAI, spec -> spec.contentType(MediaType.APPLICATION_JSON));
        assertEquals("hello", response.path("choices").get(0).path("message").path("content").asText());
        server.verify();
    }

    @Test
    void malformedResponseDoesNotExposeBodyOrCredential() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://provider.example/chat"))
                .andRespond(withSuccess("private-response-secret", MediaType.APPLICATION_JSON));
        var error = assertThrows(LlmProviderException.class, () -> LlmHttp.postJson(builder.build(),
                URI.create("https://provider.example/chat"), Map.of("model", "test"), AiProvider.OPENAI,
                spec -> spec.header("Authorization", "Bearer private-credential")));
        assertFalse(error.getMessage().contains("private-response-secret"));
        assertFalse(error.getMessage().contains("private-credential"));
        assertNull(error.getCause());
        server.verify();
    }
}
