package io.kalunge.eunomia.evaluation

import io.kalunge.eunomia.diff.DiffVerdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiffEvaluationMetricsTest {

    @Test
    fun `scores correct verdicts pairings rationales and removals`() {
        val expected = ExpectedDiffOutput(
            judgments = listOf(
                expected("same", DiffVerdict.UNCHANGED, baselineIndex = 0),
                expected(
                    "changed",
                    DiffVerdict.MODIFIED,
                    baselineIndex = 1,
                    rationaleTermGroups = listOf(
                        setOf("forty-eight"),
                        setOf("twenty-four"),
                    ),
                ),
                expected("new", DiffVerdict.ADDED, baselineIndex = null),
            ),
            removedBaselineIndices = setOf(2),
        )
        val score = DiffEvaluationMetrics.score(
            baselineCount = 3,
            expected = expected,
            predictions = listOf(
                prediction("same", DiffVerdict.UNCHANGED, 0, "Same requirement."),
                prediction(
                    "changed",
                    DiffVerdict.MODIFIED,
                    1,
                    "Deadline reduced from forty‑eight to twenty‑four hours.",
                ),
                prediction("new", DiffVerdict.ADDED, null, "No baseline equivalent."),
            ),
        )

        assertEquals(1.0, score.classificationAccuracy)
        assertEquals(1.0, score.macroF1)
        assertEquals(1.0, score.pairingAccuracy)
        assertEquals(1.0, score.removedF1)
        assertEquals(1.0, score.rationaleCoverage)
        assertEquals(setOf(2), score.predictedRemovedBaselineIndices)
    }

    @Test
    fun `penalizes wrong pairing and missing rationale values`() {
        val expected = ExpectedDiffOutput(
            judgments = listOf(
                expected(
                    "changed",
                    DiffVerdict.MODIFIED,
                    baselineIndex = 0,
                    rationaleTermGroups = listOf(setOf("50,000", "50000"), setOf("70,000", "70000")),
                ),
            ),
            removedBaselineIndices = setOf(1),
        )
        val score = DiffEvaluationMetrics.score(
            baselineCount = 2,
            expected = expected,
            predictions = listOf(
                prediction("changed", DiffVerdict.MODIFIED, 1, "The threshold changed."),
            ),
        )

        assertEquals(1.0, score.classificationAccuracy)
        assertEquals(0.0, score.pairingAccuracy)
        assertEquals(0.0, score.removedF1)
        assertEquals(0.0, score.rationaleCoverage)
    }

    private fun expected(
        candidateId: String,
        verdict: DiffVerdict,
        baselineIndex: Int?,
        rationaleTermGroups: List<Set<String>> = emptyList(),
    ) = ExpectedDiffJudgment(candidateId, verdict, baselineIndex, rationaleTermGroups)

    private fun prediction(
        candidateId: String,
        verdict: DiffVerdict,
        baselineIndex: Int?,
        rationale: String,
    ) = DiffCandidatePrediction(candidateId, verdict, baselineIndex, rationale, parseSuccess = true)
}
