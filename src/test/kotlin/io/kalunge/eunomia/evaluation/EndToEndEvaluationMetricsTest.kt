package io.kalunge.eunomia.evaluation

import io.kalunge.eunomia.domain.GapStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EndToEndEvaluationMetricsTest {

    private val expected = listOf(
        ExpectedEndToEndObligation(
            id = "identity",
            summary = "Verify identity.",
            sourceExcerpt = "Verify identity and biometric.",
            anchorTerms = setOf("identity"),
            expectedGapStatus = GapStatus.MAPPED,
            expectedPolicyTerms = setOf("identity", "biometric"),
            requiredProducts = setOf("Agent Banking"),
            allowedProducts = setOf("Agent Banking"),
        ),
        ExpectedEndToEndObligation(
            id = "training",
            summary = "Train agents annually.",
            sourceExcerpt = "Annual training is required.",
            anchorTerms = setOf("training"),
            expectedGapStatus = GapStatus.GAP,
            requiredProducts = setOf("Agent Banking"),
            allowedProducts = setOf("Agent Banking", "Learning"),
        ),
    )

    @Test
    fun `scores a complete pipeline observation`() {
        val observations = listOf(
            EndToEndObligationObservation(
                id = "identity",
                gapStatus = GapStatus.MAPPED,
                matchedPolicyTexts = listOf("Capture identity documents and biometric evidence."),
                impactedProducts = setOf("Agent Banking"),
            ),
            EndToEndObligationObservation(
                id = "training",
                gapStatus = GapStatus.GAP,
                matchedPolicyTexts = emptyList(),
                impactedProducts = setOf("Agent Banking", "Learning"),
            ),
        )

        val score = EndToEndEvaluationMetrics.score(
            expected = expected,
            observations = observations,
            workflowEvents = EndToEndEvaluationMetrics.requiredWorkflowEvents,
            policyIndexed = true,
            regulationBriefReady = true,
        )

        assertThat(score.asMetrics().values).allMatch { it == 1.0 }
    }

    @Test
    fun `reports stage failures independently`() {
        val observations = listOf(
            EndToEndObligationObservation(
                id = "identity",
                gapStatus = GapStatus.GAP,
                matchedPolicyTexts = emptyList(),
                impactedProducts = setOf("Mobile Banking"),
            ),
        )

        val score = EndToEndEvaluationMetrics.score(
            expected = expected,
            observations = observations,
            workflowEvents = setOf("regulation.uploaded"),
            policyIndexed = true,
            regulationBriefReady = false,
        )

        assertThat(score.mappingAccuracy).isLessThan(1.0)
        assertThat(score.retrievalCoverage).isEqualTo(0.0)
        assertThat(score.impactF1).isEqualTo(0.0)
        assertThat(score.workflowEventCoverage).isLessThan(1.0)
        assertThat(score.finalStateSuccess).isEqualTo(0.0)
    }
}
