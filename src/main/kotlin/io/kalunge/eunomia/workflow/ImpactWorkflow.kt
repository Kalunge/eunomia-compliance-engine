package io.kalunge.eunomia.workflow

import io.kalunge.eunomia.domain.Obligation
import io.kalunge.eunomia.domain.ObligationImpact
import io.kalunge.eunomia.domain.ObligationImpactRepository
import io.kalunge.eunomia.domain.ObligationRepository
import io.kalunge.eunomia.domain.ProductService
import io.kalunge.eunomia.domain.ProductServiceRepository
import io.kalunge.eunomia.impact.CatalogueItem
import io.kalunge.eunomia.impact.ImpactAnalyzerAgent
import io.kalunge.eunomia.impact.ObligationContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Third agent's workflow — closed-list classification of which
 * products/services each obligation affects. Runs inline within
 * MappingWorkflow rather than as its own @Async stage; see
 * docs/09-impact-analyzer-deep-dive.md for the rationale.
 *
 * Fail-soft by design: per-obligation tagging errors are logged and
 * the obligation simply stays untagged. The workflow never sets the
 * regulation to FAILED on its own — the Mapper is the gating agent
 * for that.
 */
@Service
class ImpactWorkflow(
    private val obligationRepository: ObligationRepository,
    private val productServiceRepository: ProductServiceRepository,
    private val impactRepository: ObligationImpactRepository,
    private val impactAnalyzer: ImpactAnalyzerAgent,
    @Value("\${eunomia.impact.max-per-obligation:6}") private val maxPerObligation: Int,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun tagAll(regulationId: UUID, tenantId: UUID) {
        val catalogue = productServiceRepository.findAllByTenantIdOrderByNameAsc(tenantId)
        if (catalogue.isEmpty()) {
            log.info("Skipping impact tagging — empty product catalogue for tenant {}", tenantId)
            return
        }

        val obligations = obligationRepository.findAllByRegulationIdAndTenantIdOrderByOrderIndexAsc(regulationId, tenantId)
        log.info("Tagging impacts for {} obligations (catalogue size = {})", obligations.size, catalogue.size)

        var tagged = 0
        var untagged = 0
        for (obligation in obligations) {
            val outcome = runCatching { tagOne(obligation, catalogue, tenantId) }
                .onFailure { log.warn("Impact tagging failed for obligation {}: {}", obligation.id, it.message) }
                .getOrDefault(0)
            if (outcome > 0) tagged++ else untagged++
        }

        log.info("Impact tagging complete for regulation {} — {} tagged, {} untagged",
            regulationId, tagged, untagged)
    }

    private fun tagOne(
        obligation: Obligation,
        catalogue: List<ProductService>,
        tenantId: UUID,
    ): Int {
        val indices = impactAnalyzer.tag(
            ObligationContext(obligation.summary, obligation.sourceExcerpt),
            catalogue.map { CatalogueItem(it.name, it.description) },
        )
        // The agent already filters out-of-range, but defend again.
        val validIndices = indices.filter { it in catalogue.indices }.distinct()
        // Hard cap: even with the precision-over-recall prompt, the LLM
        // sometimes returns the whole catalogue on broad obligations.
        // Trim the visual noise without altering the agent's contract.
        val cappedIndices = validIndices.take(maxPerObligation)
        if (validIndices.size > maxPerObligation) {
            log.debug(
                "Obligation {} produced {} indices, capping to {}",
                obligation.id, validIndices.size, maxPerObligation,
            )
        }
        persistImpacts(obligation, tenantId, cappedIndices.map { catalogue[it] })
        return cappedIndices.size
    }

    @Transactional
    internal fun persistImpacts(
        obligation: Obligation,
        tenantId: UUID,
        products: List<ProductService>,
    ) {
        // Idempotent: clear any previous tags before persisting.
        impactRepository.deleteAllByObligationIdAndTenantId(obligation.id, tenantId)
        if (products.isEmpty()) return
        val rows = products.map { product ->
            ObligationImpact(
                tenantId = tenantId,
                obligationId = obligation.id,
                productServiceId = product.id,
            )
        }
        impactRepository.saveAll(rows)
    }
}
