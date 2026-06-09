package io.kalunge.eunomia.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class UserRole {
    ADMIN,
    MEMBER,
}

/**
 * A login identity scoped to a tenant. Email is unique per tenant (not
 * globally) — two banks may legitimately share an email shape.
 */
@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    override val tenantId: UUID,

    @Column(nullable = false)
    val email: String,

    /** BCrypt hash. Never log this. */
    @Column(name = "password_hash", nullable = false)
    val passwordHash: String,

    @Column(name = "display_name", nullable = false)
    val displayName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: UserRole = UserRole.MEMBER,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
) : TenantOwned
