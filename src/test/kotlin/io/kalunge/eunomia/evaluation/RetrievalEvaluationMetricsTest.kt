package io.kalunge.eunomia.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RetrievalEvaluationMetricsTest {

    @Test
    fun `scores single relevant result at rank two`() {
        val score = RetrievalEvaluationMetrics.score(
            rankedChunkIds = listOf("distractor", "relevant", "other"),
            relevantChunkIds = setOf("relevant"),
        )

        assertEquals(0.0, score.recallAt1)
        assertEquals(1.0, score.recallAt3)
        assertEquals(0.5, score.reciprocalRank)
    }

    @Test
    fun `calculates recall for multiple relevant chunks`() {
        val score = RetrievalEvaluationMetrics.score(
            rankedChunkIds = listOf("relevant-a", "distractor", "relevant-b"),
            relevantChunkIds = setOf("relevant-a", "relevant-b"),
        )

        assertEquals(0.5, score.recallAt1)
        assertEquals(1.0, score.recallAt3)
        assertEquals(1.0, score.reciprocalRank)
    }

    @Test
    fun `detects cross-tenant results`() {
        val score = RetrievalEvaluationMetrics.score(
            rankedChunkIds = listOf("relevant", "other-tenant"),
            relevantChunkIds = setOf("relevant"),
            forbiddenChunkIds = setOf("other-tenant"),
        )

        assertEquals(0.0, score.tenantIsolation)
    }
}
