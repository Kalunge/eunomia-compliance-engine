package io.kalunge.eunomia.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * One persisted moment in a regulation's saga. Published by
 * WorkflowEventPublisher and consumed via SSE.
 *
 * `payload` is stored as Postgres JSONB but treated as opaque JSON
 * string at the Hibernate boundary — the application encodes/decodes
 * via Jackson when reading or writing, and the SSE endpoint passes
 * the raw string straight through to the browser.
 */
@Entity
@Table(name = "workflow_events")
class WorkflowEvent(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    override val tenantId: UUID,

    @Column(name = "regulation_id", nullable = false)
    val regulationId: UUID,

    @Column(name = "sequence_no", nullable = false)
    val sequenceNo: Long,

    @Column(name = "event_type", nullable = false)
    val eventType: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    val payload: String,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant = Instant.now(),
) : TenantOwned
