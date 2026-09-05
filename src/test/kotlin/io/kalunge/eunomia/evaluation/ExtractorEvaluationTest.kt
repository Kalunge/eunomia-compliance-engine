package io.kalunge.eunomia.evaluation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kalunge.eunomia.extraction.LangChain4jExtractorAgent
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.system.measureTimeMillis

@Tag("ai-eval")
class ExtractorEvaluationTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `evaluates extractor against labelled regulations`() {
        val evaluationModel = GroqEvaluationModelFactory.createFromEnvironment()
        assumeTrue(
            evaluationModel != null,
            "Set GROQ_API_KEY to run the live Extractor evaluation.",
        )
        val model = requireNotNull(evaluationModel)

        val agent = LangChain4jExtractorAgent(
            model.chatModel,
            objectMapper,
        )
        val dataset = EvaluationDatasetLoader(objectMapper)
            .load("ai-eval/extractor-v1.json")

        val results = dataset.cases.map { case ->
            val documentResource = case.input.path("documentResource").asText()
            val documentText = readResource(documentResource)
            val expected = objectMapper.treeToValue(
                case.expected,
                ExpectedExtractorOutput::class.java,
            ).obligations

            var predictedCount = 0
            var result: EvaluationCaseResult? = null
            val durationMs = measureTimeMillis {
                result = runCatching {
                    val predicted = agent.extract(documentText)
                    predictedCount = predicted.size
                    val score = ExtractorEvaluationMetrics.score(
                        documentText = documentText,
                        expected = expected,
                        predicted = predicted,
                    )
                    val passed = score.falsePositives == 0 &&
                        score.falseNegatives == 0 &&
                        score.sourceFaithfulness == 1.0
                    val faithfulnessDetail = if (score.unfaithfulSourceExcerpts.isEmpty()) {
                        ""
                    } else {
                        " Unfaithful excerpts: " +
                            score.unfaithfulSourceExcerpts.joinToString(" | ") { "\"${it.take(180)}\"" }
                    }
                    EvaluationCaseResult(
                        caseId = case.id,
                        status = if (passed) EvaluationStatus.PASS else EvaluationStatus.FAIL,
                        durationMs = 0,
                        metrics = score.asMetrics(),
                        details = "Expected ${expected.size}; predicted $predictedCount; " +
                            "TP=${score.truePositives}, FP=${score.falsePositives}, FN=${score.falseNegatives}." +
                            faithfulnessDetail,
                    )
                }.getOrElse { error ->
                    EvaluationCaseResult(
                        caseId = case.id,
                        status = EvaluationStatus.ERROR,
                        durationMs = 0,
                        metrics = mapOf(
                            "precision" to 0.0,
                            "recall" to 0.0,
                            "f1" to 0.0,
                            "sourceFaithfulness" to 0.0,
                            "parseSuccess" to 0.0,
                        ),
                        details = "${error::class.simpleName}: ${error.message}",
                    )
                }
            }

            requireNotNull(result).copy(durationMs = durationMs)
        }

        val report = EvaluationReportFactory.create(
            dataset = dataset,
            model = model.modelName,
            results = results,
        )
        val reportRoot = Path.of(
            System.getProperty("aiEval.reportDir", "build/reports/ai-eval"),
        )
        EvaluationReportWriter(objectMapper)
            .write(report, reportRoot.resolve(dataset.suiteId))
    }

    private fun readResource(resourcePath: String): String {
        val stream = Thread.currentThread().contextClassLoader
            .getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Evaluation document not found: $resourcePath")
        return stream.bufferedReader().use { it.readText() }
    }
}
