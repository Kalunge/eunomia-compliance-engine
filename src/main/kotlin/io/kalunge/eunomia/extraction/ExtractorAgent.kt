package io.kalunge.eunomia.extraction

/**
 * The first agent in the workflow. Takes the plain-text body of a
 * regulatory document and emits the discrete obligations it identifies.
 *
 * Kept as an interface so production code uses the LangChain4j-backed
 * implementation but tests can swap in a deterministic stub.
 */
interface ExtractorAgent {
    fun extract(documentText: String): List<ExtractedObligation>
}

/**
 * One discrete requirement extracted from a regulation. Maps onto an
 * Obligation domain entity after the workflow persists it.
 *
 * pageStart/pageEnd are nullable — Sprint 1's Tika pipeline doesn't
 * emit page markers. Sprint 2 layers PDF-aware splitting on top and
 * starts populating them.
 */
data class ExtractedObligation(
    val summary: String,
    val sourceExcerpt: String,
    val pageStart: Int? = null,
    val pageEnd: Int? = null,
)
