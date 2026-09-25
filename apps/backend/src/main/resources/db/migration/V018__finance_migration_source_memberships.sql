-- Cross-domain identities are evidence links, not additional financial postings.
ALTER TABLE finance_migration_facts
  ADD COLUMN duplicate_of_fact_id UUID REFERENCES finance_migration_facts(id);
CREATE INDEX finance_migration_facts_duplicate_idx ON finance_migration_facts(duplicate_of_fact_id);

CREATE TABLE finance_migration_identity_links (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id) ON DELETE CASCADE,
  primary_fact_id UUID NOT NULL REFERENCES finance_migration_facts(id) ON DELETE CASCADE,
  duplicate_fact_id UUID NOT NULL UNIQUE REFERENCES finance_migration_facts(id) ON DELETE CASCADE,
  confidence VARCHAR(20) NOT NULL CHECK (confidence IN ('EXACT','HIGH_CONFIDENCE','MANUAL')),
  evidence JSONB NOT NULL,
  CHECK (primary_fact_id <> duplicate_fact_id)
);
CREATE INDEX finance_migration_identity_primary_idx ON finance_migration_identity_links(primary_fact_id);
