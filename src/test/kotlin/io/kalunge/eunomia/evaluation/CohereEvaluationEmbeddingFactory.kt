package io.kalunge.eunomia.evaluation

import dev.langchain4j.model.cohere.CohereEmbeddingModel
import io.kalunge.eunomia.embedding.CohereEmbeddingService
import io.kalunge.eunomia.embedding.EmbeddingService
import java.time.Duration

data class EvaluationEmbeddingModel(
    val modelName: String,
    val embeddingService: EmbeddingService,
)

object CohereEvaluationEmbeddingFactory {

    fun createFromEnvironment(): EvaluationEmbeddingModel? {
        val apiKey = System.getenv("COHERE_API_KEY").orEmpty()
        if (apiKey.isBlank()) return null

        val modelName = System.getenv("COHERE_MODEL")
            ?.takeIf { it.isNotBlank() }
            ?: "embed-english-v3.0"
        val timeoutSeconds = System.getenv("COHERE_TIMEOUT")
            ?.toLongOrNull()
            ?: 60

        val documentModel = CohereEmbeddingModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .inputType("search_document")
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()
        val queryModel = CohereEmbeddingModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .inputType("search_query")
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()

        return EvaluationEmbeddingModel(
            modelName = modelName,
            embeddingService = CohereEmbeddingService(documentModel, queryModel),
        )
    }
}
