package io.kalunge.eunomia.dashboard

import io.kalunge.eunomia.domain.TenantRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Self-heal for tenant_metrics — runs on two triggers:
 *
 *   1. **Startup** (ApplicationReadyEvent) — catches any stale row
 *      left by a deploy that introduced the recompute hook against
 *      pre-existing data (rows seeded at zero from V5, regulations
 *      processed before the hook existed, etc.).
 *
 *   2. **Every 15 minutes** (cron) — catches any drift in the
 *      live system if a synchronous hook is ever missed (a manual
 *      SQL insert, an aborted workflow, a migration backfill).
 *
 * Cheap on our scale — three COUNT queries per tenant. A bank with
 * 100 tenants runs 300 queries per pass, well within any Postgres
 * budget.
 */
@Component
class TenantMetricsScheduler(
    private val tenantRepository: TenantRepository,
    private val tenantMetricsService: TenantMetricsService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun reconcileOnStartup() {
        log.info("Startup reconciliation of tenant_metrics")
        reconcileAll()
    }

    @Scheduled(cron = "0 */15 * * * *")
    fun scheduledReconcile() {
        reconcileAll()
    }

    private fun reconcileAll() {
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
