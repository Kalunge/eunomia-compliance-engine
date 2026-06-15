package io.kalunge.eunomia.workflow

import io.kalunge.eunomia.dashboard.TenantMetricsService
import io.kalunge.eunomia.domain.ObligationImpactRepository
import io.kalunge.eunomia.events.WorkflowEventPublisher
import io.kalunge.eunomia.domain.ObligationPolicyMatchRepository
import io.kalunge.eunomia.domain.ObligationRepository
import io.kalunge.eunomia.domain.PolicyChunkRepository
import io.kalunge.eunomia.domain.PolicyRepository
import io.kalunge.eunomia.domain.ProductServiceRepository
import io.kalunge.eunomia.domain.Regulation
import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.RegulationStatus
import io.kalunge.eunomia.summarize.BriefInput
import io.kalunge.eunomia.summarize.BriefMatchInput
import io.kalunge.eunomia.summarize.BriefObligationInput
import io.kalunge.eunomia.summarize.SummarizerAgent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Fourth and last @Async stage of the saga. Composes the impact brief
 * and persists it on the regulation row. Fired either:
 *
 *  1. Automatically from MappingWorkflow.process() once mapping +
 *     impact tagging finish — the user lands on the detail page and
 *     finds prose already rendered.
 *  2. Manually via POST /api/v1/regulations/{id}/brief when the
 *     user wants to regenerate after editing a policy.
 *
 * Failure is non-fatal to the underlying data: on Summarizer error
 * we revert status from BRIEF_GENERATING back to MAPPED with
 * `lastError` populated, so the user can still see all the obligation
 * / mapping / impact data and click "Regenerate" to retry.
 */
