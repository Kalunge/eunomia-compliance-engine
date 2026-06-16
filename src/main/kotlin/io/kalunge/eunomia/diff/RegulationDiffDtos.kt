package io.kalunge.eunomia.diff

import java.util.UUID

/**
 * Request body for POST /api/v1/diff. The service validates that both
 * regulations belong to the caller's tenant, are in MAPPED+ state, and
 * are not the same regulation.
 */
data class RegulationDiffRequest(
    val baselineId: UUID,
    val candidateId: UUID,
)

/**
 * The full response from a diff run. The four buckets cover the
 * relationship between baseline (older) and candidate (newer)
 * obligations exhaustively — every baseline appears in exactly one of
 * `unchanged`, `modified`, or `removed`; every candidate appears in
 * exactly one of `unchanged`, `modified`, or `added`.
 */
data class RegulationDiff(
    val baselineId: UUID,
    val baselineTitle: String,
    val candidateId: UUID,
    val candidateTitle: String,
    val unchanged: List<DiffPair>,
    val modified: List<DiffPair>,
    val added: List<DiffSide>,
    val removed: List<DiffSide>,
    val summary: DiffSummary,
)

/**
 * One matched pair across versions. For UNCHANGED pairs, `rationale`
 * is the LLM's explanation of why they're the same rule despite
 * wording differences. For MODIFIED pairs, `rationale` quotes the
 * specific element that shifted (e.g., "threshold raised from KES
 * 50,000 to KES 70,000").
 */
data class DiffPair(
    val baseline: DiffSide,
    val candidate: DiffSide,
    val rationale: String,
)

/**
 * One obligation seen from either side of the diff. Same shape on
 * both sides; the field names in DiffPair distinguish baseline from
 * candidate. The agent operates on these directly — no separate
 * agent-internal type needed.
 */
data class DiffSide(
    val obligationId: UUID,
    val orderIndex: Int,
    val summary: String,
    val sourceExcerpt: String,
)

/**
 * Aggregate counts plus the derived materialChangeScore. The score is
 * (modified + added + removed) / max(totalBaseline, totalCandidate).
 * 0.0 means a no-op amendment (everything restated identically); 1.0
 * means a completely new regulation that happens to share a title.
 *
 * The UI shows the score as a percentage and uses it to colour the
 * banner ("minor", "material", "substantial change").
 */
data class DiffSummary(
    val totalBaseline: Int,
    val totalCandidate: Int,
    val unchanged: Int,
    val modified: Int,
    val added: Int,
    val removed: Int,
    val materialChangeScore: Double,
)
