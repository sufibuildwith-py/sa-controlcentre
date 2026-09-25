ALTER TABLE finance_migration_identity_links
  ADD COLUMN relation_type VARCHAR(24) NOT NULL DEFAULT 'DUPLICATE_SOURCE'
  CHECK (relation_type IN ('DUPLICATE_SOURCE','OWNER_TRANSFER'));
ALTER TABLE finance_migration_facts ADD COLUMN resolved_event_type VARCHAR(48);
