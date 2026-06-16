package io.kalunge.eunomia.diff

/**
 * Strategy interface for the Diff agent. Given a candidate obligation
 * (from the newer regulation) and up to K baseline obligations (the
 * closest semantic matches from the older regulation), the agent
 * classifies the relationship.
 *
 * Empty baselines → caller treats the candidate as ADDED without
 * invoking the agent at all (no point asking the LLM a no-op question).
 * Malformed LLM output → DiffParseException; the service catches and
 * classifies as ADDED per docs/12-regulation-diff.md's failure-modes
 * table.
 */
interface DiffAgent {
    fun judge(candidate: DiffSide, baselines: List<DiffSide>): DiffJudgement
}

/**
 * The Diff agent's verdict on one candidate obligation.
 *
 * - UNCHANGED: matches a baseline; same operational substance
 * - MODIFIED:  matches a baseline; substance shifted (threshold,
 *              timeframe, scope, etc.) — rationale states what
 * - ADDED:     no baseline plausibly covers the same ground
 *
 * `baselineIndex` is the position of the matched baseline in the input
 * list for UNCHANGED and MODIFIED; null for ADDED. The service uses
 * `verdict` (not baselineIndex presence) as the source of truth so a
 * malformed pair where the LLM returned ADDED with a stray index is
 * treated as ADDED.
 */
data class DiffJudgement(
    val verdict: DiffVerdict,
    val baselineIndex: Int?,
    val rationale: String,
)

enum class DiffVerdict { UNCHANGED, MODIFIED, ADDED }

class DiffParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
