package io.kalunge.eunomia.dashboard

import io.kalunge.eunomia.domain.GapStatus
import io.kalunge.eunomia.domain.ObligationImpactRepository
import io.kalunge.eunomia.domain.ObligationRepository
import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.RegulationStatus
import io.kalunge.eunomia.domain.TenantMetricsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Maintains the denormalised tenant_metrics row for a tenant.
 *
 * Recalculation runs three cheap COUNT queries against the
 * authoritative tables and UPSERTs the result in one round-trip.
 *
 * Marked REQUIRES_NEW so callers in read-only transactions (e.g. the
 * DashboardService loading a missing row on first access) don't fail
 * with "read-only transaction can't write".
 */
@Service
class TenantMetricsService(
    private val tenantMetricsRepository: TenantMetricsRepository,
    private val regulationRepository: RegulationRepository,
    private val obligationRepository: ObligationRepository,
    private val impactRepository: ObligationImpactRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Recompute and persist the three metrics for one tenant. Safe to
     * call from anywhere — idempotent and self-contained.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recalculate(tenantId: UUID) {
        val regulationsProcessed = regulationRepository.countByTenantIdAndStatusIn(
            tenantId,
            PROCESSED_STATUSES,
        )
        val openGaps = obligationRepository.countByTenantIdAndGapStatus(tenantId, GapStatus.GAP)
        val impactedProductCount = impactRepository.countDistinctProductsByTenantId(tenantId)

        tenantMetricsRepository.upsert(
            tenantId = tenantId,
            regulationsProcessed = regulationsProcessed,
            openGaps = openGaps,
            impactedProductCount = impactedProductCount,
        )
        log.debug(
            "Recalculated metrics for tenant {} — regulations={} gaps={} products={}",
            tenantId, regulationsProcessed, openGaps, impactedProductCount,
        )
    }

    companion object {
        // Anything past PARSING counts as "processed". FAILED is
        // excluded — a regulation that didn't finish extraction
        // doesn't contribute to the dashboard's "processed" tally.
        private val PROCESSED_STATUSES = setOf(
            RegulationStatus.EXTRACTED,
            RegulationStatus.MAPPED,
            RegulationStatus.BRIEF_READY,
        )
    }
}
