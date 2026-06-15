package io.kalunge.eunomia.auth

import io.kalunge.eunomia.domain.UserRepository
import io.kalunge.eunomia.tenancy.TenantContext
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val userRepository: UserRepository,
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse =
        authService.login(request)

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request))

    /**
     * Returns the authenticated user. Useful for the frontend to refresh
     * its in-memory copy after a page reload.
     */
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal userId: UUID): UserResponse {
        val tenantId = TenantContext.requireTenantId()
        val user = userRepository.findByIdAndTenantId(userId, tenantId)
            ?: throw IllegalStateException("Authenticated user disappeared from the database.")
        return UserResponse.from(user)
    }

    // ── Error mapping ────────────────────────────────────────────────

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorBody("invalid_credentials", "Invalid email or password."))

    @ExceptionHandler(TenantNotFoundException::class)
    fun handleTenantNotFound(e: TenantNotFoundException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorBody("tenant_not_found", e.message ?: "Tenant not found."))

    @ExceptionHandler(EmailAlreadyRegisteredException::class)
    fun handleEmailTaken(): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorBody("email_taken", "An account with that email already exists for this tenant."))
}

data class ErrorBody(val code: String, val message: String)
