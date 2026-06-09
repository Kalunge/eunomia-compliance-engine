package io.kalunge.eunomia.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import io.kalunge.eunomia.domain.User
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Mints and verifies JWTs. Single source of truth for the token format.
 *
 * Claim conventions (see docs/04-auth-jwt-deep-dive.md):
 *   sub  = user_id (UUID string)
 *   tid  = tenant_id (UUID string)
 *   rol  = role (ADMIN | MEMBER)
 *   name = display name (UI convenience, never used for auth)
 *   iss  = "eunomia"
 *   iat  = issued-at unix seconds
 *   exp  = expiry unix seconds
 */
@Service
class JwtService(private val props: JwtProperties) {

    private val key: SecretKey by lazy {
        // Accept either raw bytes or base64; opportunistically decode so the
        // operator can paste either form into the env var.
        val rawBytes = runCatching { Decoders.BASE64.decode(props.secret) }
            .getOrElse { props.secret.toByteArray() }
        require(rawBytes.size >= 32) {
            "JWT secret must be at least 256 bits (32 bytes). Got ${rawBytes.size} bytes. " +
                "Generate one via: openssl rand -base64 64"
        }
        Keys.hmacShaKeyFor(rawBytes)
    }

    fun issue(user: User): IssuedToken {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(props.expirySeconds)
        val token = Jwts.builder()
            .subject(user.id.toString())
            .claim(CLAIM_TENANT_ID, user.tenantId.toString())
            .claim(CLAIM_ROLE, user.role.name)
            .claim(CLAIM_DISPLAY_NAME, user.displayName)
            .issuer(props.issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key, Jwts.SIG.HS256)
            .compact()
        return IssuedToken(token = token, expiresAt = expiresAt)
    }

    /**
     * Verifies signature, issuer, and expiry. Throws JwtException on any
     * failure — the filter translates that into an HTTP 401.
     */
    fun verify(token: String): VerifiedClaims {
        val claims: Claims = Jwts.parser()
            .verifyWith(key)
            .requireIssuer(props.issuer)
            .build()
            .parseSignedClaims(token)
            .payload

        val userId = UUID.fromString(claims.subject)
        val tenantId = UUID.fromString(claims[CLAIM_TENANT_ID]?.toString() ?: throw JwtException("missing tid claim"))
        val role = claims[CLAIM_ROLE]?.toString() ?: throw JwtException("missing rol claim")
        val displayName = claims[CLAIM_DISPLAY_NAME]?.toString() ?: ""
        return VerifiedClaims(userId, tenantId, role, displayName)
    }

    companion object {
        const val CLAIM_TENANT_ID = "tid"
        const val CLAIM_ROLE = "rol"
        const val CLAIM_DISPLAY_NAME = "name"
    }
}

data class IssuedToken(
    val token: String,
    val expiresAt: Instant,
)

data class VerifiedClaims(
    val userId: UUID,
    val tenantId: UUID,
    val role: String,
    val displayName: String,
)
