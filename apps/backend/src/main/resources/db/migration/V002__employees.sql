CREATE TABLE employees (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_code VARCHAR(32) NOT NULL UNIQUE,
  first_name VARCHAR(80) NOT NULL,
  last_name VARCHAR(80),
  display_name VARCHAR(160) NOT NULL,
  phone VARCHAR(32) NOT NULL,
  whatsapp_phone VARCHAR(32),
  email VARCHAR(254),
  role_title VARCHAR(120) NOT NULL,
  department VARCHAR(100) NOT NULL,
  employment_type VARCHAR(32) NOT NULL,
  joining_date DATE NOT NULL,
  base_salary_minor BIGINT NOT NULL CHECK (base_salary_minor >= 0),
  salary_currency CHAR(3) NOT NULL DEFAULT 'INR',
  status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE','ON_LEAVE','INACTIVE')),
  profile_photo_url VARCHAR(1000),
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX employees_status_idx ON employees(status);
CREATE INDEX employees_display_name_idx ON employees(lower(display_name));

