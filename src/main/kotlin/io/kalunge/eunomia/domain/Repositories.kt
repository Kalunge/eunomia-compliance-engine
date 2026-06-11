package io.kalunge.eunomia.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
    fun countByTenantIdAndStatusIn(tenantId: UUID, statuses: Collection<RegulationStatus>): Long
}

@Repository
interface ObligationRepository : JpaRepository<Obligation, UUID> {
    fun findAllByRegulationIdAndTenantIdOrderByOrderIndexAsc(
        regulationId: UUID,
        tenantId: UUID,
    ): List<Obligation>

    fun countByTenantIdAndGapStatus(tenantId: UUID, gapStatus: GapStatus): Long
}

@Repository
interface PolicyRepository : JpaRepository<Policy, UUID> {
    fun findByIdAndTenantId(id: UUID, tenantId: UUID): Policy?
    fun findAllByTenantIdOrderByUploadedAtDesc(tenantId: UUID, pageable: Pageable): Page<Policy>
    fun countByTenantId(tenantId: UUID): Long
}

@Repository
interface PolicyChunkRepository : JpaRepository<PolicyChunk, UUID> {
    fun findAllByPolicyIdAndTenantIdOrderByChunkIndexAsc(
        policyId: UUID,
        tenantId: UUID,
    ): List<PolicyChunk>

    fun countByPolicyIdAndTenantId(policyId: UUID, tenantId: UUID): Long

    fun findAllByIdInAndTenantId(ids: Collection<UUID>, tenantId: UUID): List<PolicyChunk>
}

@Repository
interface ObligationPolicyMatchRepository : JpaRepository<ObligationPolicyMatch, UUID> {
    fun findAllByObligationIdAndTenantId(obligationId: UUID, tenantId: UUID): List<ObligationPolicyMatch>

    fun findAllByObligationIdInAndTenantId(
        obligationIds: Collection<UUID>,
        tenantId: UUID,
    ): List<ObligationPolicyMatch>

    fun deleteAllByObligationIdAndTenantId(obligationId: UUID, tenantId: UUID)
}

@Repository
interface ProductServiceRepository : JpaRepository<ProductService, UUID> {
    fun findAllByTenantIdOrderByNameAsc(tenantId: UUID): List<ProductService>
    fun findAllByIdInAndTenantId(ids: Collection<UUID>, tenantId: UUID): List<ProductService>
}

@Repository
interface ObligationImpactRepository : JpaRepository<ObligationImpact, UUID> {
    fun findAllByObligationIdAndTenantId(obligationId: UUID, tenantId: UUID): List<ObligationImpact>
    fun findAllByObligationIdInAndTenantId(
        obligationIds: Collection<UUID>,
        tenantId: UUID,
    ): List<ObligationImpact>
    fun deleteAllByObligationIdAndTenantId(obligationId: UUID, tenantId: UUID)

    @Query("SELECT COUNT(DISTINCT i.productServiceId) FROM ObligationImpact i WHERE i.tenantId = :tenantId")
    fun countDistinctProductsByTenantId(@Param("tenantId") tenantId: UUID): Long
}

@Repository
interface TenantMetricsRepository : JpaRepository<TenantMetrics, UUID> {

    /**
     * Atomic UPSERT: insert a new row or update the existing one for
     * this tenant in a single round-trip. Avoids the SELECT-then-save
     * dance that Spring Data JPA does for manually-assigned IDs.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO tenant_metrics
                (tenant_id, regulations_processed, open_gaps, impacted_product_count, updated_at)
            VALUES (:tenantId, :regulationsProcessed, :openGaps, :impactedProductCount, NOW())
            ON CONFLICT (tenant_id) DO UPDATE SET
                regulations_processed  = EXCLUDED.regulations_processed,
                open_gaps              = EXCLUDED.open_gaps,
                impacted_product_count = EXCLUDED.impacted_product_count,
                updated_at             = NOW()
        """,
        nativeQuery = true,
    )
    fun upsert(
        @Param("tenantId") tenantId: UUID,
        @Param("regulationsProcessed") regulationsProcessed: Long,
        @Param("openGaps") openGaps: Long,
        @Param("impactedProductCount") impactedProductCount: Long,
    )
}
