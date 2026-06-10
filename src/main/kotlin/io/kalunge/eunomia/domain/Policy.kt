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
 * Lifecycle of an uploaded internal policy.
 *
 * PARSING  — uploaded, chunker/embedder hasn't finished yet
 * INDEXED  — chunks persisted and embedded; policy is searchable by the Mapper
 * FAILED   — chunking or embedding threw; lastError on the row explains why
 */
enum class PolicyStatus {
    PARSING,
    INDEXED,
    FAILED,
}

@Entity
@Table(name = "policies")
class Policy(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    override val tenantId: UUID,

    @Column(nullable = false)
    val title: String,

    @Column(name = "source_filename", nullable = false)
    val sourceFilename: String,

    @Column(name = "storage_key", nullable = false)
    val storageKey: String,

    @Column(name = "mime_type", nullable = false)
    val mimeType: String,

    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: PolicyStatus = PolicyStatus.PARSING,

    @Column(name = "uploaded_by", nullable = false)
    val uploadedBy: UUID,

    @Column(name = "uploaded_at", nullable = false)
    val uploadedAt: Instant = Instant.now(),

    @Column(name = "processed_at")
    val processedAt: Instant? = null,

    @Column(name = "last_error")
    val lastError: String? = null,
) : TenantOwned
