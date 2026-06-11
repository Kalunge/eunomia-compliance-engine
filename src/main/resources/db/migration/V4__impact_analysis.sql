-- Sprint 2 Chunk H: product/service catalogue + obligation→product
-- impact links, both tenant-scoped.
--
-- For the capstone the catalogue is seeded via this migration against
-- the demo-bank tenant from V1. Real deployments would add a CRUD
-- endpoint per tenant.

-- ── Catalogue table ────────────────────────────────────────────────
CREATE TABLE product_services (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_services_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_product_services_tenant ON product_services(tenant_id);

-- ── Obligation→Product impact link ─────────────────────────────────
-- One row per (obligation, product) pair the ImpactAnalyzer judged
-- the obligation to affect. Idempotent re-runs delete prior rows
-- (see ImpactWorkflow.tagOne).
CREATE TABLE obligation_impacts (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    obligation_id       UUID NOT NULL REFERENCES obligations(id) ON DELETE CASCADE,
    product_service_id  UUID NOT NULL REFERENCES product_services(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_impact_obligation_product UNIQUE (obligation_id, product_service_id)
);
CREATE INDEX idx_impacts_obligation ON obligation_impacts(obligation_id);
CREATE INDEX idx_impacts_tenant ON obligation_impacts(tenant_id);

-- ── Seed demo-bank catalogue ───────────────────────────────────────
-- Stable UUIDs so the seed is idempotent across deploys and tests.
INSERT INTO product_services (id, tenant_id, name, description) VALUES
    ('11111111-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
     'Agent Banking',
     'Third-party agent outlets providing cash-in/cash-out and account services'),
    ('11111111-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001',
     'Mobile Banking',
     'Smartphone app + USSD channel for retail customers'),
    ('11111111-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001',
     'Retail Deposits',
     'Current, savings, and fixed deposit accounts for individuals'),
    ('11111111-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001',
     'Wholesale Treasury',
     'Money market, FX trading, repo, and interbank operations'),
    ('11111111-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001',
     'Corporate Banking',
     'Lending and transaction services for large enterprises'),
    ('11111111-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000001',
     'SME Lending',
     'Working-capital and term loans for small and medium businesses'),
    ('11111111-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000001',
     'Bancassurance',
     'Insurance products distributed through the bank''s channels'),
    ('11111111-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000001',
     'Foreign Exchange',
     'Retail and corporate FX dealing, remittance corridors'),
    ('11111111-0000-0000-0000-000000000009', '00000000-0000-0000-0000-000000000001',
     'Custodial Services',
     'Securities custody and settlement for institutional clients'),
    ('11111111-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-000000000001',
     'Wealth Management',
     'Investment advisory and portfolio management for HNW clients'),
    ('11111111-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-000000000001',
     'Card Issuing',
     'Debit, credit, and prepaid card products'),
    ('11111111-0000-0000-0000-00000000000c', '00000000-0000-0000-0000-000000000001',
     'Trade Finance',
     'Letters of credit, guarantees, and supply-chain finance')
ON CONFLICT (tenant_id, name) DO NOTHING;
