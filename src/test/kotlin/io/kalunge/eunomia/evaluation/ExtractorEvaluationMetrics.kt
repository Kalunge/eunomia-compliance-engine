package io.kalunge.eunomia.evaluation

import io.kalunge.eunomia.extraction.ExtractedObligation

data class ExpectedExtractorOutput(
    val obligations: List<ExpectedExtractorObligation>,
)

data class ExpectedExtractorObligation(
    val summary: String,
    val sourceExcerpt: String,
)

data class ExtractorEvaluationScore(
    val truePositives: Int,
    val falsePositives: Int,
    val falseNegatives: Int,
    val precision: Double,
    val recall: Double,
    val f1: Double,
    val sourceFaithfulness: Double,
    val unfaithfulSourceExcerpts: List<String>,
) {
    fun asMetrics(parseSuccess: Double = 1.0): Map<String, Double> = mapOf(
        "precision" to precision,
        "recall" to recall,
        "f1" to f1,
        "sourceFaithfulness" to sourceFaithfulness,
        "parseSuccess" to parseSuccess,
    )
}

object ExtractorEvaluationMetrics {

    private const val MATCH_THRESHOLD = 0.65
    private val tokenSeparator = Regex("[^a-z0-9]+")
    private val whitespace = Regex("\\s+")

    fun score(
        documentText: String,
        expected: List<ExpectedExtractorObligation>,
        predicted: List<ExtractedObligation>,
    ): ExtractorEvaluationScore {
        val unmatchedPredictions = predicted.indices.toMutableSet()
        var truePositives = 0

        expected.forEach { expectedObligation ->
            val bestMatch = unmatchedPredictions
                .map { index ->
                    index to excerptSimilarity(
                        expectedObligation.sourceExcerpt,
                        predicted[index].sourceExcerpt,
                    )
                }
                .maxByOrNull { (_, similarity) -> similarity }

            if (bestMatch != null && bestMatch.second >= MATCH_THRESHOLD) {
                truePositives++
                unmatchedPredictions.remove(bestMatch.first)
            }
        }

        val falsePositives = predicted.size - truePositives
        val falseNegatives = expected.size - truePositives
        val precision = ratio(
            numerator = truePositives,
            denominator = truePositives + falsePositives,
            bothEmpty = expected.isEmpty() && predicted.isEmpty(),
        )
        val recall = ratio(
            numerator = truePositives,
            denominator = truePositives + falseNegatives,
            bothEmpty = expected.isEmpty() && predicted.isEmpty(),
        )
        val f1 = if (precision + recall == 0.0) {
            0.0
        } else {
            2 * precision * recall / (precision + recall)
        }

        val normalizedDocument = normalizeText(documentText)
        val unfaithfulSourceExcerpts = predicted.mapNotNull { obligation ->
            val excerpt = normalizeText(obligation.sourceExcerpt)
            obligation.sourceExcerpt.takeUnless {
                excerpt.isNotEmpty() && normalizedDocument.contains(excerpt)
            }
        }
        val sourceFaithfulness = if (predicted.isEmpty()) {
            1.0
        } else {
            (predicted.size - unfaithfulSourceExcerpts.size).toDouble() / predicted.size
        }

        return ExtractorEvaluationScore(
            truePositives = truePositives,
            falsePositives = falsePositives,
            falseNegatives = falseNegatives,
            precision = precision,
            recall = recall,
            f1 = f1,
            sourceFaithfulness = sourceFaithfulness,
            unfaithfulSourceExcerpts = unfaithfulSourceExcerpts,
        )
    }

    private fun excerptSimilarity(expected: String, predicted: String): Double {
        val expectedNormalized = normalizeText(expected)
        val predictedNormalized = normalizeText(predicted)
        if (expectedNormalized.isEmpty() || predictedNormalized.isEmpty()) return 0.0
        if (
            expectedNormalized.contains(predictedNormalized) ||
            predictedNormalized.contains(expectedNormalized)
        ) {
            return 1.0
        }

        val expectedTokens = tokenize(expectedNormalized)
        val predictedTokens = tokenize(predictedNormalized)
        val union = expectedTokens union predictedTokens
        if (union.isEmpty()) return 0.0
        return (expectedTokens intersect predictedTokens).size.toDouble() / union.size
    }

    private fun tokenize(value: String): Set<String> =
        value.split(tokenSeparator).filter { it.isNotBlank() }.toSet()

    private fun normalizeText(value: String): String =
        value.lowercase()
            .replace(tokenSeparator, " ")
            .replace(whitespace, " ")
            .trim()

    private fun ratio(numerator: Int, denominator: Int, bothEmpty: Boolean): Double =
        when {
            bothEmpty -> 1.0
            denominator == 0 -> 0.0
            else -> numerator.toDouble() / denominator
        }
}
