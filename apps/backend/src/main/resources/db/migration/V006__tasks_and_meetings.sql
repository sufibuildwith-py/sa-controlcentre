CREATE TABLE meetings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(180) NOT NULL,
  description TEXT,
  agenda TEXT,
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  location VARCHAR(250),
  status VARCHAR(24) NOT NULL CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT meeting_time_order CHECK (ends_at > starts_at)
);
CREATE INDEX meetings_starts_idx ON meetings(starts_at);

ALTER TABLE calendar_events ADD COLUMN meeting_id UUID UNIQUE REFERENCES meetings(id) ON DELETE CASCADE;

CREATE TABLE meeting_attendees (
  meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
  employee_id UUID NOT NULL REFERENCES employees(id),
  response VARCHAR(24) NOT NULL CHECK (response IN ('PENDING','ACCEPTED','DECLINED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(meeting_id, employee_id)
);

CREATE TABLE meeting_notes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tasks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  production_id UUID REFERENCES productions(id) ON DELETE SET NULL,
  meeting_origin_id UUID REFERENCES meetings(id) ON DELETE SET NULL,
  title VARCHAR(180) NOT NULL,
  description TEXT,
  assigned_employee_id UUID REFERENCES employees(id),
  created_by UUID REFERENCES users(id),
  status VARCHAR(24) NOT NULL CHECK (status IN ('TODO','IN_PROGRESS','BLOCKED','DONE','CANCELLED')),
  priority VARCHAR(16) NOT NULL CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
  start_date DATE,
  due_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  progress_percent INTEGER NOT NULL DEFAULT 0 CHECK (progress_percent BETWEEN 0 AND 100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX tasks_assignee_idx ON tasks(assigned_employee_id);
CREATE INDEX tasks_due_idx ON tasks(due_at);
CREATE INDEX tasks_status_idx ON tasks(status);
CREATE INDEX tasks_production_idx ON tasks(production_id);

CREATE TABLE task_updates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  author_id UUID REFERENCES users(id),
  progress_percent INTEGER NOT NULL CHECK (progress_percent BETWEEN 0 AND 100),
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX task_updates_task_idx ON task_updates(task_id, created_at DESC);
