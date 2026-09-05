package io.kalunge.eunomia.evaluation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kalunge.eunomia.mapping.ChunkCandidate
import io.kalunge.eunomia.mapping.LangChain4jMapperAgent
import io.kalunge.eunomia.mapping.ObligationContext
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Locale
import java.util.UUID
import kotlin.system.measureTimeMillis

@Tag("ai-eval")
class MapperEvaluationTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `evaluates mapper against labelled policy candidates`() {
        val evaluationModel = GroqEvaluationModelFactory.createFromEnvironment()
        assumeTrue(
            evaluationModel != null,
            "Set GROQ_API_KEY to run the live Mapper evaluation.",
        )
        val model = requireNotNull(evaluationModel)
        val confidenceThreshold = System.getenv("MAPPER_CONFIDENCE_THRESHOLD")
            ?.toDoubleOrNull()
            ?: 0.6
        val callDelayMs = System.getenv("AI_EVAL_CALL_DELAY_MS")
            ?.toLongOrNull()
            ?.coerceAtLeast(0)
            ?: 500
        val agent = LangChain4jMapperAgent(model.chatModel, objectMapper)
        val dataset = EvaluationDatasetLoader(objectMapper)
            .load("ai-eval/mapper-v1.json")

        val results = dataset.cases.mapIndexed { caseIndex, case ->
            val input = objectMapper.treeToValue(
                case.input,
                MapperEvaluationInput::class.java,
            )
            val expected = objectMapper.treeToValue(
                case.expected,
                ExpectedMapperOutput::class.java,
            )
            val candidates = input.candidates.mapIndexed { candidateIndex, candidate ->
                ChunkCandidate(
                    chunkId = stableId("${case.id}:chunk:$candidateIndex"),
                    policyId = stableId("${case.id}:policy:$candidateIndex"),
                    chunkIndex = candidateIndex,
                    text = candidate.text,
                    distance = candidate.distance,
                )
            }

            var result: EvaluationCaseResult? = null
            val durationMs = measureTimeMillis {
                result = runCatching {
                    val judgments = agent.judge(
                        obligation = ObligationContext(
                            summary = input.obligation.summary,
                            sourceExcerpt = input.obligation.sourceExcerpt,
                        ),
                        candidates = candidates,
                    )
                    val score = MapperEvaluationMetrics.score(
                        candidateCount = candidates.size,
                        expectedSatisfiedIndices = expected.satisfiedIndices,
                        judgments = judgments,
                        confidenceThreshold = confidenceThreshold,
                    )
                    val passed = score.expectedSatisfiedIndices == score.predictedSatisfiedIndices &&
                        score.judgmentCompleteness == 1.0
                    val judgmentDetails = judgments
                        .sortedBy { it.candidateIndex }
                        .joinToString(", ") { judgment ->
                            val verdict = if (judgment.satisfied) "satisfied" else "not-satisfied"
                            "${judgment.candidateIndex}:$verdict@" +
                                "%.2f".format(Locale.ROOT, judgment.confidence)
                        }
                    EvaluationCaseResult(
                        caseId = case.id,
                        status = if (passed) EvaluationStatus.PASS else EvaluationStatus.FAIL,
                        durationMs = 0,
                        metrics = score.asMetrics(),
                        details = "Expected ${score.expectedSatisfiedIndices.sorted()}; " +
                            "predicted ${score.predictedSatisfiedIndices.sorted()}; " +
                            "threshold=$confidenceThreshold; judgments=[$judgmentDetails].",
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
                            "classificationAccuracy" to 0.0,
                            "judgmentCompleteness" to 0.0,
                            "brierScore" to 1.0,
                            "parseSuccess" to 0.0,
                        ),
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

    private fun stableId(value: String): UUID =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))
}
