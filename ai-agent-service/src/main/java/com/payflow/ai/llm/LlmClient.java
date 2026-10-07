package com.payflow.ai.llm;

/**
 * Provider-neutral completion client. Implementations build their HTTP client
 * per call from the supplied {@link LlmCredential} so no key is ever retained on
 * a shared bean, and translate transport/HTTP failures into {@link LlmException}
 * subtypes without leaking the key or authorization header.
 */
public interface LlmClient {

    AiProvider provider();

    LlmResponse complete(LlmCredential credential, LlmRequest request);
}
