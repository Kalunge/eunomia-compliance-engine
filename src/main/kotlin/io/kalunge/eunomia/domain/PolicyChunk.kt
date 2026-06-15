package io.kalunge.eunomia.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One text window from a policy along with its embedding (the embedding
 * lives in a pgvector column at the DB level but is omitted from this
 * entity — Hibernate can't natively map `vector(1024)`).
 *
 * Writes go through a JdbcTemplate-backed inserter that includes the
 * embedding in the same INSERT statement; reads of just the relational
 * fields (for the detail-view chunk list) use this entity, and ANN
 * lookups use native `@Query` with the `<=>` cosine-distance operator.
 */
@Entity
@Table(name = "policy_chunks")
class PolicyChunk(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    override val tenantId: UUID,

    @Column(name = "policy_id", nullable = false)
    val policyId: UUID,

    @Column(name = "chunk_index", nullable = false)
    val chunkIndex: Int,

    @Column(nullable = false, columnDefinition = "TEXT")
    val text: String,

    @Column(name = "page_start")
    val pageStart: Int? = null,

    @Column(name = "page_end")
    val pageEnd: Int? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) : TenantOwned
