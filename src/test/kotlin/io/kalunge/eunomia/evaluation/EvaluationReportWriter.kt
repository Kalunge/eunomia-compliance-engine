package io.kalunge.eunomia.evaluation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class EvaluationReportWriter(
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {

    fun write(report: EvaluationRunReport, outputDirectory: Path) {
        Files.createDirectories(outputDirectory)

        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(outputDirectory.resolve("results.json").toFile(), report)

        Files.writeString(
            outputDirectory.resolve("report.md"),
            renderMarkdown(report),
        )
    }

    private fun renderMarkdown(report: EvaluationRunReport): String = buildString {
        appendLine("# AI Evaluation Report")
        appendLine()
        appendLine("- Suite: `${report.suiteId}`")
        appendLine("- Evaluator: `${report.evaluator}`")
        appendLine("- Model: `${report.model}`")
        appendLine("- Generated: `${report.generatedAt}`")
        appendLine()
        appendLine(report.description)
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("| Total | Passed | Failed | Errors |")
        appendLine("|---:|---:|---:|---:|")
        appendLine(
            "| ${report.totalCases} | ${report.passedCases} | " +
                "${report.failedCases} | ${report.errorCases} |",
        )

        if (report.aggregateMetrics.isNotEmpty()) {
            appendLine()
            appendLine("## Aggregate metrics")
            appendLine()
            appendLine("| Metric | Value |")
            appendLine("|---|---:|")
            report.aggregateMetrics.forEach { (name, value) ->
                appendLine("| $name | ${"%.4f".format(Locale.ROOT, value)} |")
            }
        }

        appendLine()
        appendLine("## Cases")
        appendLine()
        appendLine("| Case | Status | Duration (ms) | Details |")
        appendLine("|---|---|---:|---|")
        report.cases.forEach { result ->
            val details = result.details
                ?.replace("|", "\\|")
                ?.replace("\n", " ")
                .orEmpty()
            appendLine("| `${result.caseId}` | ${result.status} | ${result.durationMs} | $details |")
        }
    }
}
