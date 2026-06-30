package io.kalunge.eunomia.config

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.StreamingChatLanguageModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Duration

/**
 * LangChain4j wiring.
 *
 * The Extractor (and later Mapper/Impact/Summarizer) agents depend on
 * a ChatLanguageModel. In production that's GPT OSS 120B served by
 * Groq via the OpenAI-compatible endpoint — migrated from
 * llama-3.3-70b-versatile after Groq deprecated it in mid-2026. Tests
 * provide their own stub bean under the @Profile("test") guard so we
 * never call the real Groq API from CI.
 *
 * Why OpenAiChatModel for Groq:
 *   Groq exposes an OpenAI-compatible REST surface, so we can reuse
 *   LangChain4j's OpenAI adapter by overriding the baseUrl. Saves us a
 *   bespoke client and matches what the design doc says about strategy-
 *   patterned providers.
 */
@Configuration
@Profile("!test")
class LangChain4jConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun chatModel(
        @Value("\${eunomia.llm.groq.api-key:}") groqApiKey: String,
        @Value("\${eunomia.llm.groq.model:openai/gpt-oss-120b}") modelName: String,
        @Value("\${eunomia.llm.groq.base-url:https://api.groq.com/openai/v1}") baseUrl: String,
        @Value("\${eunomia.llm.groq.temperature:0.1}") temperature: Double,
        @Value("\${eunomia.llm.groq.timeout-seconds:60}") timeoutSeconds: Long,
    ): ChatLanguageModel {
        if (groqApiKey.isBlank()) {
            log.warn(
                "GROQ_API_KEY is not set. Extractor calls will fail at runtime. " +
                    "Set the env var in .env locally and in Render's Environment tab in production.",
            )
        }
        return OpenAiChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(groqApiKey.ifBlank { "missing-key" })
            .modelName(modelName)
            .temperature(temperature)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            // Default is 3 retries with short exponential backoff (~3s
            // cumulative). Groq's free-tier TPM rate-limit message hints
            // 8-14s; 5 retries gives 1+2+4+8+16 = ~31s headroom which
            // clears the rolling window. Tune lower on a paid tier.
            .maxRetries(5)
            .build()
    }

    /**
     * Streaming variant of the chat model. Same Groq endpoint, but the
     * SDK streams the response token-by-token via a callback handler
     * instead of returning the full text at the end. Used by the in-app
     * help assistant so users see tokens flow as they're generated.
     */
    @Bean
    fun streamingChatModel(
        @Value("\${eunomia.llm.groq.api-key:}") groqApiKey: String,
        @Value("\${eunomia.llm.groq.model:openai/gpt-oss-120b}") modelName: String,
        @Value("\${eunomia.llm.groq.base-url:https://api.groq.com/openai/v1}") baseUrl: String,
        @Value("\${eunomia.llm.groq.temperature:0.2}") temperature: Double,
        @Value("\${eunomia.llm.groq.timeout-seconds:60}") timeoutSeconds: Long,
    ): StreamingChatLanguageModel {
        return OpenAiStreamingChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(groqApiKey.ifBlank { "missing-key" })
            .modelName(modelName)
            .temperature(temperature)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()
    }
}
