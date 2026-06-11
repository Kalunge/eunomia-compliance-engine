package io.kalunge.eunomia.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One product or service line on the bank's catalogue. Seeded per
 * tenant via Flyway in Sprint 2; a future CRUD endpoint can extend.
 *
 * The ImpactAnalyzer presents the catalogue to the LLM as a numbered
 * list and the agent returns indices — names are user-facing but the
 * stable identifier the saga persists is the UUID.
 */
@Entity
@Table(name = "product_services")
class ProductService(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    override val tenantId: UUID,

    @Column(nullable = false)
    val name: String,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) : TenantOwned
