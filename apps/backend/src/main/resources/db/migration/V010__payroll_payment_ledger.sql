ALTER TABLE payroll_items DROP CONSTRAINT IF EXISTS payroll_items_payment_status_check;
UPDATE payroll_items SET payment_status = 'UNPAID' WHERE payment_status = 'PENDING';
ALTER TABLE payroll_items ADD CONSTRAINT payroll_items_payment_status_check
  CHECK (payment_status IN ('UNPAID','PARTIALLY_PAID','PAID'));

CREATE TABLE payroll_payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payroll_item_id UUID NOT NULL REFERENCES payroll_items(id),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  paid_at TIMESTAMPTZ NOT NULL,
  payment_method VARCHAR(24) NOT NULL CHECK (payment_method IN ('CASH','BANK_TRANSFER','UPI','CHEQUE','OTHER')),
  reference VARCHAR(160),
  note VARCHAR(500),
  recorded_by UUID REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX payroll_payments_item_idx ON payroll_payments(payroll_item_id);
CREATE INDEX payroll_payments_paid_at_idx ON payroll_payments(paid_at);

INSERT INTO payroll_payments(payroll_item_id,amount_minor,paid_at,payment_method,reference,note,created_at)
SELECT i.id,i.net_salary_minor,coalesce(i.paid_at,p.paid_at,i.created_at),'OTHER','LEGACY-MIGRATION',
       'Migrated from the V1 paid payroll record.',coalesce(i.paid_at,p.paid_at,i.created_at)
FROM payroll_items i
JOIN payroll_periods p ON p.id=i.payroll_period_id
WHERE i.payment_status='PAID' AND i.net_salary_minor>0;
