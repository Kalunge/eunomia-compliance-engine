package io.kalunge.eunomia.diff

import io.kalunge.eunomia.domain.Obligation
import io.kalunge.eunomia.domain.ObligationRepository
import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.RegulationStatus
import io.kalunge.eunomia.embedding.EmbeddingService
import io.kalunge.eunomia.regulation.RegulationNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Orchestrates the obligation-level diff between two regulations
 * belonging to the same tenant. See docs/12-regulation-diff.md for the
 * full design.
 *
 * Algorithm (per the doc):
 *  1. Validate both regulations: same tenant, both in MAPPED+ state,
 *     not the same regulation.
 *  2. Batch-embed all baseline obligation summaries (Cohere
 *     search_document).
 *  3. For each candidate obligation:
 *     a. Embed as a search_query.
 *     b. Compute cosine distance to every baseline; take top-K.
 *     c. If top-1 distance > MAX_PAIR_DISTANCE → classify as ADDED
 *        without invoking the LLM (no plausible match).
 *     d. Otherwise, send candidate + K baselines to the DiffAgent for
 *        a verdict. Parse failure → ADDED with a logged warning.
 *  4. Apply MAPPER_CALL_DELAY_MS between LLM judge calls to stay under
 *     Groq's free-tier TPM cap.
 *  5. Baseline obligations not claimed by any candidate (UNCHANGED or
 *     MODIFIED) are emitted as REMOVED.
 *
 * The endpoint is synchronous, no persistence — re-running is cheap
 * and the inputs (obligations) don't drift between runs.
 */
