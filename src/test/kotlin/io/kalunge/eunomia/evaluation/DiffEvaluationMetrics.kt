package io.kalunge.eunomia.evaluation

import io.kalunge.eunomia.diff.DiffVerdict

data class DiffEvaluationInput(
    val baselines: List<DiffEvaluationObligation>,
    val candidates: List<DiffEvaluationCandidate>,
)

data class DiffEvaluationObligation(
    val id: String,
    val summary: String,
    val sourceExcerpt: String,
)

data class DiffEvaluationCandidate(
    val id: String,
    val summary: String,
    val sourceExcerpt: String,
    val baselineIndices: List<Int>,
)

data class ExpectedDiffOutput(
    val judgments: List<ExpectedDiffJudgment>,
    val removedBaselineIndices: Set<Int>,
)

data class ExpectedDiffJudgment(
    val candidateId: String,
    val verdict: DiffVerdict,
    val baselineIndex: Int?,
    val rationaleTermGroups: List<Set<String>> = emptyList(),
)

data class DiffCandidatePrediction(
    val candidateId: String,
    val verdict: DiffVerdict?,
    val baselineIndex: Int?,
    val rationale: String?,
    val parseSuccess: Boolean,
)

data class DiffEvaluationScore(
    val classificationAccuracy: Double,
    val macroF1: Double,
    val pairingAccuracy: Double,
    val removedPrecision: Double,
    val removedRecall: Double,
    val removedF1: Double,
    val rationaleCoverage: Double,
    val parseSuccess: Double,
    val predictedRemovedBaselineIndices: Set<Int>,
) {
    fun asMetrics(): Map<String, Double> = mapOf(
        "classificationAccuracy" to classificationAccuracy,
        "macroF1" to macroF1,
        "pairingAccuracy" to pairingAccuracy,
        "removedPrecision" to removedPrecision,
        "removedRecall" to removedRecall,
        "removedF1" to removedF1,
        "rationaleCoverage" to rationaleCoverage,
        "parseSuccess" to parseSuccess,
    )
}

object DiffEvaluationMetrics {

    private val nonAlphaNumeric = Regex("[^a-z0-9]+")
    private val whitespace = Regex("\\s+")

