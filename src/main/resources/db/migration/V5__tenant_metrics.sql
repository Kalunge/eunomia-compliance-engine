-- Sprint 3 Chunk J: denormalised per-tenant metrics for the dashboard.
--
-- A read-optimised projection of three counts that are otherwise
-- computed on the fly from regulations / obligations /
-- obligation_impacts. Kept fresh by:
--   1. Synchronous recompute hooks in service methods after writes
--      that could change the counts (MappingWorkflow.process()
--      being the main one).
--   2. A 15-minute @Scheduled self-heal job (TenantMetricsScheduler)
--      that catches any case where the hook is missed.
--
-- The authoritative state still lives in the underlying tables —
-- this is an explicit derived view, not a cache.

CREATE TABLE tenant_metrics (
    tenant_id              UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    regulations_processed  BIGINT NOT NULL DEFAULT 0,
    open_gaps              BIGINT NOT NULL DEFAULT 0,
    impacted_product_count BIGINT NOT NULL DEFAULT 0,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed a row for the demo-bank tenant so the dashboard reads return
-- something meaningful immediately after deploy. Zeros are correct
-- pre-upload; the scheduler / hooks will populate real values.
INSERT INTO tenant_metrics (tenant_id) VALUES
    ('00000000-0000-0000-0000-000000000001')
ON CONFLICT (tenant_id) DO NOTHING;
