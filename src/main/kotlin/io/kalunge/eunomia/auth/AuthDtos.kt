package io.kalunge.eunomia.auth

import io.kalunge.eunomia.domain.User
import io.kalunge.eunomia.domain.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class LoginRequest(
    @field:NotBlank
    @field:Email
    val email: String,

    @field:NotBlank
    val password: String,

    /**
     * Slug of the tenant the user is logging into. Multi-tenant systems
     * disambiguate which tenant a login belongs to; we keep this in the
     * request body rather than relying on subdomain routing.
     */
    @field:NotBlank
    @field:Pattern(regexp = "[a-z0-9-]+", message = "tenantSlug must be lower-case alphanumeric with hyphens")
    val tenantSlug: String,
)

data class RegisterRequest(
    @field:NotBlank
    @field:Email
    val email: String,

    @field:NotBlank
    @field:Size(min = 12, max = 128, message = "Password must be 12-128 characters")
    val password: String,

    @field:NotBlank
    @field:Size(min = 1, max = 120)
    val displayName: String,

    @field:NotBlank
    @field:Pattern(regexp = "[a-z0-9-]+")
    val tenantSlug: String,
)

data class AuthResponse(
    val token: String,
    val expiresAt: Instant,
    val user: UserResponse,
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val tenantId: UUID,
) {
    companion object {
        fun from(user: User) = UserResponse(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            role = user.role,
            tenantId = user.tenantId,
        )
    }
}
