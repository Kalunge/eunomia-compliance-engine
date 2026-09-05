package io.kalunge.eunomia.evaluation

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

data class EvaluationDataset(
    val schemaVersion: Int,
    val suiteId: String,
    val evaluator: String,
    val description: String,
    val cases: List<EvaluationCaseDefinition>,
)

data class EvaluationCaseDefinition(
    val id: String,
    val input: JsonNode,
    val expected: JsonNode,
    val tags: Set<String> = emptySet(),
)

enum class EvaluationStatus {
    PASS,
    FAIL,
    ERROR,
}

data class EvaluationCaseResult(
    val caseId: String,
    val status: EvaluationStatus,
    val durationMs: Long,
    val metrics: Map<String, Double> = emptyMap(),
    val details: String? = null,
)

data class EvaluationRunReport(
    val schemaVersion: Int = 1,
    val suiteId: String,
    val evaluator: String,
    val description: String,
    val model: String,
    val generatedAt: String,
    val totalCases: Int,
    val passedCases: Int,
    val failedCases: Int,
    val errorCases: Int,
    val aggregateMetrics: Map<String, Double>,
    val cases: List<EvaluationCaseResult>,
)

object EvaluationReportFactory {

    fun create(
        dataset: EvaluationDataset,
        model: String,
        results: List<EvaluationCaseResult>,
        generatedAt: Instant = Instant.now(),
    ): EvaluationRunReport {
        require(results.map { it.caseId }.toSet().size == results.size) {
            "Evaluation results contain duplicate case IDs."
        }

        val datasetCaseIds = dataset.cases.map { it.id }.toSet()
        val resultCaseIds = results.map { it.caseId }.toSet()
        require(resultCaseIds == datasetCaseIds) {
            "Evaluation results must cover every case in dataset '${dataset.suiteId}'."
        }

        val aggregateMetrics = results
            .flatMap { result -> result.metrics.map { (name, value) -> name to value } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.average() }
            .toSortedMap()

        return EvaluationRunReport(
            suiteId = dataset.suiteId,
            evaluator = dataset.evaluator,
            description = dataset.description,
            model = model,
            generatedAt = generatedAt.toString(),
            totalCases = results.size,
            passedCases = results.count { it.status == EvaluationStatus.PASS },
            failedCases = results.count { it.status == EvaluationStatus.FAIL },
            errorCases = results.count { it.status == EvaluationStatus.ERROR },
            aggregateMetrics = aggregateMetrics,
            cases = results,
        )
    }
}
