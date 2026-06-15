package io.kalunge.eunomia.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One confirmed match between a regulatory obligation and a chunk of
 * internal policy text, written by the Mapper agent. Only matches
 * above the confidence threshold are persisted; below-threshold
 * judgments are logged but not stored.
 *
 * `confidence` is the LLM judge's self-reported probability that the
 * chunk satisfies the obligation. `reasoning` is the one-sentence
 * rationale the judge produced — surfaced to the UI for explainability.
 */
@Entity
@Table(name = "obligation_policy_matches")
class ObligationPolicyMatch(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    override val tenantId: UUID,

    @Column(name = "obligation_id", nullable = false)
    val obligationId: UUID,

    @Column(name = "policy_chunk_id", nullable = false)
    val policyChunkId: UUID,

    @Column(nullable = false)
    val confidence: Double,

    @Column(columnDefinition = "TEXT")
    val reasoning: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) : TenantOwned
