package io.kalunge.eunomia.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * The bank using Eunomia. Top of the multi-tenancy hierarchy: every
 * domain row in the system traces back to a Tenant.
 *
 * Note that Tenant does not itself implement TenantOwned — it's its
 * own root. Code asking for `entity.tenantId` should use the entity's
 * own ID for Tenant.
 */
@Entity
@Table(name = "tenants")
class Tenant(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, unique = true)
    val slug: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)
