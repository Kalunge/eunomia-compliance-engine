package io.kalunge.eunomia.evaluation

import io.kalunge.eunomia.extraction.ExtractedObligation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExtractorEvaluationMetricsTest {

    @Test
    fun `scores a perfect extraction`() {
        val excerpt = "Banks shall report suspicious transactions within 24 hours."
        val score = ExtractorEvaluationMetrics.score(
            documentText = excerpt,
            expected = listOf(ExpectedExtractorObligation("Report suspicious transactions.", excerpt)),
            predicted = listOf(ExtractedObligation("Banks must report suspicious transactions.", excerpt)),
        )

        assertEquals(1, score.truePositives)
        assertEquals(1.0, score.precision)
        assertEquals(1.0, score.recall)
        assertEquals(1.0, score.f1)
        assertEquals(1.0, score.sourceFaithfulness)
        assertEquals(emptyList<String>(), score.unfaithfulSourceExcerpts)
    }

    @Test
    fun `counts missed and hallucinated obligations`() {
        val expectedExcerpt = "Banks shall retain records for seven years."
        val hallucinatedExcerpt = "Banks shall submit reports every week."
        val score = ExtractorEvaluationMetrics.score(
            documentText = expectedExcerpt,
            expected = listOf(ExpectedExtractorObligation("Retain records.", expectedExcerpt)),
            predicted = listOf(ExtractedObligation("Submit weekly reports.", hallucinatedExcerpt)),
        )

        assertEquals(0, score.truePositives)
        assertEquals(1, score.falsePositives)
        assertEquals(1, score.falseNegatives)
        assertEquals(0.0, score.f1)
        assertEquals(0.0, score.sourceFaithfulness)
        assertEquals(listOf(hallucinatedExcerpt), score.unfaithfulSourceExcerpts)
    }

    @Test
    fun `treats an empty document with no predictions as correct`() {
        val score = ExtractorEvaluationMetrics.score(
            documentText = "This paper provides background information only.",
            expected = emptyList(),
            predicted = emptyList(),
        )

        assertEquals(1.0, score.precision)
        assertEquals(1.0, score.recall)
        assertEquals(1.0, score.f1)
        assertEquals(1.0, score.sourceFaithfulness)
    }
}
