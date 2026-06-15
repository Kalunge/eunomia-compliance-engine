package io.kalunge.eunomia.dashboard

import io.kalunge.eunomia.domain.GapStatus
import io.kalunge.eunomia.domain.ObligationRepository
import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.TenantMetricsRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DashboardService(
    private val tenantMetricsRepository: TenantMetricsRepository,
    private val tenantMetricsService: TenantMetricsService,
    private val regulationRepository: RegulationRepository,
    private val obligationRepository: ObligationRepository,
) {

    @Transactional(readOnly = true)
    fun summary(tenantId: UUID): DashboardSummary {
        val metrics = tenantMetricsRepository.findById(tenantId).orElse(null)
            ?: run {
                // First dashboard load for this tenant — materialise the row
                // (REQUIRES_NEW propagation lets this write from inside our
                // read-only enclosing transaction).
                tenantMetricsService.recalculate(tenantId)
                tenantMetricsRepository.findById(tenantId).orElse(null)
            }

        val recentPage = regulationRepository.findAllByTenantIdOrderByUploadedAtDesc(
            tenantId, PageRequest.of(0, RECENT_LIMIT),
        )
        val recent = recentPage.content.map { r ->
            val obligations = obligationRepository
                .findAllByRegulationIdAndTenantIdOrderByOrderIndexAsc(r.id, tenantId)
            val gaps = obligations.count { it.gapStatus == GapStatus.GAP }
            RecentRegulationView.from(r, obligations.size, gaps)
        }

        return DashboardSummary(
            regulationsProcessed = metrics?.regulationsProcessed ?: 0,
            openGaps = metrics?.openGaps ?: 0,
            impactedProductCount = metrics?.impactedProductCount ?: 0,
            recentActivity = recent,
        )
    }

    companion object {
        const val RECENT_LIMIT = 10
    }
}
