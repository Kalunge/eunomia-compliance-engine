package io.kalunge.eunomia.evaluation

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.openai.OpenAiChatModel
import java.time.Duration

data class EvaluationChatModel(
    val modelName: String,
    val chatModel: ChatLanguageModel,
)

object GroqEvaluationModelFactory {

    fun createFromEnvironment(): EvaluationChatModel? {
        val apiKey = System.getenv("GROQ_API_KEY").orEmpty()
        if (apiKey.isBlank()) return null

        val modelName = System.getenv("GROQ_MODEL")
            ?.takeIf { it.isNotBlank() }
            ?: "openai/gpt-oss-120b"
        val baseUrl = System.getenv("GROQ_BASE_URL")
            ?.takeIf { it.isNotBlank() }
            ?: "https://api.groq.com/openai/v1"
        val temperature = System.getenv("GROQ_TEMPERATURE")
            ?.toDoubleOrNull()
            ?: 0.1
        val timeoutSeconds = System.getenv("GROQ_TIMEOUT")
            ?.toLongOrNull()
            ?: 60

        return EvaluationChatModel(
            modelName = modelName,
            chatModel = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(5)
                .build(),
        )
    }
}
