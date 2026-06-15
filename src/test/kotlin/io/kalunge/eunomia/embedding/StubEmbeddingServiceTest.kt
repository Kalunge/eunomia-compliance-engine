package io.kalunge.eunomia.embedding

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

class StubEmbeddingServiceTest {

    private val service = StubEmbeddingService()

    @Test
    fun `dimension matches Cohere v3`() {
        assertThat(service.dimension).isEqualTo(1024)
    }

    @Test
    fun `same input produces the same vector`() {
        val a = service.embedQuery("regulatory obligation")
        val b = service.embedQuery("regulatory obligation")
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `different inputs produce different vectors`() {
        val a = service.embedQuery("regulatory obligation")
        val b = service.embedQuery("agent KYC procedure")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `vectors are L2-normalised`() {
        val v = service.embedQuery("any policy text")
        var sumOfSquares = 0.0
        for (x in v) sumOfSquares += x * x
        val length = sqrt(sumOfSquares)
        assertThat(abs(length - 1.0)).isLessThan(1e-6)
    }

    @Test
    fun `embedDocuments preserves order`() {
        val texts = listOf("alpha", "beta", "gamma")
        val vectors = service.embedDocuments(texts)
        assertThat(vectors).hasSize(3)
        assertThat(vectors[0]).isEqualTo(service.embedQuery("alpha"))
        assertThat(vectors[1]).isEqualTo(service.embedQuery("beta"))
        assertThat(vectors[2]).isEqualTo(service.embedQuery("gamma"))
    }

    @Test
    fun `empty batch returns empty list`() {
        assertThat(service.embedDocuments(emptyList())).isEmpty()
    }
}
