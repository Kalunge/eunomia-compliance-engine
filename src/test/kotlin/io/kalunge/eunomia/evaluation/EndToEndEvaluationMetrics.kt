package io.kalunge.eunomia.evaluation

import io.kalunge.eunomia.domain.GapStatus

data class EndToEndEvaluationInput(
    val policyTitle: String,
    val policyFilename: String,
    val policyText: String,
    val regulationTitle: String,
    val regulationFilename: String,
    val regulationText: String,
    val products: List<EndToEndProduct>,
)

data class EndToEndProduct(
    val name: String,
    val description: String,
)

data class ExpectedEndToEndOutput(
    val obligations: List<ExpectedEndToEndObligation>,
    val forbiddenBriefTerms: Set<String> = emptySet(),
)

data class ExpectedEndToEndObligation(
    val id: String,
    val summary: String,
    val sourceExcerpt: String,
    val anchorTerms: Set<String>,
    val expectedGapStatus: GapStatus,
    val expectedPolicyTerms: Set<String> = emptySet(),
    val requiredProducts: Set<String>,
    val allowedProducts: Set<String>,
)

data class EndToEndObligationObservation(
    val id: String,
    val gapStatus: GapStatus,
    val matchedPolicyTexts: List<String>,
    val impactedProducts: Set<String>,
)

data class EndToEndPipelineScore(
    val mappingAccuracy: Double,
    val retrievalCoverage: Double,
    val impactPrecision: Double,
    val impactRecall: Double,
    val impactF1: Double,
    val workflowEventCoverage: Double,
    val finalStateSuccess: Double,
    val incorrectMappings: List<String>,
    val missingRetrievalEvidence: List<String>,
    val missingRequiredProducts: List<String>,
    val unexpectedProducts: List<String>,
    val missingWorkflowEvents: List<String>,
) {
    fun asMetrics(): Map<String, Double> = mapOf(
        "mappingAccuracy" to mappingAccuracy,
        "retrievalCoverage" to retrievalCoverage,
        "impactPrecision" to impactPrecision,
        "impactRecall" to impactRecall,
        "impactF1" to impactF1,
        "workflowEventCoverage" to workflowEventCoverage,
        "finalStateSuccess" to finalStateSuccess,
    )
}

object EndToEndEvaluationMetrics {

    val requiredWorkflowEvents = setOf(
        "regulation.uploaded",
        "extractor.start",
        "extractor.identified",
        "mapper.start",
        "mapper.complete",
        "impact.start",
        "impact.complete",
        "summarizer.start",
        "summarizer.complete",
    )

    fun score(
        expected: List<ExpectedEndToEndObligation>,
        observations: List<EndToEndObligationObservation>,
        workflowEvents: Set<String>,
        policyIndexed: Boolean,
        regulationBriefReady: Boolean,
    ): EndToEndPipelineScore {
        val observationsById = observations.associateBy { it.id }
        val incorrectMappings = expected.filter { obligation ->
            observationsById[obligation.id]?.gapStatus != obligation.expectedGapStatus
        }.map { it.id }

        val mappedExpectations = expected.filter {
            it.expectedGapStatus == GapStatus.MAPPED && it.expectedPolicyTerms.isNotEmpty()
        }
        val missingRetrievalEvidence = mappedExpectations.filter { obligation ->
            observationsById[obligation.id]?.matchedPolicyTexts.orEmpty().none { text ->
                obligation.expectedPolicyTerms.all { term ->
                    normalize(text).contains(normalize(term))
                }
            }
        }.map { it.id }

        var truePositiveProducts = 0
        var falsePositiveProducts = 0
        var falseNegativeProducts = 0
        val missingRequiredProducts = ArrayList<String>()
        val unexpectedProducts = ArrayList<String>()
        expected.forEach { obligation ->
            val actual = observationsById[obligation.id]?.impactedProducts.orEmpty()
            val missing = obligation.requiredProducts - actual
            val unexpected = actual - obligation.allowedProducts
            truePositiveProducts += obligation.requiredProducts.count { it in actual }
            falseNegativeProducts += missing.size
            falsePositiveProducts += unexpected.size
            missingRequiredProducts += missing.map { "${obligation.id}:$it" }
            unexpectedProducts += unexpected.map { "${obligation.id}:$it" }
        }

        val impactPrecision = ratio(
            truePositiveProducts,
            truePositiveProducts + falsePositiveProducts,
            expected.isEmpty(),
        )
        val impactRecall = ratio(
            truePositiveProducts,
            truePositiveProducts + falseNegativeProducts,
            expected.isEmpty(),
        )
        val impactF1 = if (impactPrecision + impactRecall == 0.0) {
            0.0
        } else {
            2 * impactPrecision * impactRecall / (impactPrecision + impactRecall)
        }
        val missingWorkflowEvents = requiredWorkflowEvents
            .filterNot { it in workflowEvents }
            .sorted()

        return EndToEndPipelineScore(
            mappingAccuracy = coverage(expected.size, incorrectMappings.size),
            retrievalCoverage = coverage(mappedExpectations.size, missingRetrievalEvidence.size),
            impactPrecision = impactPrecision,
            impactRecall = impactRecall,
            impactF1 = impactF1,
            workflowEventCoverage = coverage(requiredWorkflowEvents.size, missingWorkflowEvents.size),
            finalStateSuccess = if (policyIndexed && regulationBriefReady) 1.0 else 0.0,
            incorrectMappings = incorrectMappings,
            missingRetrievalEvidence = missingRetrievalEvidence,
            missingRequiredProducts = missingRequiredProducts.sorted(),
            unexpectedProducts = unexpectedProducts.sorted(),
            missingWorkflowEvents = missingWorkflowEvents,
        )
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun coverage(total: Int, missing: Int): Double =
        if (total == 0) 1.0 else (total - missing).toDouble() / total

    private fun ratio(numerator: Int, denominator: Int, bothEmpty: Boolean): Double =
        when {
            bothEmpty -> 1.0
            denominator == 0 -> 0.0
            else -> numerator.toDouble() / denominator
        }
}
