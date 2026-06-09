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
 * State machine for a regulation's progress through the agent workflow.
 *
 * PARSING      — uploaded, Tika still extracting text or Extractor not yet run
 * EXTRACTED    — obligations identified, mapper hasn't run
 * MAPPED       — mappings + gaps computed (Sprint 2)
 * BRIEF_READY  — Summarizer has produced the impact brief (Sprint 3)
 * FAILED       — workflow halted; lastError on the row explains why
 */
enum class RegulationStatus {
    PARSING,
    EXTRACTED,
    MAPPED,
    BRIEF_READY,
    FAILED,
}

@Entity
@Table(name = "regulations")
class Regulation(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    override val tenantId: UUID,

    @Column(nullable = false)
    val title: String,

    @Column(name = "source_filename", nullable = false)
    val sourceFilename: String,

    /**
     * Opaque pointer to where the blob lives. Sprint 1 uses a local
     * `data/` path; production swaps in an S3 key without changing
     * callers.
     */
    @Column(name = "storage_key", nullable = false)
    val storageKey: String,

    @Column(name = "mime_type", nullable = false)
    val mimeType: String,

    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: RegulationStatus = RegulationStatus.PARSING,

    @Column(name = "uploaded_by", nullable = false)
    val uploadedBy: UUID,

    @Column(name = "uploaded_at", nullable = false)
    val uploadedAt: Instant = Instant.now(),

    @Column(name = "processed_at")
    val processedAt: Instant? = null,

    @Column(name = "last_error")
    val lastError: String? = null,
) : TenantOwned
