-- Sprint 1 schema: users (for auth) + regulations + obligations.
--
-- Multi-tenancy convention: every domain table carries tenant_id NOT NULL
-- with a FK back to tenants(id) and a covering index. The application layer
-- (Spring interceptor + JPA filter) enforces that no query runs without a
-- tenant context, but the database is the final guarantee — no orphan rows.

-- ── Users ────────────────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email           TEXT NOT NULL,
    password_hash   TEXT NOT NULL,
    display_name    TEXT NOT NULL,
    role            TEXT NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('ADMIN', 'MEMBER')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Email unique per tenant, not globally. Two banks can share email shapes.
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email)
);
CREATE INDEX idx_users_tenant ON users(tenant_id);

-- No seed admin user. The first user for a tenant is created via
-- POST /api/v1/auth/register so the password is set by the operator,
-- never baked into the repository. Tenants come from V1; users are
-- self-service from this point on.

-- ── Regulations ─────────────────────────────────────────────────────
-- An uploaded regulatory document. status drives the UI badge.
CREATE TABLE regulations (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    source_filename TEXT NOT NULL,
    storage_key     TEXT NOT NULL,
    mime_type       TEXT NOT NULL,
    size_bytes      BIGINT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'PARSING'
                    CHECK (status IN ('PARSING', 'EXTRACTED', 'MAPPED', 'BRIEF_READY', 'FAILED')),
    uploaded_by     UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    last_error      TEXT
);
CREATE INDEX idx_regulations_tenant ON regulations(tenant_id);
CREATE INDEX idx_regulations_tenant_uploaded ON regulations(tenant_id, uploaded_at DESC);

-- ── Obligations ─────────────────────────────────────────────────────
-- Discrete claims extracted from a regulation by the Extractor agent.
CREATE TABLE obligations (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    regulation_id   UUID NOT NULL REFERENCES regulations(id) ON DELETE CASCADE,
    summary         TEXT NOT NULL,
    source_excerpt  TEXT NOT NULL,
    page_start      INTEGER,
    page_end        INTEGER,
    order_index     INTEGER NOT NULL,
    gap_status      TEXT NOT NULL DEFAULT 'UNKNOWN'
                    CHECK (gap_status IN ('UNKNOWN', 'MAPPED', 'GAP')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_obligations_regulation ON obligations(regulation_id);
CREATE INDEX idx_obligations_tenant ON obligations(tenant_id);
