ALTER TABLE payroll_payments ADD COLUMN request_id UUID;
UPDATE payroll_payments SET request_id = gen_random_uuid() WHERE request_id IS NULL;
ALTER TABLE payroll_payments ALTER COLUMN request_id SET NOT NULL;
ALTER TABLE payroll_payments ADD CONSTRAINT payroll_payments_request_id_uk UNIQUE(request_id);
