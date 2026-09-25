-- Workbook evidence remains the source of historical owner positions; these indexes only accelerate read projections.
CREATE INDEX finance_migration_owner_history_idx
  ON finance_migration_facts(batch_id, account_code, slot, event_date, row_id)
  WHERE source_role = 'OWNER' AND amount > 0;

CREATE INDEX finance_migration_rows_trim_sheet_idx
  ON finance_migration_rows(batch_id, trim(sheet_name), source_row);
