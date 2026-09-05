package io.kalunge.eunomia.evaluation

import io.kalunge.eunomia.mapping.MappingJudgment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MapperEvaluationMetricsTest {

    @Test
    fun `scores thresholded mapping decisions`() {
        val score = MapperEvaluationMetrics.score(
            candidateCount = 3,
            expectedSatisfiedIndices = setOf(1),
            judgments = listOf(
                judgment(index = 0, satisfied = false, confidence = 0.9),
                judgment(index = 1, satisfied = true, confidence = 0.85),
                judgment(index = 2, satisfied = true, confidence = 0.4),
            ),
            confidenceThreshold = 0.6,
        )

        assertEquals(setOf(1), score.predictedSatisfiedIndices)
        assertEquals(1.0, score.precision)
        assertEquals(1.0, score.recall)
        assertEquals(1.0, score.f1)
        assertEquals(1.0, score.classificationAccuracy)
        assertEquals(1.0, score.judgmentCompleteness)
    }

    @Test
    fun `reports false positives and missing judgments`() {
        val score = MapperEvaluationMetrics.score(
            candidateCount = 2,
            expectedSatisfiedIndices = emptySet(),
            judgments = listOf(
                judgment(index = 0, satisfied = true, confidence = 0.95),
            ),
            confidenceThreshold = 0.6,
        )

        assertEquals(setOf(0), score.predictedSatisfiedIndices)
        assertEquals(1, score.falsePositives)
        assertEquals(0.0, score.f1)
        assertEquals(0.5, score.classificationAccuracy)
        assertEquals(0.5, score.judgmentCompleteness)
    }

    @Test
    fun `calculates Brier score from confidence in each verdict`() {
        val score = MapperEvaluationMetrics.score(
            candidateCount = 2,
            expectedSatisfiedIndices = setOf(0),
            judgments = listOf(
                judgment(index = 0, satisfied = true, confidence = 0.8),
                judgment(index = 1, satisfied = false, confidence = 0.9),
            ),
            confidenceThreshold = 0.6,
        )

        assertEquals(0.025, score.brierScore, 0.000001)
    }

    private fun judgment(
        index: Int,
        satisfied: Boolean,
        confidence: Double,
    ) = MappingJudgment(
        candidateIndex = index,
        satisfied = satisfied,
        confidence = confidence,
        reasoning = null,
    )
}
