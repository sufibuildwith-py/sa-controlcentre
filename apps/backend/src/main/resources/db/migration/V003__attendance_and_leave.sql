CREATE TABLE attendance_records (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id UUID NOT NULL REFERENCES employees(id),
  attendance_date DATE NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('PRESENT','ABSENT','LATE','HALF_DAY','LEAVE','HOLIDAY')),
  check_in_time TIME,
  check_out_time TIME,
  minutes_late INTEGER NOT NULL DEFAULT 0 CHECK (minutes_late >= 0),
  notes TEXT,
  recorded_by UUID REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT attendance_employee_date_uk UNIQUE(employee_id, attendance_date)
);
CREATE INDEX attendance_date_idx ON attendance_records(attendance_date);
CREATE INDEX attendance_employee_idx ON attendance_records(employee_id, attendance_date DESC);

CREATE TABLE leave_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id UUID NOT NULL REFERENCES employees(id),
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  leave_type VARCHAR(64) NOT NULL,
  reason TEXT NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
  owner_note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at TIMESTAMPTZ,
  CONSTRAINT leave_date_order CHECK (end_date >= start_date)
);
CREATE INDEX leave_status_idx ON leave_requests(status);

