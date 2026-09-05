package io.kalunge.eunomia.evaluation

import io.kalunge.eunomia.mapping.MappingJudgment

data class MapperEvaluationInput(
    val obligation: MapperEvaluationObligation,
    val candidates: List<MapperEvaluationCandidate>,
)

data class MapperEvaluationObligation(
    val summary: String,
    val sourceExcerpt: String,
)

data class MapperEvaluationCandidate(
    val text: String,
    val distance: Double = 0.1,
)

data class ExpectedMapperOutput(
    val satisfiedIndices: Set<Int>,
)

data class MapperEvaluationScore(
    val expectedSatisfiedIndices: Set<Int>,
    val predictedSatisfiedIndices: Set<Int>,
    val truePositives: Int,
    val falsePositives: Int,
    val falseNegatives: Int,
    val precision: Double,
    val recall: Double,
    val f1: Double,
    val classificationAccuracy: Double,
    val judgmentCompleteness: Double,
    val brierScore: Double,
) {
    fun asMetrics(parseSuccess: Double = 1.0): Map<String, Double> = mapOf(
        "precision" to precision,
        "recall" to recall,
        "f1" to f1,
        "classificationAccuracy" to classificationAccuracy,
        "judgmentCompleteness" to judgmentCompleteness,
        "brierScore" to brierScore,
        "parseSuccess" to parseSuccess,
    )
}

object MapperEvaluationMetrics {

    fun score(
        candidateCount: Int,
        expectedSatisfiedIndices: Set<Int>,
        judgments: List<MappingJudgment>,
        confidenceThreshold: Double,
    ): MapperEvaluationScore {
        require(candidateCount > 0) { "Mapper evaluation requires at least one candidate." }
        require(expectedSatisfiedIndices.all { it in 0 until candidateCount }) {
            "Expected satisfied indices must refer to supplied candidates."
        }

        val judgmentsByIndex = judgments
            .filter { it.candidateIndex in 0 until candidateCount }
            .groupBy { it.candidateIndex }
            .mapValues { (_, values) -> values.maxBy { it.confidence } }

        val predictedSatisfiedIndices = judgmentsByIndex.values
            .filter { it.satisfied && it.confidence >= confidenceThreshold }
            .map { it.candidateIndex }
            .toSet()

        val truePositives = (expectedSatisfiedIndices intersect predictedSatisfiedIndices).size
        val falsePositives = (predictedSatisfiedIndices - expectedSatisfiedIndices).size
        val falseNegatives = (expectedSatisfiedIndices - predictedSatisfiedIndices).size
        val precision = ratio(
            numerator = truePositives,
            denominator = truePositives + falsePositives,
            bothEmpty = expectedSatisfiedIndices.isEmpty() && predictedSatisfiedIndices.isEmpty(),
        )
        val recall = ratio(
            numerator = truePositives,
            denominator = truePositives + falseNegatives,
            bothEmpty = expectedSatisfiedIndices.isEmpty() && predictedSatisfiedIndices.isEmpty(),
        )
        val f1 = if (precision + recall == 0.0) {
            0.0
        } else {
            2 * precision * recall / (precision + recall)
        }

        val correctClassifications = (0 until candidateCount).count { index ->
            (index in expectedSatisfiedIndices) == (index in predictedSatisfiedIndices)
        }
        val classificationAccuracy = correctClassifications.toDouble() / candidateCount
        val judgmentCompleteness = judgmentsByIndex.size.toDouble() / candidateCount
        val brierScore = (0 until candidateCount).map { index ->
            val judgment = judgmentsByIndex[index]
            val probabilitySatisfied = when {
                judgment == null -> 0.0
                judgment.satisfied -> judgment.confidence
                else -> 1.0 - judgment.confidence
            }
            val expected = if (index in expectedSatisfiedIndices) 1.0 else 0.0
            val error = probabilitySatisfied - expected
            error * error
        }.average()

        return MapperEvaluationScore(
            expectedSatisfiedIndices = expectedSatisfiedIndices,
            predictedSatisfiedIndices = predictedSatisfiedIndices,
            truePositives = truePositives,
            falsePositives = falsePositives,
            falseNegatives = falseNegatives,
            precision = precision,
            recall = recall,
            f1 = f1,
            classificationAccuracy = classificationAccuracy,
            judgmentCompleteness = judgmentCompleteness,
            brierScore = brierScore,
        )
    }

    private fun ratio(numerator: Int, denominator: Int, bothEmpty: Boolean): Double =
        when {
            bothEmpty -> 1.0
            denominator == 0 -> 0.0
            else -> numerator.toDouble() / denominator
        }
}
