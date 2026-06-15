package io.kalunge.eunomia.impact

/**
 * Strategy interface for the ImpactAnalyzer. Closed-list classifier:
 * given an obligation and the tenant's product/service catalogue,
 * returns the indices of catalogue entries the obligation affects.
 *
 * Indices that aren't in [0, catalogue.size) are the caller's
 * responsibility to filter — implementations don't trust the LLM to
 * stay in range and neither should the workflow.
 */
interface ImpactAnalyzerAgent {
    fun tag(obligation: ObligationContext, catalogue: List<CatalogueItem>): List<Int>
}

/**
 * What the ImpactAnalyzer sees about an obligation. Same shape as the
 * Mapper's context but a separate class so the two agents can evolve
 * independently without coupling the impact package to the mapping
 * package.
 */
data class ObligationContext(
    val summary: String,
    val sourceExcerpt: String,
)

data class CatalogueItem(
    val name: String,
    val description: String?,
)

class ImpactParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