@Service
class RegulationDiffService(
    private val regulationRepository: RegulationRepository,
    private val obligationRepository: ObligationRepository,
    private val embeddingService: EmbeddingService,
    private val diffAgent: DiffAgent,
    @Value("\${eunomia.diff.top-k:3}") private val topK: Int,
    @Value("\${eunomia.diff.max-pair-distance:0.5}") private val maxPairDistance: Double,
    @Value("\${eunomia.mapper.call-delay-ms:4000}") private val callDelayMs: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun diff(tenantId: UUID, baselineId: UUID, candidateId: UUID): RegulationDiff {
        require(baselineId != candidateId) {
            "Cannot diff a regulation against itself."
        }

        val baseline = regulationRepository.findByIdAndTenantId(baselineId, tenantId)
            ?: throw RegulationNotFoundException(baselineId)
        val candidate = regulationRepository.findByIdAndTenantId(candidateId, tenantId)
            ?: throw RegulationNotFoundException(candidateId)

        if (baseline.status !in DIFFABLE_STATUSES) {
            throw RegulationNotDiffableException(baselineId, baseline.status)
        }
        if (candidate.status !in DIFFABLE_STATUSES) {
            throw RegulationNotDiffableException(candidateId, candidate.status)
        }

        val baselineObligations = obligationRepository
            .findAllByRegulationIdAndTenantIdOrderByOrderIndexAsc(baselineId, tenantId)
        val candidateObligations = obligationRepository
            .findAllByRegulationIdAndTenantIdOrderByOrderIndexAsc(candidateId, tenantId)

        log.info(
            "Diffing regulation {} ({} obligations) vs {} ({} obligations) for tenant {}",
            baselineId, baselineObligations.size, candidateId, candidateObligations.size, tenantId,
        )

        // Batch-embed the baselines once. Cohere bills per request, not
        // per token, so one batched call is much cheaper than K
        // individual queries.
        val baselineVectors = if (baselineObligations.isNotEmpty()) {
            embeddingService.embedDocuments(baselineObligations.map { it.summary })
        } else {
            emptyList()
        }

        val matchedBaselineIds = HashSet<UUID>()
        val unchanged = ArrayList<DiffPair>()
        val modified = ArrayList<DiffPair>()
        val added = ArrayList<DiffSide>()

        for ((idx, candidateOb) in candidateObligations.withIndex()) {
            val candidateSide = candidateOb.toDiffSide()

            if (baselineObligations.isEmpty()) {
                // Nothing to compare against — every candidate is new.
                added.add(candidateSide)
                continue
            }

            val candidateVec = embeddingService.embedQuery(candidateOb.summary)
            val topMatches = topKByCosineDistance(candidateVec, baselineObligations, baselineVectors, topK)

            if (topMatches.first().distance > maxPairDistance) {
                // Closest baseline is too far away — no plausible
                // pairing, classify as ADDED without invoking the LLM.
                added.add(candidateSide)
                log.debug(
                    "Candidate obligation {} classified ADDED by distance gate (top distance {} > {})",
                    candidateOb.id, topMatches.first().distance, maxPairDistance,
                )
            } else {
                val baselines = topMatches.map { it.obligation.toDiffSide() }
                val judgement = runCatching { diffAgent.judge(candidateSide, baselines) }
                    .onFailure {
                        log.warn(
                            "DiffAgent judge failed for candidate {} — classifying ADDED: {}",
                            candidateOb.id, it.message,
                        )
                    }
                    .getOrElse {
                        DiffJudgement(
                            verdict = DiffVerdict.ADDED,
                            baselineIndex = null,
                            rationale = "LLM judge produced unparseable output; classified as added defensively.",
                        )
                    }

                when (judgement.verdict) {
                    DiffVerdict.UNCHANGED -> {
                        val matched = topMatches[judgement.baselineIndex!!].obligation
                        unchanged.add(DiffPair(matched.toDiffSide(), candidateSide, judgement.rationale))
                        matchedBaselineIds.add(matched.id)
                    }
                    DiffVerdict.MODIFIED -> {
                        val matched = topMatches[judgement.baselineIndex!!].obligation
                        modified.add(DiffPair(matched.toDiffSide(), candidateSide, judgement.rationale))
                        matchedBaselineIds.add(matched.id)
                    }
                    DiffVerdict.ADDED -> {
                        added.add(candidateSide)
                    }
                }
            }

            // Throttle between LLM judge calls so we stay under Groq's
            // free-tier TPM. Same wait as the Mapper. Skip after the
            // last candidate.
            if (idx < candidateObligations.size - 1 && callDelayMs > 0) {
                try {
                    Thread.sleep(callDelayMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    log.warn("Diff interrupted between candidates; returning partial result")
                    break
                }
            }
        }

        val removed = baselineObligations
            .filterNot { it.id in matchedBaselineIds }
            .map { it.toDiffSide() }

        val summary = DiffSummary(
            totalBaseline = baselineObligations.size,
            totalCandidate = candidateObligations.size,
            unchanged = unchanged.size,
            modified = modified.size,
            added = added.size,
            removed = removed.size,
            materialChangeScore = computeMaterialChangeScore(
                modified = modified.size,
                added = added.size,
                removed = removed.size,
                totalBaseline = baselineObligations.size,
                totalCandidate = candidateObligations.size,
            ),
        )

        return RegulationDiff(
            baselineId = baseline.id,
            baselineTitle = baseline.title,
            candidateId = candidate.id,
            candidateTitle = candidate.title,
            unchanged = unchanged,
            modified = modified,
            added = added,
            removed = removed,
            summary = summary,
        )
    }

    private fun topKByCosineDistance(
        queryVec: FloatArray,
        obligations: List<Obligation>,
        vectors: List<FloatArray>,
        k: Int,
    ): List<Match> {
        // In-memory cosine over ~10-15 baselines per diff; trivially
        // fast vs the LLM call that follows. No need for an index.
        return obligations.zip(vectors)
            .map { (ob, vec) -> Match(ob, cosineDistance(queryVec, vec)) }
            .sortedBy { it.distance }
            .take(k)
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) {
            "Vector dimension mismatch: ${a.size} vs ${b.size}"
        }
        var dot = 0.0
        var magA = 0.0
        var magB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            magA += a[i] * a[i]
            magB += b[i] * b[i]
        }
        val mags = sqrt(magA) * sqrt(magB)
        if (mags == 0.0) return 1.0
        return 1.0 - (dot / mags)
    }

    private fun computeMaterialChangeScore(
        modified: Int,
        added: Int,
        removed: Int,
        totalBaseline: Int,
        totalCandidate: Int,
    ): Double {
        val denominator = max(totalBaseline, totalCandidate).coerceAtLeast(1)
        return (modified + added + removed).toDouble() / denominator
    }

    private fun Obligation.toDiffSide(): DiffSide = DiffSide(
        obligationId = this.id,
        orderIndex = this.orderIndex,
        summary = this.summary,
        sourceExcerpt = this.sourceExcerpt,
    )

    private data class Match(val obligation: Obligation, val distance: Double)

    companion object {
        // Diffable = mapping is complete. Brief generation may be in
        // flight or done; both are fine for diff purposes since the
        // obligations don't change after mapping.
        val DIFFABLE_STATUSES: Set<RegulationStatus> = setOf(
            RegulationStatus.MAPPED,
            RegulationStatus.BRIEF_GENERATING,
            RegulationStatus.BRIEF_READY,
        )
    }
}

class RegulationNotDiffableException(
    val regulationId: UUID,
    val currentStatus: RegulationStatus,
) : RuntimeException(
    "Regulation $regulationId is in status $currentStatus; must be MAPPED or later to diff.",
)
