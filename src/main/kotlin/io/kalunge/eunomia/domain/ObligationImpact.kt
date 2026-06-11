package io.kalunge.eunomia.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Asserts that a regulatory obligation directly affects a specific
 * product or service line, as judged by the ImpactAnalyzer agent.
 * One row per (obligation, product) pair; unique on that pair.
 */
@Entity
@Table(name = "obligation_impacts")
class ObligationImpact(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    override val tenantId: UUID,

    @Column(name = "obligation_id", nullable = false)
    val obligationId: UUID,

    @Column(name = "product_service_id", nullable = false)
    val productServiceId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) : TenantOwned
