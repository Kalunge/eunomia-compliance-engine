package io.kalunge.eunomia.evaluation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Tag("ai-eval")
class EvaluationFoundationTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `loads cases and writes markdown and JSON reports`() {
        val dataset = EvaluationDatasetLoader(objectMapper)
            .load("ai-eval/foundation-smoke.json")

        val results = dataset.cases.map { case ->
            val matched = case.input == case.expected
            EvaluationCaseResult(
                caseId = case.id,
                status = if (matched) EvaluationStatus.PASS else EvaluationStatus.FAIL,
                durationMs = 0,
                metrics = mapOf("exactMatch" to if (matched) 1.0 else 0.0),
                details = if (matched) "Input matched the expected value." else "Input differed from expected.",
            )
        }

        val report = EvaluationReportFactory.create(
            dataset = dataset,
            model = "deterministic-smoke",
            results = results,
            generatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        val reportDirectory = Path.of(
            System.getProperty("aiEval.reportDir", "build/reports/ai-eval"),
        )

        EvaluationReportWriter(objectMapper).write(report, reportDirectory)

        assertEquals(2, report.totalCases)
        assertEquals(2, report.passedCases)
        assertEquals(1.0, report.aggregateMetrics["exactMatch"])

        val jsonReport = reportDirectory.resolve("results.json")
        val markdownReport = reportDirectory.resolve("report.md")
        assertTrue(Files.exists(jsonReport))
        assertTrue(Files.exists(markdownReport))
        assertEquals("foundation-smoke", objectMapper.readTree(jsonReport.toFile()).path("suiteId").asText())
        assertTrue(Files.readString(markdownReport).contains("# AI Evaluation Report"))
    }
}
