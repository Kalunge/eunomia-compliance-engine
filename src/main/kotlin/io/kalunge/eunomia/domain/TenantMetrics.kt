package io.kalunge.eunomia.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Denormalised per-tenant counts powering the dashboard. Single-row
 * lookup per tenant. Updated by TenantMetricsService.recalculate(),
 * called from workflow hooks and a scheduled self-heal job.
 *
 * tenantId is the primary key — there's exactly one row per tenant.
 */
@Entity
@Table(name = "tenant_metrics")
class TenantMetrics(
    @Id
    @Column(name = "tenant_id")
    override val tenantId: UUID,

    @Column(name = "regulations_processed", nullable = false)
    val regulationsProcessed: Long = 0,

    @Column(name = "open_gaps", nullable = false)
    val openGaps: Long = 0,

    @Column(name = "impacted_product_count", nullable = false)
    val impactedProductCount: Long = 0,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
) : TenantOwned
