package io.kalunge.eunomia.evaluation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kalunge.eunomia.diff.DiffSide
import io.kalunge.eunomia.diff.DiffVerdict
import io.kalunge.eunomia.diff.LangChain4jDiffAgent
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.UUID
import kotlin.system.measureTimeMillis

@Tag("ai-eval")
class DiffEvaluationTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `evaluates regulation diff judgments and removed obligations`() {
        val evaluationModel = GroqEvaluationModelFactory.createFromEnvironment()
        assumeTrue(
            evaluationModel != null,
            "Set GROQ_API_KEY to run the live Diff evaluation.",
        )
        val model = requireNotNull(evaluationModel)
        val callDelayMs = System.getenv("AI_EVAL_CALL_DELAY_MS")
            ?.toLongOrNull()
            ?.coerceAtLeast(0)
            ?: 500
        val agent = LangChain4jDiffAgent(model.chatModel, objectMapper)
        val dataset = EvaluationDatasetLoader(objectMapper)
            .load("ai-eval/diff-v1.json")

        val results = dataset.cases.map { case ->
            val input = objectMapper.treeToValue(
                case.input,
                DiffEvaluationInput::class.java,
            )
            val expected = objectMapper.treeToValue(
                case.expected,
                ExpectedDiffOutput::class.java,
            )
            val expectedByCandidate = expected.judgments.associateBy { it.candidateId }
            val predictions = ArrayList<DiffCandidatePrediction>()

            val durationMs = measureTimeMillis {
                input.candidates.forEachIndexed { candidateIndex, candidate ->
                    require(candidate.id in expectedByCandidate) {
                        "Missing expected judgment for candidate '${candidate.id}'."
                    }
                    require(candidate.baselineIndices.isNotEmpty()) {
                        "Candidate '${candidate.id}' must provide at least one baseline candidate."
                    }
                    require(candidate.baselineIndices.size <= 3) {
                        "Candidate '${candidate.id}' provides more than three baselines."
                    }
                    require(candidate.baselineIndices.all { it in input.baselines.indices }) {
                        "Candidate '${candidate.id}' refers to an invalid baseline index."
                    }

                    val selectedBaselines = candidate.baselineIndices.map { baselineIndex ->
                        input.baselines[baselineIndex].toDiffSide()
                    }
                    val prediction = runCatching {
                        val judgment = agent.judge(candidate.toDiffSide(), selectedBaselines)
                        val globalBaselineIndex = judgment.baselineIndex
                            ?.let { localIndex -> candidate.baselineIndices.getOrNull(localIndex) }
                        DiffCandidatePrediction(
                            candidateId = candidate.id,
                            verdict = judgment.verdict,
                            baselineIndex = if (judgment.verdict == DiffVerdict.ADDED) {
                                null
                            } else {
                                globalBaselineIndex
                            },
                            rationale = judgment.rationale,
                            parseSuccess = true,
                        )
                    }.getOrElse { error ->
                        DiffCandidatePrediction(
                            candidateId = candidate.id,
                            verdict = null,
                            baselineIndex = null,
                            rationale = "${error::class.simpleName}: ${error.message}",
                            parseSuccess = false,
                        )
                    }
                    predictions.add(prediction)

                    if (candidateIndex < input.candidates.lastIndex && callDelayMs > 0) {
                        Thread.sleep(callDelayMs)
                    }
                }
            }

            val score = DiffEvaluationMetrics.score(
                baselineCount = input.baselines.size,
                expected = expected,
                predictions = predictions,
            )
            val passed = score.classificationAccuracy == 1.0 &&
                score.pairingAccuracy == 1.0 &&
                score.removedF1 == 1.0 &&
                score.rationaleCoverage == 1.0 &&
                score.parseSuccess == 1.0
            val predictionDetails = predictions.joinToString("; ") { prediction ->
                "${prediction.candidateId}:${prediction.verdict ?: "ERROR"}" +
                    "@${prediction.baselineIndex ?: "-"} (${prediction.rationale.orEmpty().take(120)})"
            }

            EvaluationCaseResult(
                caseId = case.id,
                status = if (passed) EvaluationStatus.PASS else EvaluationStatus.FAIL,
                durationMs = durationMs,
                metrics = score.asMetrics(),
                details = "Removed expected=${expected.removedBaselineIndices.sorted()}, " +
                    "predicted=${score.predictedRemovedBaselineIndices.sorted()}; $predictionDetails",
            )
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

    private fun DiffEvaluationObligation.toDiffSide(): DiffSide =
        DiffSide(
            obligationId = stableId(id),
            orderIndex = 0,
            summary = summary,
            sourceExcerpt = sourceExcerpt,
        )

    private fun DiffEvaluationCandidate.toDiffSide(): DiffSide =
        DiffSide(
            obligationId = stableId(id),
            orderIndex = 0,
            summary = summary,
            sourceExcerpt = sourceExcerpt,
        )

    private fun stableId(value: String): UUID =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))
}
