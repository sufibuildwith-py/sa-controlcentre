-- Read-only projections of the commercial workbook sheets. Historical rows remain
-- traceable to their staged workbook source and are not posted through Finance.
CREATE TABLE finance_workbook_gst_invoices (
  id UUID PRIMARY KEY,
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id) ON DELETE CASCADE,
  row_id UUID NOT NULL REFERENCES finance_migration_rows(id) ON DELETE CASCADE,
  source_row INTEGER NOT NULL,
  invoice_number VARCHAR(160) NOT NULL,
  invoice_date DATE,
  raw_date VARCHAR(120),
  party_name VARCHAR(500) NOT NULL,
  party_key VARCHAR(220) NOT NULL,
  gstin VARCHAR(80),
  base_amount NUMERIC(19,2) NOT NULL,
  igst NUMERIC(19,2) NOT NULL DEFAULT 0,
  cgst NUMERIC(19,2) NOT NULL DEFAULT 0,
  sgst NUMERIC(19,2) NOT NULL DEFAULT 0,
  total NUMERIC(19,2) NOT NULL,
  tds NUMERIC(19,2) NOT NULL DEFAULT 0,
  cash_received NUMERIC(19,2) NOT NULL DEFAULT 0,
  outstanding NUMERIC(19,2) NOT NULL,
  workbook_outstanding NUMERIC(19,2),
  parity_status VARCHAR(24) NOT NULL CHECK (parity_status IN ('MATCH','MISMATCH','NO_SOURCE_CONTROL')),
  production_row_id UUID,
  party_key_link VARCHAR(220),
  UNIQUE(batch_id,row_id)
);
CREATE INDEX finance_workbook_gst_invoice_search_idx ON finance_workbook_gst_invoices(batch_id,party_key,invoice_date);

CREATE TABLE finance_workbook_gst_settlements (
  id UUID PRIMARY KEY,
  invoice_id UUID NOT NULL REFERENCES finance_workbook_gst_invoices(id) ON DELETE CASCADE,
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id) ON DELETE CASCADE,
  settlement_kind VARCHAR(12) NOT NULL CHECK (settlement_kind IN ('CASH','TDS')),
  payment_slot VARCHAR(40) NOT NULL,
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  owner_fact_id UUID REFERENCES finance_migration_facts(id),
  evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
  UNIQUE(invoice_id,payment_slot)
);
CREATE INDEX finance_workbook_gst_settlement_invoice_idx ON finance_workbook_gst_settlements(invoice_id,settlement_kind);

CREATE TABLE finance_workbook_purchase_records (
  id UUID PRIMARY KEY,
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id) ON DELETE CASCADE,
  row_id UUID NOT NULL REFERENCES finance_migration_rows(id) ON DELETE CASCADE,
  book VARCHAR(12) NOT NULL CHECK (book IN ('LED','SOUND')),
  source_row INTEGER NOT NULL,
  purchase_date DATE,
  raw_date VARCHAR(120),
  description VARCHAR(500) NOT NULL,
  description_key VARCHAR(300) NOT NULL,
  rate NUMERIC(19,2),
  tax NUMERIC(19,2),
  quantity NUMERIC(19,3),
  purchase_amount NUMERIC(19,2) NOT NULL,
  paid NUMERIC(19,2) NOT NULL DEFAULT 0,
  outstanding NUMERIC(19,2) NOT NULL,
  hq_equipment_id UUID,
  hq_link_confidence VARCHAR(24),
  UNIQUE(batch_id,row_id,book)
);
CREATE INDEX finance_workbook_purchase_record_idx ON finance_workbook_purchase_records(batch_id,book,purchase_date,description_key);

CREATE TABLE finance_workbook_purchase_payments (
  id UUID PRIMARY KEY,
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id) ON DELETE CASCADE,
  book VARCHAR(12) NOT NULL CHECK (book IN ('LED','SOUND')),
  row_id UUID NOT NULL REFERENCES finance_migration_rows(id) ON DELETE CASCADE,
  source_row INTEGER NOT NULL,
  payment_date DATE,
  raw_date VARCHAR(120),
  description VARCHAR(500),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  formula_group_start INTEGER,
  formula_group_end INTEGER,
  owner_fact_id UUID REFERENCES finance_migration_facts(id),
  evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
  UNIQUE(batch_id,row_id,book)
);
CREATE INDEX finance_workbook_purchase_payment_idx ON finance_workbook_purchase_payments(batch_id,book,payment_date);

CREATE TABLE finance_workbook_purchase_payment_allocations (
  payment_id UUID NOT NULL REFERENCES finance_workbook_purchase_payments(id) ON DELETE CASCADE,
  purchase_id UUID NOT NULL REFERENCES finance_workbook_purchase_records(id) ON DELETE CASCADE,
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  PRIMARY KEY(payment_id,purchase_id)
);

CREATE TABLE finance_workbook_accessory_references (
  id UUID PRIMARY KEY,
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id) ON DELETE CASCADE,
  row_id UUID NOT NULL REFERENCES finance_migration_rows(id) ON DELETE CASCADE,
  source_row INTEGER NOT NULL,
  source_block VARCHAR(80) NOT NULL,
  reference_kind VARCHAR(24) NOT NULL CHECK (reference_kind IN ('ACCESSORY','EQUIPMENT_REFERENCE')),
  description VARCHAR(500) NOT NULL,
  description_key VARCHAR(300) NOT NULL,
  quantity NUMERIC(19,3) NOT NULL,
  list_price NUMERIC(19,2),
  pre_tax_price NUMERIC(19,2),
  value_amount NUMERIC(19,2),
  disposition VARCHAR(32) NOT NULL CHECK (disposition IN ('ACTIVE_SOURCE','DUPLICATE_SNAPSHOT')),
  duplicate_of_id UUID REFERENCES finance_workbook_accessory_references(id),
  hq_equipment_id UUID,
  hq_link_confidence VARCHAR(24),
  UNIQUE(batch_id,row_id,reference_kind)
);
CREATE INDEX finance_workbook_accessory_reference_idx ON finance_workbook_accessory_references(batch_id,reference_kind,description_key);
