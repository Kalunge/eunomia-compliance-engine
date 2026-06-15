package io.kalunge.eunomia.extraction

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.output.Response

/**
 * Deterministic ChatLanguageModel for tests. Returns a fixed string on
 * every call regardless of input. No network, no Groq, no quota burn.
 *
 * Used either directly (unit tests) or as a Spring bean override in
 * the test profile (integration tests). Either way the production code
 * doesn't know it's been swapped — that's the strategy pattern doing
 * its job.
 */
class StubChatLanguageModel(private val cannedResponse: String) : ChatLanguageModel {

    var lastMessages: List<ChatMessage> = emptyList()
        private set

    override fun generate(messages: MutableList<ChatMessage>): Response<AiMessage> {
        lastMessages = messages.toList()
        return Response.from(AiMessage.from(cannedResponse))
    }

    override fun generate(userMessage: String): String = cannedResponse
}
