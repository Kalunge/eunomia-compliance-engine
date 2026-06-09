-- Baseline migration. The schema fills out across Sprint 1 (auth + documents)
-- and Sprint 2 (policies + obligations + mappings).
--
-- This file establishes the extension we depend on (pgvector) and a minimal
-- tenants table so the application can boot against an empty database.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS tenants (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(64)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Seed demo tenant so /health can report a real DB read on an empty deploy.
INSERT INTO tenants (id, name, slug)
VALUES ('00000000-0000-0000-0000-000000000001', 'Demo Bank', 'demo-bank')
ON CONFLICT (slug) DO NOTHING;
