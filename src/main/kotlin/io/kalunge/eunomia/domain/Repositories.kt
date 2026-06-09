package io.kalunge.eunomia.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Multi-tenant repositories.
 *
 * Convention: every read method takes `tenantId` as its first
 * parameter. There is no method that returns rows without it. A code
 * reviewer (or the compiler, via the method signature) catches
 * accidental cross-tenant access at the call site, before it reaches
 * the database.
 *
 * Spring Data JPA derives the SQL from method names; the
 * `findByXxxAndTenantId` convention makes the tenant filter visible
 * at the call site rather than buried in a query.
 */

@Repository
interface TenantRepository : JpaRepository<Tenant, UUID> {
    fun findBySlug(slug: String): Tenant?
}

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByTenantIdAndEmail(tenantId: UUID, email: String): User?
    fun findByIdAndTenantId(id: UUID, tenantId: UUID): User?
}

@Repository
interface RegulationRepository : JpaRepository<Regulation, UUID> {
    fun findByIdAndTenantId(id: UUID, tenantId: UUID): Regulation?
    fun findAllByTenantIdOrderByUploadedAtDesc(tenantId: UUID, pageable: Pageable): Page<Regulation>
    fun countByTenantId(tenantId: UUID): Long
}

@Repository
interface ObligationRepository : JpaRepository<Obligation, UUID> {
    fun findAllByRegulationIdAndTenantIdOrderByOrderIndexAsc(
        regulationId: UUID,
        tenantId: UUID,
    ): List<Obligation>

    fun countByTenantIdAndGapStatus(tenantId: UUID, gapStatus: GapStatus): Long
}
