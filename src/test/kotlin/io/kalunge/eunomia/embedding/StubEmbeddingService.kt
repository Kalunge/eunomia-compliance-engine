package io.kalunge.eunomia.embedding

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Deterministic offline embeddings for tests. Same input string always
 * produces the same vector, vectors are L2-normalised so cosine
 * arithmetic behaves like real Cohere output, and there are no
 * network calls.
 *
 * Quality is intentionally low — these vectors don't carry semantic
 * meaning; they're just stable. Tests that need to assert "this query
 * found this chunk" should rely on string equality (or pass the same
 * string to both sides), not on semantic similarity.
 */
class StubEmbeddingService(
    override val dimension: Int = 1024,
) : EmbeddingService {

    override fun embedDocuments(texts: List<String>): List<FloatArray> =
        texts.map { generate(it) }

    override fun embedQuery(text: String): FloatArray = generate(text)

    private fun generate(text: String): FloatArray {
        val rng = Random(text.hashCode().toLong())
        val raw = FloatArray(dimension) { rng.nextFloat() * 2 - 1 }
        var sumOfSquares = 0.0
        for (v in raw) sumOfSquares += v * v
        val norm = sqrt(sumOfSquares).toFloat()
        return FloatArray(dimension) { raw[it] / norm }
    }
}
