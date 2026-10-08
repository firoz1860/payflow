package com.payflow.ai.llm;

/**
 * Test double returning a canned {@link LlmResponse}. Used so tests never call a
 * real provider API or need a real key.
 */
public class FakeLlmClient implements LlmClient {

    private final AiProvider provider;
    private final LlmResponse cannedResponse;

    public FakeLlmClient(AiProvider provider, LlmResponse cannedResponse) {
        this.provider = provider;
        this.cannedResponse = cannedResponse;
    }

    public FakeLlmClient(AiProvider provider) {
        this(provider, new LlmResponse("canned test answer", 10, 20, "fake-model"));
    }

    @Override
    public AiProvider provider() {
        return provider;
    }

    @Override
    public LlmResponse complete(LlmCredential credential, LlmRequest request) {
        return cannedResponse;
    }
}
