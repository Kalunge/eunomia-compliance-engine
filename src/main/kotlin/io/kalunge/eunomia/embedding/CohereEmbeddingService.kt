package io.kalunge.eunomia.embedding

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/**
 * Production implementation backed by Cohere via LangChain4j. Two
 * underlying models are injected — one configured with input_type
 * "search_document", the other with "search_query" — because Cohere v3
 * tunes the projection per use case.
 */
@Service
@Profile("!test")
class CohereEmbeddingService(
    @Qualifier("cohereDocumentEmbeddingModel") private val documentModel: EmbeddingModel,
    @Qualifier("cohereQueryEmbeddingModel") private val queryModel: EmbeddingModel,
) : EmbeddingService {

    override val dimension: Int = COHERE_V3_DIMENSION

    override fun embedDocuments(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val segments = texts.map { TextSegment.from(it) }
        val response = documentModel.embedAll(segments)
        return response.content().map { it.vector() }
    }

    override fun embedQuery(text: String): FloatArray =
        queryModel.embed(text).content().vector()

    companion object {
        const val COHERE_V3_DIMENSION = 1024
    }
}
