-- Read-only historical staff evidence. Never posts into the Finance journal.
CREATE TABLE finance_workbook_employee_facts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id) ON DELETE CASCADE,
  row_id UUID NOT NULL REFERENCES finance_migration_rows(id) ON DELETE CASCADE,
  employee_key VARCHAR(80) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  kind VARCHAR(16) NOT NULL CHECK (kind IN ('EARNING','PAYMENT')),
  source_column VARCHAR(4) NOT NULL,
  source_heading VARCHAR(120),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  event_date DATE,
  identity_status VARCHAR(32) NOT NULL CHECK (identity_status IN ('FORMULA_PAIRED','IDENTITY_REVIEW')),
  UNIQUE (batch_id,row_id,source_column)
);
CREATE INDEX finance_workbook_employee_facts_person_date_idx
  ON finance_workbook_employee_facts(batch_id,employee_key,event_date,row_id);
CREATE INDEX finance_workbook_employee_facts_date_idx
  ON finance_workbook_employee_facts(batch_id,event_date);
