package io.kalunge.eunomia.auth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * JWT settings loaded from application.yml + env vars.
 *
 * The secret is required — the app refuses to boot without it. We choose
 * fail-fast over a fallback default so a misconfigured deploy never runs
 * with a guessable signing key.
 */
@ConfigurationProperties(prefix = "eunomia.security.jwt")
data class JwtProperties(
    /** Base64-encoded HMAC-SHA256 key. Generate via `openssl rand -base64 64`. */
    val secret: String,
    /** Token lifetime in seconds. 24 hours by default. */
    val expirySeconds: Long = 60 * 60 * 24,
    /** Issuer claim ("iss"). Identifies tokens minted by Eunomia. */
    val issuer: String = "eunomia",
)
