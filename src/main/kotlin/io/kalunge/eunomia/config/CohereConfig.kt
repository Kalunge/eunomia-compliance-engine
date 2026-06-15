package io.kalunge.eunomia.config

import dev.langchain4j.model.cohere.CohereEmbeddingModel
import dev.langchain4j.model.embedding.EmbeddingModel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Duration

/**
 * Cohere wiring for the embedding layer.
 *
 * Two beans are exposed because Cohere v3 has separate input_type
 * settings — "search_document" for text being indexed and
 * "search_query" for retrieval — and the model has to be built once
 * per type. The CohereEmbeddingService injects both and routes calls
 * appropriately.
 *
 * Profile("!test") keeps the real provider out of CI; tests register a
 * StubEmbeddingService bean instead.
 */
@Configuration
@Profile("!test")
class CohereConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun cohereDocumentEmbeddingModel(
        @Value("\${eunomia.llm.cohere.api-key:}") apiKey: String,
        @Value("\${eunomia.llm.cohere.model:embed-english-v3.0}") modelName: String,
        @Value("\${eunomia.llm.cohere.timeout-seconds:60}") timeoutSeconds: Long,
    ): EmbeddingModel = buildModel(apiKey, modelName, "search_document", timeoutSeconds)

    @Bean
    fun cohereQueryEmbeddingModel(
        @Value("\${eunomia.llm.cohere.api-key:}") apiKey: String,
        @Value("\${eunomia.llm.cohere.model:embed-english-v3.0}") modelName: String,
        @Value("\${eunomia.llm.cohere.timeout-seconds:60}") timeoutSeconds: Long,
    ): EmbeddingModel = buildModel(apiKey, modelName, "search_query", timeoutSeconds)

    private fun buildModel(
        apiKey: String,
        modelName: String,
        inputType: String,
        timeoutSeconds: Long,
    ): EmbeddingModel {
        if (apiKey.isBlank()) {
            log.warn(
                "COHERE_API_KEY is not set. Policy embedding calls will fail at runtime. " +
                    "Set the env var in .env locally and in Render's Environment tab in production.",
            )
        }
        return CohereEmbeddingModel.builder()
            .apiKey(apiKey.ifBlank { "missing-key" })
            .modelName(modelName)
            .inputType(inputType)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()
    }
}
