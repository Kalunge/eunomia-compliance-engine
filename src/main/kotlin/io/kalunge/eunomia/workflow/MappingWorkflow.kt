package io.kalunge.eunomia.workflow

import io.kalunge.eunomia.domain.GapStatus
import io.kalunge.eunomia.domain.Obligation
import io.kalunge.eunomia.domain.ObligationPolicyMatch
import io.kalunge.eunomia.domain.ObligationPolicyMatchRepository
import io.kalunge.eunomia.domain.ObligationRepository
import io.kalunge.eunomia.domain.Regulation
import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.RegulationStatus
import io.kalunge.eunomia.embedding.EmbeddingService
import io.kalunge.eunomia.mapping.ChunkCandidate
import io.kalunge.eunomia.mapping.MapperAgent
import io.kalunge.eunomia.mapping.ObligationContext
import io.kalunge.eunomia.mapping.PolicyChunkSearch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Second agent in the saga. After the Extractor has populated
 * obligations for a regulation, the Mapper:
 *  1. Embeds each obligation's summary (Cohere search_query)
 *  2. Does ANN top-K over the tenant's policy chunks (cosine, pgvector)
 *  3. Asks the LLM judge to score each candidate
 *  4. Persists matches above the confidence threshold; below-threshold
 *     candidates are logged but not stored.
 *  5. Sets each obligation's gap_status to MAPPED or GAP accordingly.
 *  6. Transitions the regulation from EXTRACTED to MAPPED.
 *
 * Per-obligation failures don't halt the workflow — they leave that
 * obligation with gap_status = UNKNOWN and the regulation still
 * advances. A whole-workflow failure (e.g. embedding-API outage) sets
 * regulation status to FAILED with lastError, like the Extractor.
 */
