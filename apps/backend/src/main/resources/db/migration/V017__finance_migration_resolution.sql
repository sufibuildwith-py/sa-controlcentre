-- Migration evidence is separate from posted Finance. Only owner accounts AZ-2/AK-2
-- exist in the normal ledger; unresolved historical cash stays here for review.
CREATE TABLE finance_migration_facts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id) ON DELETE CASCADE,
  row_id UUID NOT NULL REFERENCES finance_migration_rows(id) ON DELETE CASCADE,
  slot VARCHAR(32) NOT NULL,
  source_range VARCHAR(80) NOT NULL,
  event_type VARCHAR(48) NOT NULL,
  amount NUMERIC(19,2),
  event_date DATE,
  raw_date VARCHAR(100),
  raw_name VARCHAR(250),
  normalized_name VARCHAR(250),
  direction VARCHAR(8) NOT NULL CHECK (direction IN ('IN','OUT','NONE')),
  source_role VARCHAR(16) NOT NULL CHECK (source_role IN ('OWNER','DOMAIN','CONTROL')),
  account_code VARCHAR(8) CHECK (account_code IN ('AZ-2','AK-2')),
  classification VARCHAR(32) NOT NULL CHECK (classification IN
    ('EXACT','HIGH_CONFIDENCE','REVIEW_REQUIRED','DISTINCT','NON_CASH','IGNORED_NON_FINANCIAL','LEGACY_UNALLOCATED')),
  issue_code VARCHAR(64),
  evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
  canonical_transaction_id UUID REFERENCES finance_transactions(id),
  UNIQUE(batch_id,row_id,slot,event_type)
);
CREATE INDEX finance_migration_facts_batch_class_idx ON finance_migration_facts(batch_id,classification);
CREATE INDEX finance_migration_facts_match_idx ON finance_migration_facts(batch_id,direction,amount,event_date,normalized_name);
CREATE INDEX finance_migration_facts_row_idx ON finance_migration_facts(row_id);

CREATE TABLE finance_migration_links (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id) ON DELETE CASCADE,
  domain_fact_id UUID NOT NULL REFERENCES finance_migration_facts(id) ON DELETE CASCADE,
  owner_fact_id UUID NOT NULL REFERENCES finance_migration_facts(id) ON DELETE CASCADE,
  allocated_amount NUMERIC(19,2) NOT NULL CHECK (allocated_amount > 0),
  confidence VARCHAR(20) NOT NULL CHECK (confidence IN ('EXACT','HIGH_CONFIDENCE','MANUAL')),
  evidence JSONB NOT NULL,
  UNIQUE(domain_fact_id,owner_fact_id),
  CHECK (domain_fact_id <> owner_fact_id)
);
CREATE INDEX finance_migration_links_owner_idx ON finance_migration_links(owner_fact_id);

CREATE TABLE finance_migration_aliases (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  raw_name VARCHAR(250) NOT NULL,
  normalized_identity VARCHAR(250) NOT NULL,
  entity_type VARCHAR(40) NOT NULL,
  confidence VARCHAR(20) NOT NULL CHECK (confidence IN ('EXACT','HIGH_CONFIDENCE','MANUAL')),
  source VARCHAR(160) NOT NULL,
  reviewed BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE(raw_name,entity_type)
);

-- Decisions survive demo Finance resets and are replayed against the same source hash.
CREATE TABLE finance_migration_overrides (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workbook_sha256 CHAR(64) NOT NULL,
  sheet_name VARCHAR(160) NOT NULL,
  source_row INTEGER NOT NULL CHECK (source_row > 0),
  slot VARCHAR(32) NOT NULL,
  action VARCHAR(40) NOT NULL CHECK (action IN
    ('ASSIGN_AZ','ASSIGN_AK','MARK_NON_CASH','LINK_EXISTING','MARK_DISTINCT','MAP_ENTITY','IGNORE_NON_FINANCIAL','LEGACY_ADJUSTMENT')),
  target VARCHAR(250),
  reason VARCHAR(500) NOT NULL,
  actor VARCHAR(180) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(workbook_sha256,sheet_name,source_row,slot)
);
CREATE TABLE finance_migration_override_audit (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  override_id UUID NOT NULL REFERENCES finance_migration_overrides(id),
  action VARCHAR(40) NOT NULL,
  target VARCHAR(250),
  reason VARCHAR(500) NOT NULL,
  actor VARCHAR(180) NOT NULL,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
