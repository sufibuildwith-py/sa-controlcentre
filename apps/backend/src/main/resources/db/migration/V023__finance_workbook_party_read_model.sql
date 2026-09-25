-- Historical party read model. Source evidence is retained in finance_migration_rows;
-- these projections never post a second Finance transaction.
CREATE TABLE finance_workbook_party_blocks (
  id UUID PRIMARY KEY,
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id) ON DELETE CASCADE,
  sheet_name VARCHAR(160) NOT NULL,
  block_index INTEGER NOT NULL CHECK (block_index > 0),
  start_column VARCHAR(4) NOT NULL,
  end_column VARCHAR(4) NOT NULL,
  source_range VARCHAR(80) NOT NULL,
  party_key VARCHAR(160) NOT NULL,
  raw_name VARCHAR(250) NOT NULL,
  role VARCHAR(32) NOT NULL CHECK (role IN ('CUSTOMER','BUSINESS_PARTY','OTHER','UNKNOWN_LEGACY')),
  disposition VARCHAR(32) NOT NULL CHECK (disposition IN ('ACTIVE_SOURCE','DUPLICATE_SNAPSHOT','REVIEW_REQUIRED')),
  duplicate_of_block_id UUID REFERENCES finance_workbook_party_blocks(id),
  layout JSONB NOT NULL,
  workbook_amount NUMERIC(19,2),
  workbook_payment NUMERIC(19,2),
  workbook_balance NUMERIC(19,2),
  projection_amount NUMERIC(19,2) NOT NULL,
  projection_payment NUMERIC(19,2) NOT NULL,
  projection_balance NUMERIC(19,2) NOT NULL,
  parity_status VARCHAR(24) NOT NULL CHECK (parity_status IN ('MATCH','MISMATCH','NO_SOURCE_CONTROL')),
  UNIQUE(batch_id,sheet_name,start_column)
);
CREATE INDEX finance_workbook_party_blocks_person_idx ON finance_workbook_party_blocks(batch_id,party_key);

CREATE TABLE finance_workbook_party_entries (
  id UUID PRIMARY KEY,
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id) ON DELETE CASCADE,
  block_id UUID NOT NULL REFERENCES finance_workbook_party_blocks(id) ON DELETE CASCADE,
  row_id UUID NOT NULL REFERENCES finance_migration_rows(id) ON DELETE CASCADE,
  source_row INTEGER NOT NULL,
  event_date DATE,
  raw_date VARCHAR(100),
  venue VARCHAR(500),
  service VARCHAR(300),
  rate VARCHAR(100),
  amount NUMERIC(19,2) NOT NULL DEFAULT 0,
  payment NUMERIC(19,2) NOT NULL DEFAULT 0,
  duplicate_of_entry_id UUID REFERENCES finance_workbook_party_entries(id),
  UNIQUE(batch_id,block_id,source_row)
);
CREATE INDEX finance_workbook_party_entries_block_date_idx ON finance_workbook_party_entries(block_id,event_date,source_row);
CREATE INDEX finance_workbook_party_entries_match_idx ON finance_workbook_party_entries(batch_id,event_date,amount,payment);

-- Extension of the existing workbook fact/link boundary: one party source cell
-- may have independently proven production and owner evidence.
CREATE TABLE finance_workbook_party_links (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id) ON DELETE CASCADE,
  entry_id UUID NOT NULL REFERENCES finance_workbook_party_entries(id) ON DELETE CASCADE,
  fact_id UUID NOT NULL REFERENCES finance_migration_facts(id) ON DELETE CASCADE,
  relation_type VARCHAR(32) NOT NULL CHECK (relation_type IN ('PRODUCTION_CHARGE','PRODUCTION_RECEIPT','OWNER_RECEIPT')),
  allocated_amount NUMERIC(19,2) NOT NULL CHECK (allocated_amount > 0),
  confidence VARCHAR(20) NOT NULL CHECK (confidence IN ('EXACT','HIGH_CONFIDENCE')),
  evidence JSONB NOT NULL,
  UNIQUE(entry_id,fact_id,relation_type)
);
CREATE INDEX finance_workbook_party_links_fact_idx ON finance_workbook_party_links(fact_id,relation_type);
