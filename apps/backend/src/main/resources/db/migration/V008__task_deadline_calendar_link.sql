ALTER TABLE calendar_events ADD COLUMN task_id UUID UNIQUE REFERENCES tasks(id) ON DELETE CASCADE;
CREATE INDEX calendar_events_task_idx ON calendar_events(task_id) WHERE task_id IS NOT NULL;
