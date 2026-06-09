package io.kalunge.eunomia.auth

import io.kalunge.eunomia.domain.Tenant
import io.kalunge.eunomia.domain.TenantRepository
import io.kalunge.eunomia.domain.User
import io.kalunge.eunomia.domain.UserRepository
import io.kalunge.eunomia.domain.UserRole
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Auth business logic. Stays thin — the controller does HTTP, the
 * JwtService does crypto, the repositories do persistence.
 *
 * Errors are typed (BadCredentialsException, TenantNotFoundException,
 * EmailAlreadyRegisteredException) so the controller advice can map them
 * to specific HTTP responses without sniffing exception messages.
 */
@Service
class AuthService(
    private val tenantRepository: TenantRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {

    @Transactional(readOnly = true)
    fun login(request: LoginRequest): AuthResponse {
        val tenant = findTenant(request.tenantSlug)
        val user = userRepository.findByTenantIdAndEmail(tenant.id, request.email.lowercase())
            ?: throw BadCredentialsException()

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw BadCredentialsException()
        }

        return issueResponse(user)
    }

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        val tenant = findTenant(request.tenantSlug)
        val normalizedEmail = request.email.lowercase()

        if (userRepository.findByTenantIdAndEmail(tenant.id, normalizedEmail) != null) {
            throw EmailAlreadyRegisteredException()
        }

        val user = User(
            tenantId = tenant.id,
            email = normalizedEmail,
            passwordHash = passwordEncoder.encode(request.password),
            displayName = request.displayName,
            role = UserRole.MEMBER,
        )
        userRepository.save(user)

        return issueResponse(user)
    }

    private fun findTenant(slug: String): Tenant =
        tenantRepository.findBySlug(slug) ?: throw TenantNotFoundException(slug)

    private fun issueResponse(user: User): AuthResponse {
        val issued = jwtService.issue(user)
        return AuthResponse(
            token = issued.token,
            expiresAt = issued.expiresAt,
            user = UserResponse.from(user),
        )
    }
}

class BadCredentialsException : RuntimeException("Invalid email or password.")
class EmailAlreadyRegisteredException : RuntimeException("Email already registered for this tenant.")
class TenantNotFoundException(slug: String) : RuntimeException("No tenant with slug '$slug'.")