    fun score(
        baselineCount: Int,
        expected: ExpectedDiffOutput,
        predictions: List<DiffCandidatePrediction>,
    ): DiffEvaluationScore {
        require(baselineCount >= 0) { "Baseline count cannot be negative." }
        require(expected.removedBaselineIndices.all { it in 0 until baselineCount }) {
            "Expected removed indices must refer to supplied baselines."
        }

        val expectedByCandidate = expected.judgments.associateBy { it.candidateId }
        require(expectedByCandidate.size == expected.judgments.size) {
            "Expected diff judgments contain duplicate candidate IDs."
        }
        val predictionsByCandidate = predictions.associateBy { it.candidateId }
        require(predictionsByCandidate.keys == expectedByCandidate.keys) {
            "Diff predictions must cover every expected candidate."
        }

        val correctlyClassified = expected.judgments.count { expectedJudgment ->
            predictionsByCandidate.getValue(expectedJudgment.candidateId).verdict == expectedJudgment.verdict
        }
        val classificationAccuracy = correctlyClassified.toDouble() / expected.judgments.size

        val activeClasses = (
            expected.judgments.map { it.verdict } +
                predictions.mapNotNull { it.verdict }
            ).toSet()
        val macroF1 = activeClasses.map { verdict ->
            val truePositives = expected.judgments.count { expectedJudgment ->
                expectedJudgment.verdict == verdict &&
                    predictionsByCandidate.getValue(expectedJudgment.candidateId).verdict == verdict
            }
            val falsePositives = expected.judgments.count { expectedJudgment ->
                expectedJudgment.verdict != verdict &&
                    predictionsByCandidate.getValue(expectedJudgment.candidateId).verdict == verdict
            }
            val falseNegatives = expected.judgments.count { expectedJudgment ->
                expectedJudgment.verdict == verdict &&
                    predictionsByCandidate.getValue(expectedJudgment.candidateId).verdict != verdict
            }
            f1(truePositives, falsePositives, falseNegatives)
        }.average()

        val expectedPaired = expected.judgments.filter { it.verdict != DiffVerdict.ADDED }
        val correctlyPaired = expectedPaired.count { expectedJudgment ->
            val prediction = predictionsByCandidate.getValue(expectedJudgment.candidateId)
            prediction.verdict == expectedJudgment.verdict &&
                prediction.baselineIndex == expectedJudgment.baselineIndex
        }
        val pairingAccuracy = if (expectedPaired.isEmpty()) {
            1.0
        } else {
            correctlyPaired.toDouble() / expectedPaired.size
        }

        val claimedBaselineIndices = predictions
            .filter { it.verdict != null && it.verdict != DiffVerdict.ADDED }
            .mapNotNull { it.baselineIndex }
            .filter { it in 0 until baselineCount }
            .toSet()
        val predictedRemoved = (0 until baselineCount).toSet() - claimedBaselineIndices
        val removedTruePositives = (expected.removedBaselineIndices intersect predictedRemoved).size
        val removedFalsePositives = (predictedRemoved - expected.removedBaselineIndices).size
        val removedFalseNegatives = (expected.removedBaselineIndices - predictedRemoved).size
        val removedPrecision = ratio(
            removedTruePositives,
            removedTruePositives + removedFalsePositives,
            expected.removedBaselineIndices.isEmpty() && predictedRemoved.isEmpty(),
        )
        val removedRecall = ratio(
            removedTruePositives,
            removedTruePositives + removedFalseNegatives,
            expected.removedBaselineIndices.isEmpty() && predictedRemoved.isEmpty(),
        )
        val removedF1 = harmonicMean(removedPrecision, removedRecall)

        val modifiedJudgments = expected.judgments.filter { it.verdict == DiffVerdict.MODIFIED }
        val rationaleCoverage = if (modifiedJudgments.isEmpty()) {
            1.0
        } else {
            modifiedJudgments.count { expectedJudgment ->
                val rationale = normalize(
                    predictionsByCandidate.getValue(expectedJudgment.candidateId)
                        .rationale
                        .orEmpty(),
                )
                expectedJudgment.rationaleTermGroups.all { alternatives ->
                    alternatives.any { term -> rationale.contains(normalize(term)) }
                }
            }.toDouble() / modifiedJudgments.size
        }

        val parseSuccess = predictions.count { it.parseSuccess }.toDouble() / predictions.size

        return DiffEvaluationScore(
            classificationAccuracy = classificationAccuracy,
            macroF1 = macroF1,
            pairingAccuracy = pairingAccuracy,
            removedPrecision = removedPrecision,
            removedRecall = removedRecall,
            removedF1 = removedF1,
            rationaleCoverage = rationaleCoverage,
            parseSuccess = parseSuccess,
            predictedRemovedBaselineIndices = predictedRemoved,
        )
    }

    private fun f1(truePositives: Int, falsePositives: Int, falseNegatives: Int): Double {
        val precision = ratio(truePositives, truePositives + falsePositives, bothEmpty = false)
        val recall = ratio(truePositives, truePositives + falseNegatives, bothEmpty = false)
        return harmonicMean(precision, recall)
    }

    private fun harmonicMean(left: Double, right: Double): Double =
        if (left + right == 0.0) 0.0 else 2 * left * right / (left + right)

    private fun ratio(numerator: Int, denominator: Int, bothEmpty: Boolean): Double =
        when {
            bothEmpty -> 1.0
            denominator == 0 -> 0.0
            else -> numerator.toDouble() / denominator
        }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(nonAlphaNumeric, " ")
            .replace(whitespace, " ")
            .trim()
}