@Service
class MappingWorkflow(
    private val regulationRepository: RegulationRepository,
    private val obligationRepository: ObligationRepository,
    private val matchRepository: ObligationPolicyMatchRepository,
    private val embeddingService: EmbeddingService,
    private val policyChunkSearch: PolicyChunkSearch,
    private val mapperAgent: MapperAgent,
    private val impactWorkflow: ImpactWorkflow,
    @Value("\${eunomia.mapper.top-k:3}") private val topK: Int,
    @Value("\${eunomia.mapper.confidence-threshold:0.6}") private val confidenceThreshold: Double,
    @Value("\${eunomia.mapper.call-delay-ms:4000}") private val callDelayMs: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun processInBackground(regulationId: UUID, tenantId: UUID) {
        try {
            process(regulationId, tenantId)
        } catch (e: Exception) {
            log.error("Mapping failed for regulation {} (tenant {})", regulationId, tenantId, e)
            recordFailure(regulationId, tenantId, e)
        }
    }

    private fun process(regulationId: UUID, tenantId: UUID) {
        val regulation = regulationRepository.findByIdAndTenantId(regulationId, tenantId)
            ?: error("Regulation $regulationId vanished between extraction and mapping.")
        val obligations = obligationRepository.findAllByRegulationIdAndTenantIdOrderByOrderIndexAsc(regulationId, tenantId)

        val started = System.currentTimeMillis()
        log.info("Mapping {} obligations for regulation {} (k={}, threshold={})",
            obligations.size, regulationId, topK, confidenceThreshold)

        var matched = 0
        var gaps = 0
        var skipped = 0

        for ((idx, obligation) in obligations.withIndex()) {
            val outcome = runCatching { mapOne(obligation, tenantId) }
                .onFailure { log.warn("Mapping obligation {} failed: {}", obligation.id, it.message) }
                .getOrNull()

            when (outcome) {
                MapOutcome.MATCHED -> matched++
                MapOutcome.GAP -> gaps++
                null -> skipped++
            }

            // Throttle between calls so we don't blow through Groq's
            // free-tier TPM cap. Skip the wait after the last obligation.
            if (idx < obligations.size - 1 && callDelayMs > 0) {
                try {
                    Thread.sleep(callDelayMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    log.warn("Mapping interrupted between obligations; aborting workflow")
                    break
                }
            }
        }

        // Tag impacts inline (sync) before marking MAPPED so the user
        // sees a single status transition with full enrichment in place.
        runCatching { impactWorkflow.tagAll(regulationId, tenantId) }
            .onFailure { log.warn("Impact tagging step failed (non-fatal) for regulation {}: {}", regulationId, it.message) }

        markRegulationMapped(regulation)

        val elapsedMs = System.currentTimeMillis() - started
        log.info("Mapping complete for regulation {} — {} matched, {} gaps, {} skipped in {} ms",
            regulationId, matched, gaps, skipped, elapsedMs)
    }

    private fun mapOne(obligation: Obligation, tenantId: UUID): MapOutcome {
        val queryVec = embeddingService.embedQuery(obligation.summary)
        val candidates = policyChunkSearch.findNearest(tenantId, queryVec, topK)

        if (candidates.isEmpty()) {
            persistGap(obligation)
            return MapOutcome.GAP
        }

        val judgments = mapperAgent.judge(
            ObligationContext(obligation.summary, obligation.sourceExcerpt),
            candidates,
        )
        val confirmed = judgments.filter { it.satisfied && it.confidence >= confidenceThreshold }
        log.debug("Obligation {} — {} candidates, {} above threshold",
            obligation.id, candidates.size, confirmed.size)

        return if (confirmed.isEmpty()) {
            persistGap(obligation)
            MapOutcome.GAP
        } else {
            persistMatches(obligation, candidates, confirmed)
            persistMapped(obligation)
            MapOutcome.MATCHED
        }
    }

    @Transactional
    internal fun persistMatches(
        obligation: Obligation,
        candidates: List<ChunkCandidate>,
        confirmedJudgments: List<io.kalunge.eunomia.mapping.MappingJudgment>,
    ) {
        // Defensive: if the workflow runs twice for the same obligation,
        // wipe previous matches first to avoid uq_match_obligation_chunk.
        matchRepository.deleteAllByObligationIdAndTenantId(obligation.id, obligation.tenantId)
        val rows = confirmedJudgments.map { j ->
            val c = candidates[j.candidateIndex]
            ObligationPolicyMatch(
                tenantId = obligation.tenantId,
                obligationId = obligation.id,
                policyChunkId = c.chunkId,
                confidence = j.confidence,
                reasoning = j.reasoning,
            )
        }
        matchRepository.saveAll(rows)
    }

    @Transactional
    internal fun persistMapped(obligation: Obligation) {
        obligationRepository.save(obligationWith(obligation, GapStatus.MAPPED))
    }

    @Transactional
    internal fun persistGap(obligation: Obligation) {
        obligationRepository.save(obligationWith(obligation, GapStatus.GAP))
    }

    @Transactional
    internal fun markRegulationMapped(regulation: Regulation) {
        regulationRepository.save(
            regulationWith(regulation, status = RegulationStatus.MAPPED, processedAt = Instant.now()),
        )
    }

    @Transactional
    internal fun recordFailure(regulationId: UUID, tenantId: UUID, error: Throwable) {
        val regulation = regulationRepository.findByIdAndTenantId(regulationId, tenantId) ?: return
        regulationRepository.save(
            regulationWith(
                regulation,
                status = RegulationStatus.FAILED,
                processedAt = Instant.now(),
                lastError = error.message ?: error.javaClass.simpleName,
            ),
        )
    }

    private fun obligationWith(o: Obligation, gapStatus: GapStatus): Obligation = Obligation(
        id = o.id,
        tenantId = o.tenantId,
        regulationId = o.regulationId,
        summary = o.summary,
        sourceExcerpt = o.sourceExcerpt,
        pageStart = o.pageStart,
        pageEnd = o.pageEnd,
        orderIndex = o.orderIndex,
        gapStatus = gapStatus,
        createdAt = o.createdAt,
    )

    private fun regulationWith(
        from: Regulation,
        status: RegulationStatus = from.status,
        processedAt: Instant? = from.processedAt,
        lastError: String? = from.lastError,
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
    )

    private enum class MapOutcome { MATCHED, GAP }
}
