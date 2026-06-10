-- Sprint 2 schema: internal policy catalog, embedded chunks, and the
-- obligation→chunk match table the Mapper agent will write into.
--
-- pgvector is already enabled in V1. Cosine similarity (vector_cosine_ops)
-- fits semantic search on Cohere embeddings, which are L2-normalised so
-- cosine ≈ dot product.

-- ── Policies ────────────────────────────────────────────────────────
-- An uploaded internal policy. Three-state machine: PARSING while the
-- async worker is chunking + embedding, INDEXED once chunks are stored,
-- FAILED if any step throws.
CREATE TABLE policies (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    source_filename TEXT NOT NULL,
    storage_key     TEXT NOT NULL,
    mime_type       TEXT NOT NULL,
    size_bytes      BIGINT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'PARSING'
                    CHECK (status IN ('PARSING', 'INDEXED', 'FAILED')),
    uploaded_by     UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    last_error      TEXT
);
CREATE INDEX idx_policies_tenant ON policies(tenant_id);
CREATE INDEX idx_policies_tenant_uploaded ON policies(tenant_id, uploaded_at DESC);

-- ── Policy chunks ───────────────────────────────────────────────────
-- One text window from a policy + its 1024-d Cohere embedding. The
-- embedding column is read/written via native SQL (JdbcTemplate or
-- @Query nativeQuery) — JPA owns the relational columns only.
--
-- HNSW gives sub-linear ANN search with reasonable recall; we use it
-- over IVFFlat because HNSW doesn't need a separate training step and
-- the catalogs here will be small enough that build time is fine.
CREATE TABLE policy_chunks (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    policy_id       UUID NOT NULL REFERENCES policies(id) ON DELETE CASCADE,
    chunk_index     INTEGER NOT NULL,
    text            TEXT NOT NULL,
    page_start      INTEGER,
    page_end        INTEGER,
    embedding       VECTOR(1024) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_policy_chunks_policy ON policy_chunks(policy_id);
CREATE INDEX idx_policy_chunks_tenant ON policy_chunks(tenant_id);
CREATE INDEX idx_policy_chunks_embedding_hnsw
    ON policy_chunks USING hnsw (embedding vector_cosine_ops);

-- ── Obligation→Policy matches ───────────────────────────────────────
-- Populated by the Mapper agent in Chunk F. One row per (obligation,
-- chunk) pair the Mapper considered relevant, with confidence and the
-- judge's reasoning. The Gap derivation uses presence/absence of rows
-- above the confidence threshold per obligation.
CREATE TABLE obligation_policy_matches (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    obligation_id       UUID NOT NULL REFERENCES obligations(id) ON DELETE CASCADE,
    policy_chunk_id     UUID NOT NULL REFERENCES policy_chunks(id) ON DELETE CASCADE,
    confidence          DOUBLE PRECISION NOT NULL,
    reasoning           TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_match_obligation_chunk UNIQUE (obligation_id, policy_chunk_id)
);
CREATE INDEX idx_matches_obligation ON obligation_policy_matches(obligation_id);
CREATE INDEX idx_matches_tenant ON obligation_policy_matches(tenant_id);
