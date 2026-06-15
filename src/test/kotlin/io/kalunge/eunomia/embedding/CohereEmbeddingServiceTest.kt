package io.kalunge.eunomia.embedding

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CohereEmbeddingServiceTest {

    private val documentModel: EmbeddingModel = mockk()
    private val queryModel: EmbeddingModel = mockk()
    private val service = CohereEmbeddingService(documentModel, queryModel)

    @Test
    fun `dimension is Cohere v3 default`() {
        assertThat(service.dimension).isEqualTo(1024)
    }

    @Test
    fun `embedDocuments routes through the document model`() {
        val captured = slot<List<TextSegment>>()
        val mockResponse: Response<List<Embedding>> = Response.from(
            listOf(
                Embedding.from(floatArrayOf(0.1f, 0.2f)),
                Embedding.from(floatArrayOf(0.3f, 0.4f)),
            ),
        )
        every { documentModel.embedAll(capture(captured)) } returns mockResponse

        val result = service.embedDocuments(listOf("hello", "world"))

        verify(exactly = 1) { documentModel.embedAll(any()) }
        assertThat(captured.captured.map { it.text() }).containsExactly("hello", "world")
        assertThat(result).hasSize(2)
        assertThat(result[0]).isEqualTo(floatArrayOf(0.1f, 0.2f))
        assertThat(result[1]).isEqualTo(floatArrayOf(0.3f, 0.4f))
    }

    @Test
    fun `embedQuery routes through the query model`() {
        val mockResponse: Response<Embedding> = Response.from(
            Embedding.from(floatArrayOf(0.7f, 0.8f)),
        )
        every { queryModel.embed(any<String>()) } returns mockResponse

        val result = service.embedQuery("regulatory obligation")

        verify(exactly = 1) { queryModel.embed("regulatory obligation") }
        assertThat(result).isEqualTo(floatArrayOf(0.7f, 0.8f))
    }

    @Test
    fun `empty batch short-circuits and never calls the model`() {
        val result = service.embedDocuments(emptyList())
        assertThat(result).isEmpty()
        verify(exactly = 0) { documentModel.embedAll(any()) }
    }
}
