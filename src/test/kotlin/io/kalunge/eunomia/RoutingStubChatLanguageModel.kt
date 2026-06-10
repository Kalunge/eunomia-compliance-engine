package io.kalunge.eunomia

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.output.Response

/**
 * Smart test stub. Inspects the system message to decide which canned
 * response to return — so the same bean can serve both the Extractor
 * (which is told "you are a banking compliance analyst") and the
 * Mapper (which is told "you are a banking compliance reviewer") in
 * the same Spring context.
 *
 * Bypassing this routing would require either two ChatLanguageModel
 * beans (with @Qualifier wiring on the agents) or per-test stubs. The
 * routing approach keeps the production wiring unchanged.
 */
class RoutingStubChatLanguageModel(
    private val extractorResponse: String,
    private val mapperResponse: String,
) : ChatLanguageModel {

    override fun generate(messages: MutableList<ChatMessage>): Response<AiMessage> {
        val systemText = messages.firstOrNull { it is SystemMessage }
            ?.let { (it as SystemMessage).text() }
            ?: ""
        val response = when {
            systemText.contains("compliance reviewer", ignoreCase = true) -> mapperResponse
            systemText.contains("compliance analyst", ignoreCase = true) -> extractorResponse
            else -> """{ "obligations": [] }"""
        }
        return Response.from(AiMessage.from(response))
    }

    override fun generate(userMessage: String): String = extractorResponse
}
