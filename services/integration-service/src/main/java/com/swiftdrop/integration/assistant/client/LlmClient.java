package com.swiftdrop.integration.assistant.client;

/**
 * Interface abstracting the LLM client (e.g. Claude API implementation or Fake for testing).
 */
public interface LlmClient {

    /**
     * Send prompt to the LLM with tool declarations and return conversational reply.
     *
     * @param prompt The user's input query
     * @param conversationId Optional identifier to continue conversation context
     * @param callerToken JWT token of the authenticated user to pass through to internal tools
     * @return Generated text response from the assistant
     */
    String chat(String prompt, String conversationId, String callerToken);
}
