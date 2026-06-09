package io.kalunge.eunomia.domain

import java.util.UUID

/**
 * Marker interface: every domain entity carries the owning tenant ID.
 *
 * The application layer (Spring interceptor + repository helpers) uses
 * this to enforce that no row is read or written outside the active
 * tenant context. The database also has tenant_id NOT NULL on every
 * table, so the contract is enforced top-to-bottom.
 *
 * Anything in the `domain` package that doesn't implement this should
 * be looked at with suspicion — it likely either belongs in a non-
 * domain package or it's an entity that's missing its tenant column.
 */
interface TenantOwned {
    val tenantId: UUID
}
