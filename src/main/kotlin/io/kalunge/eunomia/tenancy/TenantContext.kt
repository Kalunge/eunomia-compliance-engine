package io.kalunge.eunomia.tenancy

import java.util.UUID

/**
 * Request-scoped holder for the current tenant ID.
 *
 * The JWT filter sets this once per request after verifying the token;
 * controllers and services read it via [requireTenantId]. Backed by
 * ThreadLocal, which is sufficient for Spring MVC's thread-per-request
 * model. Async code (coroutines, CompletableFutures) must propagate the
 * tenant ID explicitly — the holder does not follow async context.
 *
 * The filter is responsible for clearing the holder after the request
 * (try/finally) so a pooled thread doesn't leak one request's tenant
 * into the next.
 */
object TenantContext {

    private val holder = ThreadLocal<UUID>()

    fun setTenantId(tenantId: UUID) {
        holder.set(tenantId)
    }

    fun tenantId(): UUID? = holder.get()

    /**
     * Fail loudly if called outside an authenticated request. Controllers
     * use this; service-layer code that legitimately runs without a tenant
     * (e.g., the seeded-admin bootstrap on startup) calls [tenantId] and
     * checks for null instead.
     */
    fun requireTenantId(): UUID =
        tenantId() ?: error("No tenant in context. This code path must run inside an authenticated request.")

    fun clear() {
        holder.remove()
    }
}
