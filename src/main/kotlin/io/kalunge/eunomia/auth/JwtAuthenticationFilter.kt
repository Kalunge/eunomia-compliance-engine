package io.kalunge.eunomia.auth

import io.jsonwebtoken.JwtException
import io.kalunge.eunomia.tenancy.TenantContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Reads the Authorization header, verifies the JWT, populates the
 * SecurityContext and the TenantContext.
 *
 * Bearer-only — basic auth, cookies, and other schemes are not supported.
 *
 * Crucially: TenantContext is cleared in a finally block so a pooled
 * Tomcat worker thread doesn't leak one request's tenant into the next.
 */
@Component
class JwtAuthenticationFilter(private val jwtService: JwtService) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val token = extractBearerToken(request)
            if (token != null) {
                authenticate(token)
            }
            filterChain.doFilter(request, response)
        } finally {
            TenantContext.clear()
            SecurityContextHolder.clearContext()
        }
    }

    private fun extractBearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        return if (header.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            header.substring(BEARER_PREFIX.length).trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }
    }

    private fun authenticate(token: String) {
        try {
            val claims = jwtService.verify(token)
            val auth = UsernamePasswordAuthenticationToken(
                claims.userId,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${claims.role}")),
            )
            SecurityContextHolder.getContext().authentication = auth
            TenantContext.setTenantId(claims.tenantId)
        } catch (e: JwtException) {
            // Log the failure without the token. Spring Security will respond
            // 401 because the SecurityContext stays empty.
            log.debug("Rejecting JWT: {}", e.javaClass.simpleName)
        }
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
