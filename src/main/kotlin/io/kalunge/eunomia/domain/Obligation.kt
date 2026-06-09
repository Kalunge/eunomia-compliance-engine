package io.kalunge.eunomia.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Gap-status of an obligation. UNKNOWN until the Mapper has run; then
 * either MAPPED (a confident policy match exists) or GAP (no match
 * above the confidence threshold). Defaults to UNKNOWN so newly-
 * extracted obligations don't lie about being "fine."
 */
enum class GapStatus {
    UNKNOWN,
    MAPPED,
    GAP,
}

/**
 * One discrete requirement extracted by the Extractor agent. Many
 * obligations belong to one regulation; order_index preserves the
 * reading order so the UI can show them in document order.
 */
@Entity
@Table(name = "obligations")
class Obligation(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    override val tenantId: UUID,

    @Column(name = "regulation_id", nullable = false)
    val regulationId: UUID,

    @Column(nullable = false)
    val summary: String,

    @Column(name = "source_excerpt", nullable = false, columnDefinition = "TEXT")
    val sourceExcerpt: String,

    @Column(name = "page_start")
    val pageStart: Int? = null,

    @Column(name = "page_end")
    val pageEnd: Int? = null,

    @Column(name = "order_index", nullable = false)
    val orderIndex: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "gap_status", nullable = false)
    val gapStatus: GapStatus = GapStatus.UNKNOWN,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) : TenantOwned
