-- Sprint 3 Chunk N: persistent workflow event log for SSE streaming.
--
-- Each event captures one meaningful saga moment ("Extractor parsed",
-- "Mapper matched obligation 3 of 9 to policy X", "Brief ready"). The
-- table is the source of truth for replay — a client that reconnects
-- mid-saga reads from here before subscribing to live events.
--
-- sequence_no is per-regulation and assigned at the application layer
-- so a SELECT max(seq)+1 race is bounded by single-regulation
-- saga concurrency (and the UNIQUE constraint catches anything that
-- slips through).

CREATE TABLE workflow_events (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    regulation_id   UUID NOT NULL REFERENCES regulations(id) ON DELETE CASCADE,
    sequence_no     BIGINT NOT NULL,
    event_type      TEXT NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workflow_events_seq UNIQUE (regulation_id, sequence_no)
);
CREATE INDEX idx_workflow_events_regulation_seq
    ON workflow_events(regulation_id, sequence_no);
CREATE INDEX idx_workflow_events_tenant
    ON workflow_events(tenant_id);
