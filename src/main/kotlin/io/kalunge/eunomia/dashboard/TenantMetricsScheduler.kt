package io.kalunge.eunomia.dashboard

import io.kalunge.eunomia.domain.TenantRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Self-heal job for tenant_metrics. Recomputes every tenant's row
 * every 15 minutes so any synchronous hook that was missed (a manual
 * SQL insert, a migration backfill, a deploy that briefly disabled
 * the hook) is caught and reconciled without operator action.
 *
 * Cheap on our scale — three COUNT queries per tenant. A bank with
 * 100 tenants would run 300 queries every 15 minutes, well within
 * any pgvector / Postgres budget.
 */
@Component
class TenantMetricsScheduler(
    private val tenantRepository: TenantRepository,
    private val tenantMetricsService: TenantMetricsService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 */15 * * * *")
    fun reconcileAll() {
        val tenants = tenantRepository.findAll()
        if (tenants.isEmpty()) return

        log.debug("Reconciling tenant_metrics for {} tenants", tenants.size)
        var failures = 0
        for (tenant in tenants) {
            runCatching { tenantMetricsService.recalculate(tenant.id) }
                .onFailure {
                    failures++
                    log.warn("Metrics reconciliation failed for tenant {}: {}", tenant.id, it.message)
                }
        }
        if (failures > 0) {
            log.warn("Tenant metrics reconciliation: {} of {} failed", failures, tenants.size)
        }
    }
}
