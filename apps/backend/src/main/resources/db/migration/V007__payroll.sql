CREATE TABLE payroll_periods (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  year INTEGER NOT NULL CHECK (year BETWEEN 2000 AND 2200),
  month INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
  status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT','CALCULATED','APPROVED','PAID','LOCKED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  calculated_at TIMESTAMPTZ,
  approved_at TIMESTAMPTZ,
  paid_at TIMESTAMPTZ,
  locked_at TIMESTAMPTZ,
  CONSTRAINT payroll_period_uk UNIQUE(year, month)
);
CREATE INDEX payroll_year_month_idx ON payroll_periods(year DESC, month DESC);

CREATE TABLE payroll_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payroll_period_id UUID NOT NULL REFERENCES payroll_periods(id) ON DELETE CASCADE,
  employee_id UUID NOT NULL REFERENCES employees(id),
  employee_name_snapshot VARCHAR(160) NOT NULL,
  salary_currency CHAR(3) NOT NULL,
  base_salary_minor BIGINT NOT NULL CHECK (base_salary_minor >= 0),
  attendance_deduction_minor BIGINT NOT NULL DEFAULT 0 CHECK (attendance_deduction_minor >= 0),
  overtime_minor BIGINT NOT NULL DEFAULT 0,
  bonus_minor BIGINT NOT NULL DEFAULT 0,
  advance_deduction_minor BIGINT NOT NULL DEFAULT 0,
  manual_adjustment_minor BIGINT NOT NULL DEFAULT 0,
  net_salary_minor BIGINT NOT NULL,
  payment_status VARCHAR(16) NOT NULL CHECK (payment_status IN ('PENDING','PAID')),
  paid_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT payroll_item_uk UNIQUE(payroll_period_id, employee_id)
);
CREATE INDEX payroll_items_employee_idx ON payroll_items(employee_id, payroll_period_id);

CREATE TABLE payroll_adjustments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payroll_item_id UUID NOT NULL REFERENCES payroll_items(id) ON DELETE CASCADE,
  type VARCHAR(24) NOT NULL CHECK (type IN ('BONUS','DEDUCTION','OVERTIME','ADVANCE','CORRECTION','OTHER')),
  amount_minor BIGINT NOT NULL CHECK (amount_minor <> 0),
  reason VARCHAR(500) NOT NULL,
  created_by UUID REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX payroll_adjustments_item_idx ON payroll_adjustments(payroll_item_id, created_at);
