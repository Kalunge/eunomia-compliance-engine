package io.kalunge.eunomia.summarize

import io.kalunge.eunomia.domain.GapStatus
import java.time.Instant

/**
 * Fourth and final agent in Eunomia's saga. Composes a Markdown impact
 * brief from the structured obligations, mappings, and product impacts
 * the upstream agents already produced.
 *
 * Unlike Extractor / Mapper / ImpactAnalyzer this is *composition* —
 * the agent doesn't invent facts, just arranges them into prose.
 */
interface SummarizerAgent {
    fun compose(input: BriefInput): String
}

data class BriefInput(
    val regulationTitle: String,
    val sourceFilename: String,
    val uploadedAt: Instant,
    val obligations: List<BriefObligationInput>,
)

data class BriefObligationInput(
    val summary: String,
    val sourceExcerpt: String,
    val gapStatus: GapStatus,
    val impactedProducts: List<String>,
    val matches: List<BriefMatchInput>,
)

data class BriefMatchInput(
    val policyTitle: String,
    val chunkText: String,
    val confidence: Double,
)

class BriefGenerationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
