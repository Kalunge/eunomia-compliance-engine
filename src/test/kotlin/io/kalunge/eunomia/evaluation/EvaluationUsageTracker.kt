package io.kalunge.eunomia.evaluation

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.output.Response
import io.kalunge.eunomia.embedding.EmbeddingService
import java.util.concurrent.atomic.AtomicLong

data class EvaluationUsageSnapshot(
    val chatCalls: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val documentEmbeddingRequests: Long,
    val embeddedDocuments: Long,
    val queryEmbeddingRequests: Long,
    val estimatedEmbeddingTokens: Long,
)

class MeteredChatLanguageModel(
    private val delegate: ChatLanguageModel,
) : ChatLanguageModel {

    private val chatCalls = AtomicLong()
    private val inputTokens = AtomicLong()
    private val outputTokens = AtomicLong()

    override fun generate(messages: MutableList<ChatMessage>): Response<AiMessage> {
        val response = delegate.generate(messages)
        val usage = response.tokenUsage()
        val measuredInput = usage?.inputTokenCount()?.toLong()
            ?: estimateTokens(messages.joinToString("\n"))
        val measuredOutput = usage?.outputTokenCount()?.toLong()
            ?: estimateTokens(response.content().text())
        chatCalls.incrementAndGet()
        inputTokens.addAndGet(measuredInput)
        outputTokens.addAndGet(measuredOutput)
        return response
    }

    override fun generate(userMessage: String): String {
        val response = delegate.generate(userMessage)
        chatCalls.incrementAndGet()
        inputTokens.addAndGet(estimateTokens(userMessage))
        outputTokens.addAndGet(estimateTokens(response))
        return response
    }

    fun reset() {
        chatCalls.set(0)
        inputTokens.set(0)
        outputTokens.set(0)
    }

    fun snapshot(): Triple<Long, Long, Long> =
        Triple(chatCalls.get(), inputTokens.get(), outputTokens.get())

    private fun estimateTokens(text: String): Long =
        ((text.length + 3) / 4).toLong()
}

class MeteredEmbeddingService(
    private val delegate: EmbeddingService,
) : EmbeddingService {

    private val documentRequests = AtomicLong()
    private val embeddedDocuments = AtomicLong()
    private val queryRequests = AtomicLong()
    private val estimatedTokens = AtomicLong()

    override val dimension: Int
        get() = delegate.dimension

    override fun embedDocuments(texts: List<String>): List<FloatArray> {
        documentRequests.incrementAndGet()
        embeddedDocuments.addAndGet(texts.size.toLong())
        estimatedTokens.addAndGet(texts.sumOf(::estimateTokens))
        return delegate.embedDocuments(texts)
    }

    override fun embedQuery(text: String): FloatArray {
        queryRequests.incrementAndGet()
        estimatedTokens.addAndGet(estimateTokens(text))
        return delegate.embedQuery(text)
    }

    fun reset() {
        documentRequests.set(0)
        embeddedDocuments.set(0)
        queryRequests.set(0)
        estimatedTokens.set(0)
    }

    fun snapshot(chat: MeteredChatLanguageModel): EvaluationUsageSnapshot {
        val (calls, input, output) = chat.snapshot()
        return EvaluationUsageSnapshot(
            chatCalls = calls,
            inputTokens = input,
            outputTokens = output,
            totalTokens = input + output,
            documentEmbeddingRequests = documentRequests.get(),
            embeddedDocuments = embeddedDocuments.get(),
            queryEmbeddingRequests = queryRequests.get(),
            estimatedEmbeddingTokens = estimatedTokens.get(),
        )
    }

    private fun estimateTokens(text: String): Long =
        ((text.length + 3) / 4).toLong()
}
