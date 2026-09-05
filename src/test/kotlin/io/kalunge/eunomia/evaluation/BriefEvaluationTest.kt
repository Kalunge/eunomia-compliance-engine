package io.kalunge.eunomia.evaluation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kalunge.eunomia.summarize.LangChain4jSummarizerAgent
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.system.measureTimeMillis

@Tag("ai-eval")
class BriefEvaluationTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `evaluates executive impact brief quality`() {
        val evaluationModel = GroqEvaluationModelFactory.createFromEnvironment()
        assumeTrue(
            evaluationModel != null,
            "Set GROQ_API_KEY to run the live Brief evaluation.",
        )
        val model = requireNotNull(evaluationModel)
        val callDelayMs = System.getenv("AI_EVAL_CALL_DELAY_MS")
            ?.toLongOrNull()
            ?.coerceAtLeast(0)
            ?: 500
        val agent = LangChain4jSummarizerAgent(model.chatModel)
        val dataset = EvaluationDatasetLoader(objectMapper)
            .load("ai-eval/brief-v1.json")

        val results = dataset.cases.mapIndexed { caseIndex, case ->
            val input = objectMapper.treeToValue(
                case.input,
                BriefEvaluationInput::class.java,
            )
            val expected = objectMapper.treeToValue(
                case.expected,
                ExpectedBriefOutput::class.java,
            )

            var result: EvaluationCaseResult? = null
            val durationMs = measureTimeMillis {
                result = runCatching {
                    val markdown = agent.compose(input.toBriefInput())
                    val score = BriefEvaluationMetrics.score(input, expected, markdown)
                    val passed = score.sectionCoverage == 1.0 &&
                        score.obligationCoverage == 1.0 &&
                        score.statusAccuracy == 1.0 &&
                        score.productCoverage == 1.0 &&
                        score.policyCitationCoverage == 1.0 &&
                        score.gapActionCoverage == 1.0 &&
                        score.numericGrounding == 1.0 &&
                        score.hallucinationGuard == 1.0
                    EvaluationCaseResult(
                        caseId = case.id,
                        status = if (passed) EvaluationStatus.PASS else EvaluationStatus.FAIL,
                        durationMs = 0,
                        metrics = score.asMetrics(),
                        details = buildDetails(score),
                    )
                }.getOrElse { error ->
                    EvaluationCaseResult(
                        caseId = case.id,
                        status = EvaluationStatus.ERROR,
                        durationMs = 0,
                        metrics = zeroMetrics(),
                        details = "${error::class.simpleName}: ${error.message}",
                    )
                }
            }

            if (caseIndex < dataset.cases.lastIndex && callDelayMs > 0) {
                Thread.sleep(callDelayMs)
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

    private fun buildDetails(score: BriefEvaluationScore): String = listOf(
        "missingSections=${score.missingSections}",
        "missingObligations=${score.missingObligations}",
        "incorrectStatuses=${score.incorrectStatuses}",
        "missingProducts=${score.missingProducts}",
        "missingPolicies=${score.missingPolicies}",
        "unactionedGaps=${score.unactionedGaps}",
        "unsupportedNumbers=${score.unsupportedNumbers}",
        "forbiddenTerms=${score.presentForbiddenTerms}",
    ).joinToString("; ")

    private fun zeroMetrics(): Map<String, Double> = mapOf(
        "sectionCoverage" to 0.0,
        "obligationCoverage" to 0.0,
        "statusAccuracy" to 0.0,
        "productCoverage" to 0.0,
        "policyCitationCoverage" to 0.0,
        "gapActionCoverage" to 0.0,
        "numericGrounding" to 0.0,
        "hallucinationGuard" to 0.0,
        "factualGrounding" to 0.0,
        "parseSuccess" to 0.0,
    )
}
