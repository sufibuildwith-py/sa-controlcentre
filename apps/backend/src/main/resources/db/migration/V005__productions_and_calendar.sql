CREATE TABLE productions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(180) NOT NULL,
  client_name VARCHAR(180) NOT NULL,
  description TEXT,
  event_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  venue_name VARCHAR(180) NOT NULL,
  venue_address VARCHAR(500),
  status VARCHAR(32) NOT NULL CHECK (status IN ('DRAFT','PLANNING','PRE_PRODUCTION','PRODUCTION','POST_PRODUCTION','REVIEW','DELIVERED','CANCELLED')),
  priority VARCHAR(16) NOT NULL CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
  progress_percent INTEGER NOT NULL DEFAULT 0 CHECK (progress_percent BETWEEN 0 AND 100),
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT production_time_order CHECK (end_time > start_time)
);
CREATE INDEX productions_status_idx ON productions(status);
CREATE INDEX productions_event_date_idx ON productions(event_date);
CREATE INDEX productions_priority_idx ON productions(priority);

CREATE TABLE calendar_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type VARCHAR(24) NOT NULL CHECK (type IN ('PRODUCTION','SHOOT','MEETING','DEADLINE','INTERNAL','REMINDER')),
  title VARCHAR(180) NOT NULL,
  description TEXT,
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  location_name VARCHAR(180),
  location_address VARCHAR(500),
  production_id UUID UNIQUE REFERENCES productions(id) ON DELETE CASCADE,
  status VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED','CANCELLED','COMPLETED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT calendar_time_order CHECK (ends_at > starts_at)
);
CREATE INDEX calendar_starts_idx ON calendar_events(starts_at);
CREATE INDEX calendar_ends_idx ON calendar_events(ends_at);

CREATE TABLE production_members (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  production_id UUID NOT NULL REFERENCES productions(id) ON DELETE CASCADE,
  employee_id UUID NOT NULL REFERENCES employees(id),
  production_role VARCHAR(120) NOT NULL,
  attendance_required BOOLEAN NOT NULL DEFAULT true,
  assignment_status VARCHAR(24) NOT NULL CHECK (assignment_status IN ('PENDING','CONFIRMED','DECLINED')),
  conflict_overridden BOOLEAN NOT NULL DEFAULT false,
  override_reason VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT production_member_uk UNIQUE(production_id, employee_id)
);
CREATE INDEX production_members_employee_idx ON production_members(employee_id);

CREATE TABLE event_attendees (
  event_id UUID NOT NULL REFERENCES calendar_events(id) ON DELETE CASCADE,
  employee_id UUID NOT NULL REFERENCES employees(id),
  response VARCHAR(24) NOT NULL CHECK (response IN ('PENDING','ACCEPTED','DECLINED')),
  notified_at TIMESTAMPTZ,
  acknowledged_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(event_id, employee_id)
);
CREATE INDEX event_attendees_employee_idx ON event_attendees(employee_id, event_id);
