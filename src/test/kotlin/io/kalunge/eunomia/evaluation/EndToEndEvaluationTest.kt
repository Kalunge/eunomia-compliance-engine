package io.kalunge.eunomia.evaluation

import com.fasterxml.jackson.databind.ObjectMapper
import io.kalunge.eunomia.TestcontainersConfiguration
import io.kalunge.eunomia.domain.GapStatus
import io.kalunge.eunomia.domain.Obligation
import io.kalunge.eunomia.domain.ObligationImpactRepository
import io.kalunge.eunomia.domain.ObligationPolicyMatchRepository
import io.kalunge.eunomia.domain.ObligationRepository
import io.kalunge.eunomia.domain.PolicyChunkRepository
import io.kalunge.eunomia.domain.PolicyRepository
import io.kalunge.eunomia.domain.PolicyStatus
import io.kalunge.eunomia.domain.ProductService
import io.kalunge.eunomia.domain.ProductServiceRepository
import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.RegulationStatus
import io.kalunge.eunomia.domain.Tenant
import io.kalunge.eunomia.domain.TenantRepository
import io.kalunge.eunomia.domain.WorkflowEvent
import io.kalunge.eunomia.domain.WorkflowEventRepository
import io.kalunge.eunomia.extraction.ExtractedObligation
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Tag("ai-eval")
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+")
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration::class, EndToEndEvaluationTest.LiveAiConfiguration::class)
@SpringBootTest(
    properties = [
        "eunomia.mapper.call-delay-ms=1000",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EndToEndEvaluationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tenantRepository: TenantRepository
    @Autowired private lateinit var policyRepository: PolicyRepository
    @Autowired private lateinit var policyChunkRepository: PolicyChunkRepository
    @Autowired private lateinit var regulationRepository: RegulationRepository
    @Autowired private lateinit var obligationRepository: ObligationRepository
    @Autowired private lateinit var matchRepository: ObligationPolicyMatchRepository
    @Autowired private lateinit var productRepository: ProductServiceRepository
    @Autowired private lateinit var impactRepository: ObligationImpactRepository
    @Autowired private lateinit var eventRepository: WorkflowEventRepository
    @Autowired private lateinit var chatUsage: MeteredChatLanguageModel
    @Autowired private lateinit var embeddingUsage: MeteredEmbeddingService

    @Test
    fun `evaluates the complete compliance workflow`() {
        val dataset = EvaluationDatasetLoader(objectMapper)
            .load("ai-eval/end-to-end-v1.json")
        val case = dataset.cases.single()
        val input = objectMapper.treeToValue(case.input, EndToEndEvaluationInput::class.java)
        val expected = objectMapper.treeToValue(case.expected, ExpectedEndToEndOutput::class.java)

        chatUsage.reset()
        embeddingUsage.reset()

        val slug = "e2e-${System.nanoTime()}"
        val tenant = tenantRepository.save(Tenant(name = "End-to-End Evaluation", slug = slug))
        val token = registerAndGetToken("e2e-${System.nanoTime()}@example.com", slug)
        productRepository.saveAll(
            input.products.map { product ->
                ProductService(
                    tenantId = tenant.id,
                    name = product.name,
                    description = product.description,
                )
            },
        )

        val policyStartedAt = System.nanoTime()
        val policyId = uploadPolicy(input, token)
        val policy = awaitPolicyTerminal(policyId, tenant.id)
        val policyLatencyMs = elapsedMillis(policyStartedAt)

        val regulationStartedAt = System.nanoTime()
        val regulationId = uploadRegulation(input, token)
        val regulation = awaitRegulationTerminal(regulationId, tenant.id)
        val regulationLatencyMs = elapsedMillis(regulationStartedAt)

        val obligations = obligationRepository
            .findAllByRegulationIdAndTenantIdOrderByOrderIndexAsc(regulationId, tenant.id)
        val extractionScore = ExtractorEvaluationMetrics.score(
            documentText = input.regulationText,
            expected = expected.obligations.map { obligation ->
                ExpectedExtractorObligation(
                    summary = obligation.summary,
                    sourceExcerpt = obligation.sourceExcerpt,
                )
            },
            predicted = obligations.map { obligation ->
                ExtractedObligation(
                    summary = obligation.summary,
                    sourceExcerpt = obligation.sourceExcerpt,
                    pageStart = obligation.pageStart,
                    pageEnd = obligation.pageEnd,
                )
            },
        )

        val matches = matchRepository.findAllByObligationIdInAndTenantId(
            obligations.map { it.id },
            tenant.id,
        )
        val matchedChunkIds = matches.map { it.policyChunkId }.toSet()
        val chunks = if (matchedChunkIds.isEmpty()) {
            emptyList()
        } else {
            policyChunkRepository.findAllByIdInAndTenantId(matchedChunkIds, tenant.id)
        }
        val chunksById = chunks.associateBy { it.id }
        val matchesByObligation = matches.groupBy { it.obligationId }

        val impacts = impactRepository.findAllByObligationIdInAndTenantId(
            obligations.map { it.id },
            tenant.id,
        )
        val impactedProductIds = impacts.map { it.productServiceId }.toSet()
        val productsById = if (impactedProductIds.isEmpty()) {
            emptyMap()
        } else {
            productRepository.findAllByIdInAndTenantId(impactedProductIds, tenant.id)
                .associateBy { it.id }
        }
        val impactsByObligation = impacts.groupBy { it.obligationId }

        val actualByExpectedId = expected.obligations.associate { expectation ->
            expectation.id to obligations.firstOrNull { obligation ->
                containsAnchors(
                    "${obligation.summary} ${obligation.sourceExcerpt}",
                    expectation.anchorTerms,
                )
            }
        }
        val observations = expected.obligations.mapNotNull { expectation ->
            val obligation = actualByExpectedId[expectation.id] ?: return@mapNotNull null
            EndToEndObligationObservation(
                id = expectation.id,
                gapStatus = obligation.gapStatus,
                matchedPolicyTexts = matchesByObligation[obligation.id].orEmpty()
                    .mapNotNull { match -> chunksById[match.policyChunkId]?.text },
                impactedProducts = impactsByObligation[obligation.id].orEmpty()
                    .mapNotNull { impact -> productsById[impact.productServiceId]?.name }
                    .toSet(),
            )
        }

        awaitWorkflowCompletionEvent(regulationId, tenant.id)
        val events = eventRepository
            .findAllByRegulationIdAndTenantIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
                regulationId,
                tenant.id,
                0,
            )
        val pipelineScore = EndToEndEvaluationMetrics.score(
            expected = expected.obligations,
            observations = observations,
            workflowEvents = events.map { it.eventType }.toSet(),
            policyIndexed = policy.status == PolicyStatus.INDEXED,
            regulationBriefReady = regulation.status == RegulationStatus.BRIEF_READY,
        )
        val briefScore = scoreBrief(
            regulation = regulation,
            obligations = obligations,
            expected = expected,
            matchesByObligation = matchesByObligation,
            chunksById = chunksById,
            impactsByObligation = impactsByObligation,
            productsById = productsById,
        )
        val usage = embeddingUsage.snapshot(chatUsage)

        val metrics = linkedMapOf<String, Double>()
        metrics += extractionScore.asMetrics().mapKeys { "extractor.${it.key}" }
        metrics += pipelineScore.asMetrics()
        metrics["brief.sectionCoverage"] = briefScore.sectionCoverage
        metrics["brief.obligationCoverage"] = briefScore.obligationCoverage
        metrics["brief.statusAccuracy"] = briefScore.statusAccuracy
        metrics["brief.gapActionCoverage"] = briefScore.gapActionCoverage
        metrics["brief.factualGrounding"] = briefScore.factualGrounding
        metrics["policyIndexLatencyMs"] = policyLatencyMs.toDouble()
        metrics["regulationPipelineLatencyMs"] = regulationLatencyMs.toDouble()
        metrics += stageLatencyMetrics(events)
        metrics["usage.chatCalls"] = usage.chatCalls.toDouble()
        metrics["usage.inputTokens"] = usage.inputTokens.toDouble()
        metrics["usage.outputTokens"] = usage.outputTokens.toDouble()
        metrics["usage.totalTokens"] = usage.totalTokens.toDouble()
        metrics["usage.documentEmbeddingRequests"] = usage.documentEmbeddingRequests.toDouble()
        metrics["usage.embeddedDocuments"] = usage.embeddedDocuments.toDouble()
        metrics["usage.queryEmbeddingRequests"] = usage.queryEmbeddingRequests.toDouble()
        metrics["usage.estimatedEmbeddingTokens"] = usage.estimatedEmbeddingTokens.toDouble()

        val passed = extractionScore.recall == 1.0 &&
            extractionScore.sourceFaithfulness == 1.0 &&
            pipelineScore.mappingAccuracy == 1.0 &&
            pipelineScore.retrievalCoverage == 1.0 &&
            pipelineScore.impactRecall == 1.0 &&
            pipelineScore.workflowEventCoverage == 1.0 &&
            pipelineScore.finalStateSuccess == 1.0 &&
            briefScore.sectionCoverage == 1.0 &&
            briefScore.obligationCoverage == 1.0 &&
            briefScore.statusAccuracy == 1.0 &&
            briefScore.gapActionCoverage == 1.0 &&
            briefScore.factualGrounding == 1.0

        val result = EvaluationCaseResult(
            caseId = case.id,
            status = if (passed) EvaluationStatus.PASS else EvaluationStatus.FAIL,
            durationMs = policyLatencyMs + regulationLatencyMs,
            metrics = metrics,
            details = buildDetails(
                extractionScore = extractionScore,
                pipelineScore = pipelineScore,
                briefScore = briefScore,
                usage = usage,
                policyStatus = policy.status,
                regulationStatus = regulation.status,
            ),
        )
        val report = EvaluationReportFactory.create(
            dataset = dataset,
            model = "${System.getenv("GROQ_MODEL") ?: "openai/gpt-oss-120b"} + " +
                "${System.getenv("COHERE_MODEL") ?: "embed-english-v3.0"}",
            results = listOf(result),
        )
        val reportRoot = Path.of(
            System.getProperty("aiEval.reportDir", "build/reports/ai-eval"),
        )
        EvaluationReportWriter(objectMapper)
            .write(report, reportRoot.resolve(dataset.suiteId))
    }

    private fun scoreBrief(
        regulation: io.kalunge.eunomia.domain.Regulation,
        obligations: List<Obligation>,
        expected: ExpectedEndToEndOutput,
        matchesByObligation: Map<UUID, List<io.kalunge.eunomia.domain.ObligationPolicyMatch>>,
        chunksById: Map<UUID, io.kalunge.eunomia.domain.PolicyChunk>,
        impactsByObligation: Map<UUID, List<io.kalunge.eunomia.domain.ObligationImpact>>,
        productsById: Map<UUID, ProductService>,
    ): BriefEvaluationScore {
        val policyIds = chunksById.values.map { it.policyId }.toSet()
        val policyTitles = policyRepository.findAllById(policyIds).associateBy({ it.id }, { it.title })
        val briefInput = BriefEvaluationInput(
            regulationTitle = regulation.title,
            sourceFilename = regulation.sourceFilename,
            uploadedAt = regulation.uploadedAt.toString(),
            obligations = obligations.map { obligation ->
                val expectation = expected.obligations.firstOrNull {
                    containsAnchors("${obligation.summary} ${obligation.sourceExcerpt}", it.anchorTerms)
                }
                BriefEvaluationObligation(
                    id = expectation?.id ?: obligation.id.toString(),
                    summary = obligation.summary,
                    sourceExcerpt = obligation.sourceExcerpt,
                    gapStatus = obligation.gapStatus,
                    impactedProducts = impactsByObligation[obligation.id].orEmpty()
                        .mapNotNull { productsById[it.productServiceId]?.name }
                        .sorted(),
                    matches = matchesByObligation[obligation.id].orEmpty().mapNotNull { match ->
                        val chunk = chunksById[match.policyChunkId] ?: return@mapNotNull null
                        BriefEvaluationMatch(
                            policyTitle = policyTitles[chunk.policyId] ?: "Unknown policy",
                            chunkText = chunk.text,
                            confidence = match.confidence,
                        )
                    },
                    anchorTerms = expectation?.anchorTerms
                        ?: setOf(obligation.summary.substringBefore(' ')),
                )
            },
        )
        return BriefEvaluationMetrics.score(
            input = briefInput,
            expected = ExpectedBriefOutput(expected.forbiddenBriefTerms),
            markdown = regulation.briefMarkdown.orEmpty(),
        )
    }

    private fun uploadPolicy(input: EndToEndEvaluationInput, token: String): UUID {
        val file = MockMultipartFile(
            "file",
            input.policyFilename,
            MediaType.TEXT_PLAIN_VALUE,
            input.policyText.toByteArray(),
        )
        val response = mockMvc.perform(
            multipart("/api/v1/policies")
                .file(file)
                .header("Authorization", "Bearer $token"),
        ).andExpect(status().isCreated).andReturn()
        return UUID.fromString(objectMapper.readTree(response.response.contentAsString)["id"].asText())
    }

    private fun uploadRegulation(input: EndToEndEvaluationInput, token: String): UUID {
        val file = MockMultipartFile(
            "file",
            input.regulationFilename,
            MediaType.TEXT_PLAIN_VALUE,
            input.regulationText.toByteArray(),
        )
        val response = mockMvc.perform(
            multipart("/api/v1/regulations")
                .file(file)
                .header("Authorization", "Bearer $token"),
        ).andExpect(status().isCreated).andReturn()
        return UUID.fromString(objectMapper.readTree(response.response.contentAsString)["id"].asText())
    }

    private fun registerAndGetToken(email: String, slug: String): String {
        val response = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "$email",
                      "password": "evaluation-password-2026",
                      "displayName": "Evaluation User",
                      "tenantSlug": "$slug"
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(response.response.contentAsString)["token"].asText()
    }

    private fun awaitPolicyTerminal(policyId: UUID, tenantId: UUID): io.kalunge.eunomia.domain.Policy {
        await()
            .atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofMillis(250))
            .until {
                policyRepository.findByIdAndTenantId(policyId, tenantId)?.status in
                    setOf(PolicyStatus.INDEXED, PolicyStatus.FAILED)
            }
        return requireNotNull(policyRepository.findByIdAndTenantId(policyId, tenantId))
    }

    private fun awaitRegulationTerminal(
        regulationId: UUID,
        tenantId: UUID,
    ): io.kalunge.eunomia.domain.Regulation {
        await()
            .atMost(Duration.ofMinutes(5))
            .pollInterval(Duration.ofMillis(500))
            .until {
                val regulation = regulationRepository.findByIdAndTenantId(regulationId, tenantId)
                regulation?.status in setOf(RegulationStatus.BRIEF_READY, RegulationStatus.FAILED) ||
                    (regulation?.status == RegulationStatus.MAPPED && regulation.lastError != null)
            }
        return requireNotNull(regulationRepository.findByIdAndTenantId(regulationId, tenantId))
    }

    private fun awaitWorkflowCompletionEvent(regulationId: UUID, tenantId: UUID) {
        val regulation = requireNotNull(regulationRepository.findByIdAndTenantId(regulationId, tenantId))
        if (regulation.status != RegulationStatus.BRIEF_READY) return

        await()
            .atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .until {
                eventRepository
                    .findAllByRegulationIdAndTenantIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
                        regulationId,
                        tenantId,
                        0,
                    )
                    .any { it.eventType == "summarizer.complete" }
            }
    }

    private fun stageLatencyMetrics(events: List<WorkflowEvent>): Map<String, Double> {
        val firstByType = events.associateBy { it.eventType }
        return mapOf(
            "extractorStageLatencyMs" to durationMillis(
                firstByType["extractor.start"]?.occurredAt,
                firstByType["mapper.start"]?.occurredAt,
            ),
            "mapperImpactStageLatencyMs" to durationMillis(
                firstByType["mapper.start"]?.occurredAt,
                firstByType["summarizer.start"]?.occurredAt,
            ),
            "briefStageLatencyMs" to durationMillis(
                firstByType["summarizer.start"]?.occurredAt,
                firstByType["summarizer.complete"]?.occurredAt,
            ),
        )
    }

    private fun durationMillis(start: Instant?, end: Instant?): Double =
        if (start == null || end == null) {
            0.0
        } else {
            Duration.between(start, end).toMillis().coerceAtLeast(0).toDouble()
        }

    private fun containsAnchors(text: String, anchors: Set<String>): Boolean {
        val normalized = normalize(text)
        return anchors.all { normalized.contains(normalize(it)) }
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun elapsedMillis(startedAt: Long): Long =
        Duration.ofNanos(System.nanoTime() - startedAt).toMillis()

    private fun buildDetails(
        extractionScore: ExtractorEvaluationScore,
        pipelineScore: EndToEndPipelineScore,
        briefScore: BriefEvaluationScore,
        usage: EvaluationUsageSnapshot,
        policyStatus: PolicyStatus,
        regulationStatus: RegulationStatus,
    ): String = listOf(
        "policyStatus=$policyStatus",
        "regulationStatus=$regulationStatus",
        "extractorFalseNegatives=${extractionScore.falseNegatives}",
        "incorrectMappings=${pipelineScore.incorrectMappings}",
        "missingRetrieval=${pipelineScore.missingRetrievalEvidence}",
        "missingProducts=${pipelineScore.missingRequiredProducts}",
        "unexpectedProducts=${pipelineScore.unexpectedProducts}",
        "missingEvents=${pipelineScore.missingWorkflowEvents}",
        "briefMissingSections=${briefScore.missingSections}",
        "briefUnsupportedNumbers=${briefScore.unsupportedNumbers}",
        "chatCalls=${usage.chatCalls}",
        "tokens=${usage.totalTokens}",
    ).joinToString("; ")

    @TestConfiguration
    class LiveAiConfiguration {

        @Bean
        @Primary
        fun liveEvaluationChatModel(): MeteredChatLanguageModel {
            val model = requireNotNull(GroqEvaluationModelFactory.createFromEnvironment()) {
                "GROQ_API_KEY is required for the end-to-end evaluation."
            }
            return MeteredChatLanguageModel(model.chatModel)
        }

        @Bean
        @Primary
        fun liveEvaluationEmbeddingService(): MeteredEmbeddingService {
            val model = requireNotNull(CohereEvaluationEmbeddingFactory.createFromEnvironment()) {
                "COHERE_API_KEY is required for the end-to-end evaluation."
            }
            return MeteredEmbeddingService(model.embeddingService)
        }
    }
}
