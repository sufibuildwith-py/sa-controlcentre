ALTER TABLE productions ALTER COLUMN start_time DROP NOT NULL;
ALTER TABLE productions ALTER COLUMN end_time DROP NOT NULL;
ALTER TABLE productions DROP CONSTRAINT IF EXISTS production_time_order;
ALTER TABLE productions ADD CONSTRAINT production_time_order CHECK (
  (start_time IS NULL AND end_time IS NULL) OR
  (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
);