@Service
class SummarizerWorkflow(
    private val regulationRepository: RegulationRepository,
    private val obligationRepository: ObligationRepository,
    private val matchRepository: ObligationPolicyMatchRepository,
    private val policyChunkRepository: PolicyChunkRepository,
    private val policyRepository: PolicyRepository,
    private val impactRepository: ObligationImpactRepository,
    private val productServiceRepository: ProductServiceRepository,
    private val summarizerAgent: SummarizerAgent,
    private val tenantMetricsService: TenantMetricsService,
    private val eventPublisher: WorkflowEventPublisher,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun processInBackground(regulationId: UUID, tenantId: UUID) {
        try {
            transitionToGenerating(regulationId, tenantId)
            process(regulationId, tenantId)
        } catch (e: Exception) {
            log.error("Brief generation failed for {} (tenant {})", regulationId, tenantId, e)
            eventPublisher.publishQuietly(
                regulationId, tenantId, "workflow.failed",
                mapOf("stage" to "summarizer", "message" to (e.message ?: e.javaClass.simpleName)),
            )
            recordFailure(regulationId, tenantId, e)
        }
    }

    private fun process(regulationId: UUID, tenantId: UUID) {
        val started = System.currentTimeMillis()
        val input = buildInput(regulationId, tenantId)
        log.info("Composing brief for regulation {} ({} obligations)", regulationId, input.obligations.size)
        eventPublisher.publishQuietly(
            regulationId, tenantId, "summarizer.start",
            mapOf("obligationCount" to input.obligations.size),
        )

        val markdown = if (input.obligations.isEmpty()) {
            EMPTY_OBLIGATIONS_PLACEHOLDER.format(input.regulationTitle)
        } else {
            summarizerAgent.compose(input)
        }

        markBriefReady(regulationId, tenantId, markdown)
        runCatching { tenantMetricsService.recalculate(tenantId) }
            .onFailure { log.warn("Metrics recompute failed after brief (non-fatal): {}", it.message) }

        val elapsedMs = System.currentTimeMillis() - started
        log.info("Brief ready for regulation {} ({} chars in {} ms)", regulationId, markdown.length, elapsedMs)
        eventPublisher.publishQuietly(
            regulationId, tenantId, "summarizer.complete",
            mapOf("characters" to markdown.length, "elapsedMs" to elapsedMs),
        )
    }

    private fun buildInput(regulationId: UUID, tenantId: UUID): BriefInput {
        val regulation = regulationRepository.findByIdAndTenantId(regulationId, tenantId)
            ?: error("Regulation $regulationId vanished between mapping and brief generation.")
        val obligations = obligationRepository
            .findAllByRegulationIdAndTenantIdOrderByOrderIndexAsc(regulationId, tenantId)
        if (obligations.isEmpty()) {
            return BriefInput(
                regulationTitle = regulation.title,
                sourceFilename = regulation.sourceFilename,
                uploadedAt = regulation.uploadedAt,
                obligations = emptyList(),
            )
        }

        val obligationIds = obligations.map { it.id }

        val matches = matchRepository.findAllByObligationIdInAndTenantId(obligationIds, tenantId)
        val chunkIds = matches.map { it.policyChunkId }.toSet()
        val chunkById = if (chunkIds.isEmpty()) emptyMap()
        else policyChunkRepository.findAllByIdInAndTenantId(chunkIds, tenantId).associateBy { it.id }
        val policyIds = chunkById.values.map { it.policyId }.toSet()
        val policyTitleById = if (policyIds.isEmpty()) emptyMap()
        else policyRepository.findAllById(policyIds).associateBy({ it.id }, { it.title })

        val impacts = impactRepository.findAllByObligationIdInAndTenantId(obligationIds, tenantId)
        val productIds = impacts.map { it.productServiceId }.toSet()
        val productNameById = if (productIds.isEmpty()) emptyMap()
        else productServiceRepository.findAllByIdInAndTenantId(productIds, tenantId)
            .associateBy({ it.id }, { it.name })

        val matchesByObligation = matches.groupBy { it.obligationId }
        val impactsByObligation = impacts.groupBy { it.obligationId }

        val obligationInputs = obligations.map { o ->
            val matchInputs = matchesByObligation[o.id].orEmpty()
                .sortedByDescending { it.confidence }
                .mapNotNull { m ->
                    val chunk = chunkById[m.policyChunkId] ?: return@mapNotNull null
                    val title = policyTitleById[chunk.policyId] ?: "Unknown policy"
                    BriefMatchInput(
                        policyTitle = title,
                        chunkText = chunk.text,
                        confidence = m.confidence,
                    )
                }
            val productNames = impactsByObligation[o.id].orEmpty()
                .mapNotNull { productNameById[it.productServiceId] }
                .sorted()

            BriefObligationInput(
                summary = o.summary,
                sourceExcerpt = o.sourceExcerpt,
                gapStatus = o.gapStatus,
                impactedProducts = productNames,
                matches = matchInputs,
            )
        }

        return BriefInput(
            regulationTitle = regulation.title,
            sourceFilename = regulation.sourceFilename,
            uploadedAt = regulation.uploadedAt,
            obligations = obligationInputs,
        )
    }

    @Transactional
    internal fun transitionToGenerating(regulationId: UUID, tenantId: UUID) {
        val regulation = regulationRepository.findByIdAndTenantId(regulationId, tenantId) ?: return
        regulationRepository.save(
            regulationWith(regulation, status = RegulationStatus.BRIEF_GENERATING, lastError = null),
        )
    }

    @Transactional
    internal fun markBriefReady(regulationId: UUID, tenantId: UUID, markdown: String) {
        val regulation = regulationRepository.findByIdAndTenantId(regulationId, tenantId) ?: return
        regulationRepository.save(
            regulationWith(
                regulation,
                status = RegulationStatus.BRIEF_READY,
                processedAt = Instant.now(),
                lastError = null,
                briefMarkdown = markdown,
            ),
        )
    }

    @Transactional
    internal fun recordFailure(regulationId: UUID, tenantId: UUID, error: Throwable) {
        val regulation = regulationRepository.findByIdAndTenantId(regulationId, tenantId) ?: return
        // Revert to MAPPED (mapping data is still valid) but expose the reason.
        regulationRepository.save(
            regulationWith(
                regulation,
                status = RegulationStatus.MAPPED,
                lastError = "Brief generation failed: ${error.message ?: error.javaClass.simpleName}",
            ),
        )
    }

    private fun regulationWith(
        from: Regulation,
        status: RegulationStatus = from.status,
        processedAt: Instant? = from.processedAt,
        lastError: String? = from.lastError,
        briefMarkdown: String? = from.briefMarkdown,
    ): Regulation = Regulation(
        id = from.id,
        tenantId = from.tenantId,
        title = from.title,
        sourceFilename = from.sourceFilename,
        storageKey = from.storageKey,
        mimeType = from.mimeType,
        sizeBytes = from.sizeBytes,
        status = status,
        uploadedBy = from.uploadedBy,
        uploadedAt = from.uploadedAt,
        processedAt = processedAt,
        lastError = lastError,
        briefMarkdown = briefMarkdown,
    )

    companion object {
        private const val EMPTY_OBLIGATIONS_PLACEHOLDER =
            "# %s — Impact Brief\n\n*The Extractor did not identify any obligations in this regulation, " +
                "so there is no brief to compose. If this is unexpected, re-upload the document or " +
                "verify the source file is readable.*\n"
    }
}
