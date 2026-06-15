package io.kalunge.eunomia.mapping

import java.util.UUID

/**
 * Strategy interface for the Mapper agent. Given an obligation and a
 * list of candidate policy chunks already selected by ANN search, the
 * agent decides which (if any) satisfy the obligation and at what
 * confidence.
 *
 * Returns one judgment per candidate. Empty candidate list → empty
 * judgments. Malformed LLM output → MappingParseException.
 */
interface MapperAgent {
    fun judge(obligation: ObligationContext, candidates: List<ChunkCandidate>): List<MappingJudgment>
}

/**
 * What the Mapper sees about the obligation. Summary is the Extractor's
 * paraphrase; sourceExcerpt is the verbatim regulatory text. Giving
 * both protects the judge against Extractor paraphrase drift.
 */
data class ObligationContext(
    val summary: String,
    val sourceExcerpt: String,
)

/**
 * One candidate returned by the ANN search, with the cosine distance
 * pgvector computed. Lower distance = closer in meaning.
 */
data class ChunkCandidate(
    val chunkId: UUID,
    val policyId: UUID,
    val chunkIndex: Int,
    val text: String,
    val distance: Double,
)

/**
 * The Mapper's verdict on one candidate. `candidateIndex` is the index
 * of the candidate in the input list (so callers can correlate without
 * matching on chunk text). `satisfied` is the LLM's categorical call;
 * `confidence` is its 0-1 self-reported probability. The workflow uses
 * confidence (not satisfied) for the threshold decision so calibration
 * lives in one place.
 */
data class MappingJudgment(
    val candidateIndex: Int,
    val satisfied: Boolean,
    val confidence: Double,
    val reasoning: String?,
)

class MappingParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
