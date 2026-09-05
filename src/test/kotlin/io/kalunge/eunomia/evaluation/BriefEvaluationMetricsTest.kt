package io.kalunge.eunomia.evaluation

import io.kalunge.eunomia.domain.GapStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BriefEvaluationMetricsTest {

    private val input = BriefEvaluationInput(
        regulationTitle = "Agent Banking Rules 2026",
        sourceFilename = "agent-rules-2026.md",
        uploadedAt = "2026-09-05T08:00:00Z",
        obligations = listOf(
            BriefEvaluationObligation(
                id = "identity",
                summary = "Verify customer identity for transactions above KES 70,000.",
                sourceExcerpt = "Agents shall verify identity above KES 70,000.",
                gapStatus = GapStatus.GAP,
                impactedProducts = listOf("Agent Banking"),
                anchorTerms = setOf("identity", "70,000"),
            ),
            BriefEvaluationObligation(
                id = "reporting",
                summary = "Report suspicious transactions within 24 hours.",
                sourceExcerpt = "Reports must reach the FRC within 24 hours.",
                gapStatus = GapStatus.MAPPED,
                impactedProducts = listOf("Agent Banking"),
                matches = listOf(
                    BriefEvaluationMatch(
                        policyTitle = "AML Operations Manual",
                        chunkText = "Report suspicious activity within 24 hours.",
                        confidence = 0.91,
                    ),
                ),
                anchorTerms = setOf("suspicious", "24 hours"),
            ),
        ),
    )

    @Test
    fun `scores a complete grounded brief`() {
        val markdown = """
            # Agent Banking Rules 2026 — Impact Brief

            ## Executive Summary
            The regulation has 2 obligations, with 1 gap.

            ## Obligations Imposed
            1. Verify customer identity above KES 70,000 (Agent Banking) — Gap.
            2. Report suspicious transactions within 24 hours (Agent Banking) — Mapped.

            ## Gaps Requiring Action
            ### Identity checks above KES 70,000
            Implement biometric verification for Agent Banking.

            ## Already Mapped
            Suspicious transaction reporting is covered by the AML Operations Manual.

            ## Affected Operational Lines
            - Agent Banking: 2 obligations.
        """.trimIndent()

        val score = BriefEvaluationMetrics.score(input, ExpectedBriefOutput(), markdown)

        assertThat(score.asMetrics().values).allMatch { it == 1.0 }
    }

    @Test
    fun `detects missing status actions and unsupported claims`() {
        val markdown = """
            ## Executive Summary
            A fine of KES 5 million applies within 30 days.

            ## Obligations Imposed
            Identity controls apply above KES 70,000.

            ## Gaps Requiring Action
            Identity above KES 70,000 remains a gap.
        """.trimIndent()

        val score = BriefEvaluationMetrics.score(
            input,
            ExpectedBriefOutput(forbiddenTerms = setOf("fine", "30 days")),
            markdown,
        )

        assertThat(score.sectionCoverage).isLessThan(1.0)
        assertThat(score.statusAccuracy).isLessThan(1.0)
        assertThat(score.gapActionCoverage).isEqualTo(0.0)
        assertThat(score.numericGrounding).isLessThan(1.0)
        assertThat(score.hallucinationGuard).isEqualTo(0.0)
    }

    @Test
    fun `requires the all-covered statement when there are no gaps`() {
        val allMapped = input.copy(
            obligations = input.obligations.map { it.copy(gapStatus = GapStatus.MAPPED) },
        )
        val markdown = """
            ## Executive Summary
            Both obligations are covered.
            ## Obligations Imposed
            Identity above KES 70,000 — Mapped.
            Suspicious reporting within 24 hours — Mapped.
            ## Gaps Requiring Action
            All obligations are covered by existing policy.
            ## Already Mapped
            AML Operations Manual covers the requirements.
            ## Affected Operational Lines
            Agent Banking has 2 obligations.
        """.trimIndent()

        val score = BriefEvaluationMetrics.score(allMapped, ExpectedBriefOutput(), markdown)

        assertThat(score.gapActionCoverage).isEqualTo(1.0)
    }
}
