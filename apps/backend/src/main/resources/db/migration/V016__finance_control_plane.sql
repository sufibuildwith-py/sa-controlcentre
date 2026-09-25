CREATE TABLE finance_accounts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(16) NOT NULL UNIQUE,
  display_name VARCHAR(100) NOT NULL,
  account_type VARCHAR(32) NOT NULL CHECK (account_type IN ('OWNER_CURRENT')),
  currency CHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO finance_accounts(code, display_name, account_type) VALUES
  ('AZ-2','Azeem','OWNER_CURRENT'),('AK-2','Akash','OWNER_CURRENT');

CREATE TABLE finance_account_positions (
  account_id UUID PRIMARY KEY REFERENCES finance_accounts(id),
  position NUMERIC(19,2) NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO finance_account_positions(account_id) SELECT id FROM finance_accounts;

CREATE TABLE finance_profit_split_rules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  effective_from DATE NOT NULL,
  effective_to DATE,
  azeem_percent NUMERIC(5,2) NOT NULL,
  akash_percent NUMERIC(5,2) NOT NULL,
  scope VARCHAR(32) NOT NULL DEFAULT 'DEFAULT_PROFIT',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (azeem_percent >= 0 AND akash_percent >= 0 AND azeem_percent + akash_percent = 100.00),
  CHECK (effective_to IS NULL OR effective_to >= effective_from),
  UNIQUE(scope, effective_from)
);
INSERT INTO finance_profit_split_rules(effective_from, azeem_percent, akash_percent)
VALUES ('2026-01-01',65.00,35.00);

CREATE TABLE finance_expense_categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(48) NOT NULL UNIQUE,
  display_name VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true
);
INSERT INTO finance_expense_categories(code, display_name) VALUES
  ('FOOD','Food'),('TRANSPORT','Transport'),('PETROL','Petrol'),('VEHICLE','Vehicle'),
  ('REPAIR','Repair'),('EMI','EMI'),('EQUIPMENT','Equipment'),('STAFF','Staff'),
  ('RENTAL','Rental'),('INTERNET','Internet'),('OFFICE','Office'),('OTHER','Other');

CREATE TABLE finance_counterparties (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  display_name VARCHAR(180) NOT NULL,
  legal_name VARCHAR(180),
  role VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' CHECK (role IN ('CUSTOMER','VENDOR','RENTAL_PROVIDER','SUBCONTRACTOR','OTHER','UNKNOWN','MIXED')),
  gstin VARCHAR(32),
  notes TEXT,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX finance_counterparties_name_idx ON finance_counterparties(lower(display_name));

CREATE TABLE finance_production_profiles (
  production_id UUID PRIMARY KEY REFERENCES productions(id),
  contracted_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (contracted_amount >= 0),
  source_transaction_id UUID,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE finance_employee_profiles (
  employee_id UUID PRIMARY KEY REFERENCES employees(id),
  compensation_mode VARCHAR(24) NOT NULL CHECK (compensation_mode IN ('WORK_BASED','FIXED_MONTHLY')),
  monthly_salary NUMERIC(19,2) CHECK (monthly_salary >= 0),
  effective_from DATE NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (compensation_mode <> 'FIXED_MONTHLY' OR monthly_salary IS NOT NULL)
);

CREATE TABLE finance_employee_obligations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id UUID NOT NULL REFERENCES employees(id),
  production_id UUID REFERENCES productions(id),
  payroll_item_id UUID UNIQUE REFERENCES payroll_items(id),
  obligation_type VARCHAR(24) NOT NULL CHECK (obligation_type IN ('WORK_EARNING','FIXED_SALARY','LEGACY')),
  effective_date DATE NOT NULL,
  gross_amount NUMERIC(19,2) NOT NULL CHECK (gross_amount >= 0),
  approved_deductions NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (approved_deductions >= 0),
  adjustment NUMERIC(19,2) NOT NULL DEFAULT 0,
  net_amount NUMERIC(19,2) GENERATED ALWAYS AS (gross_amount - approved_deductions + adjustment) STORED,
  description VARCHAR(500) NOT NULL,
  source_transaction_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (gross_amount - approved_deductions + adjustment >= 0)
);
CREATE INDEX finance_employee_obligations_employee_idx ON finance_employee_obligations(employee_id,effective_date);

CREATE TABLE finance_counterparty_charges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  counterparty_id UUID NOT NULL REFERENCES finance_counterparties(id),
  production_id UUID REFERENCES productions(id),
  effective_date DATE NOT NULL,
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  description VARCHAR(500) NOT NULL,
  source_transaction_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX finance_counterparty_charges_party_idx ON finance_counterparty_charges(counterparty_id,effective_date);

CREATE TABLE finance_invoices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  invoice_number VARCHAR(100) NOT NULL UNIQUE,
  financial_year VARCHAR(16) NOT NULL,
  invoice_date DATE NOT NULL,
  counterparty_id UUID NOT NULL REFERENCES finance_counterparties(id),
  production_id UUID REFERENCES productions(id),
  gstin VARCHAR(32),
  tax_mode VARCHAR(16) NOT NULL CHECK (tax_mode IN ('CGST_SGST','IGST','NONE','CUSTOM')),
  base_amount NUMERIC(19,2) NOT NULL CHECK (base_amount >= 0),
  cgst_rate NUMERIC(7,4) NOT NULL DEFAULT 0 CHECK (cgst_rate >= 0),
  sgst_rate NUMERIC(7,4) NOT NULL DEFAULT 0 CHECK (sgst_rate >= 0),
  igst_rate NUMERIC(7,4) NOT NULL DEFAULT 0 CHECK (igst_rate >= 0),
  cgst_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (cgst_amount >= 0),
  sgst_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (sgst_amount >= 0),
  igst_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (igst_amount >= 0),
  invoice_total NUMERIC(19,2) NOT NULL CHECK (invoice_total >= 0),
  tds_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (tds_amount >= 0),
  source_transaction_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (invoice_total = base_amount + cgst_amount + sgst_amount + igst_amount),
  CHECK (tds_amount <= invoice_total)
);
CREATE INDEX finance_invoices_party_idx ON finance_invoices(counterparty_id,invoice_date);

CREATE TABLE finance_equipment_purchases (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  counterparty_id UUID REFERENCES finance_counterparties(id),
  purchase_date DATE NOT NULL,
  reference VARCHAR(120),
  description VARCHAR(500) NOT NULL,
  subtotal NUMERIC(19,2) NOT NULL CHECK (subtotal >= 0),
  tax NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (tax >= 0),
  total NUMERIC(19,2) NOT NULL CHECK (total > 0),
  source_transaction_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (total = subtotal + tax)
);
CREATE TABLE finance_equipment_purchase_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  purchase_id UUID NOT NULL REFERENCES finance_equipment_purchases(id),
  headquarters_equipment_id UUID REFERENCES hq_equipment(id),
  description VARCHAR(500) NOT NULL,
  quantity NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
  unit_rate NUMERIC(19,2) NOT NULL CHECK (unit_rate >= 0),
  tax NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (tax >= 0),
  amount NUMERIC(19,2) NOT NULL CHECK (amount >= 0)
);

CREATE TABLE finance_transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  transaction_no BIGINT GENERATED BY DEFAULT AS IDENTITY UNIQUE,
  transaction_type VARCHAR(40) NOT NULL CHECK (transaction_type IN (
    'PRODUCTION_CONTRACT','PRODUCTION_RECEIPT','PRODUCTION_EXPENSE','EMPLOYEE_EARNING',
    'MONTHLY_SALARY_ACCRUAL','EMPLOYEE_PAYMENT','COUNTERPARTY_CHARGE','COUNTERPARTY_RECEIPT',
    'INVOICE_ISSUED','INVOICE_PAYMENT','EQUIPMENT_PURCHASE','EQUIPMENT_PAYMENT',
    'GENERAL_EXPENSE','OWNER_CREDIT','OWNER_DEBIT','OWNER_TRANSFER','OPENING_BALANCE','ADJUSTMENT','REVERSAL')),
  status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','POSTED','REVERSED')),
  effective_date DATE NOT NULL,
  description VARCHAR(500) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  production_id UUID REFERENCES productions(id),
  employee_id UUID REFERENCES employees(id),
  counterparty_id UUID REFERENCES finance_counterparties(id),
  invoice_id UUID REFERENCES finance_invoices(id),
  equipment_purchase_id UUID REFERENCES finance_equipment_purchases(id),
  payer_account_id UUID REFERENCES finance_accounts(id),
  receiver_account_id UUID REFERENCES finance_accounts(id),
  expense_category_id UUID REFERENCES finance_expense_categories(id),
  source VARCHAR(24) NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL','MIGRATED','SYSTEM')),
  legacy_type VARCHAR(32),
  idempotency_key UUID NOT NULL UNIQUE,
  payload_hash CHAR(64) NOT NULL,
  reversal_of UUID UNIQUE REFERENCES finance_transactions(id),
  migration_batch_id UUID,
  legacy_source_json JSONB,
  created_by VARCHAR(180) NOT NULL,
  posted_by VARCHAR(180),
  posted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (status = 'DRAFT' OR posted_at IS NOT NULL),
  CHECK (transaction_type <> 'OWNER_TRANSFER' OR (payer_account_id IS NOT NULL AND receiver_account_id IS NOT NULL AND payer_account_id <> receiver_account_id)),
  CHECK (transaction_type NOT IN ('PRODUCTION_RECEIPT','COUNTERPARTY_RECEIPT','INVOICE_PAYMENT','OWNER_CREDIT') OR receiver_account_id IS NOT NULL),
  CHECK (transaction_type NOT IN ('PRODUCTION_EXPENSE','EMPLOYEE_PAYMENT','EQUIPMENT_PAYMENT','GENERAL_EXPENSE','OWNER_DEBIT') OR payer_account_id IS NOT NULL)
);
CREATE INDEX finance_transactions_date_idx ON finance_transactions(effective_date DESC,id);
CREATE INDEX finance_transactions_type_date_idx ON finance_transactions(transaction_type,effective_date DESC);
CREATE INDEX finance_transactions_production_idx ON finance_transactions(production_id,effective_date DESC);
CREATE INDEX finance_transactions_employee_idx ON finance_transactions(employee_id,effective_date DESC);
CREATE INDEX finance_transactions_party_idx ON finance_transactions(counterparty_id,effective_date DESC);
CREATE INDEX finance_transactions_invoice_idx ON finance_transactions(invoice_id);
CREATE INDEX finance_transactions_purchase_idx ON finance_transactions(equipment_purchase_id);

CREATE TABLE finance_journal_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  transaction_id UUID NOT NULL UNIQUE REFERENCES finance_transactions(id),
  posted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE finance_journal_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  entry_id UUID NOT NULL REFERENCES finance_journal_entries(id),
  ledger_code VARCHAR(40) NOT NULL,
  debit NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (debit >= 0),
  credit NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (credit >= 0),
  owner_account_id UUID REFERENCES finance_accounts(id),
  production_id UUID REFERENCES productions(id),
  employee_id UUID REFERENCES employees(id),
  counterparty_id UUID REFERENCES finance_counterparties(id),
  invoice_id UUID REFERENCES finance_invoices(id),
  equipment_purchase_id UUID REFERENCES finance_equipment_purchases(id),
  CHECK ((debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0))
);
CREATE INDEX finance_journal_lines_account_idx ON finance_journal_lines(owner_account_id,entry_id);

CREATE TABLE finance_production_receipt_allocations (
  transaction_id UUID NOT NULL REFERENCES finance_transactions(id),
  production_id UUID NOT NULL REFERENCES productions(id),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  legacy_type VARCHAR(32),
  PRIMARY KEY(transaction_id,production_id)
);
CREATE INDEX finance_production_receipts_production_idx ON finance_production_receipt_allocations(production_id);
CREATE TABLE finance_employee_payment_allocations (
  transaction_id UUID NOT NULL REFERENCES finance_transactions(id),
  obligation_id UUID NOT NULL REFERENCES finance_employee_obligations(id),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  PRIMARY KEY(transaction_id,obligation_id)
);
CREATE INDEX finance_employee_payments_obligation_idx ON finance_employee_payment_allocations(obligation_id);
CREATE TABLE finance_counterparty_payment_allocations (
  transaction_id UUID NOT NULL REFERENCES finance_transactions(id),
  charge_id UUID NOT NULL REFERENCES finance_counterparty_charges(id),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  PRIMARY KEY(transaction_id,charge_id)
);
CREATE INDEX finance_counterparty_payments_charge_idx ON finance_counterparty_payment_allocations(charge_id);
CREATE TABLE finance_invoice_payment_allocations (
  transaction_id UUID NOT NULL REFERENCES finance_transactions(id),
  invoice_id UUID NOT NULL REFERENCES finance_invoices(id),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  PRIMARY KEY(transaction_id,invoice_id)
);
CREATE INDEX finance_invoice_payments_invoice_idx ON finance_invoice_payment_allocations(invoice_id);
CREATE TABLE finance_equipment_payment_allocations (
  transaction_id UUID NOT NULL REFERENCES finance_transactions(id),
  purchase_id UUID NOT NULL REFERENCES finance_equipment_purchases(id),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  PRIMARY KEY(transaction_id,purchase_id)
);
CREATE INDEX finance_equipment_payments_purchase_idx ON finance_equipment_payment_allocations(purchase_id);

CREATE TABLE finance_reconciliation_snapshots (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  calculation_version VARCHAR(24) NOT NULL,
  overall_result NUMERIC(19,2) NOT NULL,
  azeem_position NUMERIC(19,2) NOT NULL,
  akash_position NUMERIC(19,2) NOT NULL,
  receivables NUMERIC(19,2) NOT NULL,
  employee_payables NUMERIC(19,2) NOT NULL,
  invoice_receivables NUMERIC(19,2) NOT NULL,
  equipment_payables NUMERIC(19,2) NOT NULL,
  control_difference NUMERIC(19,2) NOT NULL,
  status VARCHAR(16) NOT NULL CHECK (status IN ('RECONCILED','WARNING','BROKEN'))
);
CREATE TABLE finance_projection_checkpoints (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rebuilt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  before_hash CHAR(64) NOT NULL,
  after_hash CHAR(64) NOT NULL,
  consistent BOOLEAN NOT NULL
);

CREATE TABLE finance_migration_batches (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workbook_sha256 CHAR(64) NOT NULL,
  workbook_name VARCHAR(240) NOT NULL,
  status VARCHAR(24) NOT NULL CHECK (status IN ('PREVIEW','VALIDATED','COMMITTED','REVIEW_REQUIRED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  committed_at TIMESTAMPTZ
);
CREATE TABLE finance_migration_rows (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id),
  sheet_name VARCHAR(160) NOT NULL,
  source_row INTEGER NOT NULL CHECK (source_row > 0),
  source_range VARCHAR(80),
  raw_values JSONB NOT NULL,
  legacy_type VARCHAR(40),
  match_confidence VARCHAR(24) CHECK (match_confidence IN ('EXACT','HIGH_CONFIDENCE','REVIEW_REQUIRED','DISTINCT')),
  canonical_transaction_id UUID REFERENCES finance_transactions(id),
  linked_row_id UUID REFERENCES finance_migration_rows(id),
  UNIQUE(batch_id,sheet_name,source_row)
);
CREATE TABLE finance_migration_issues (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id UUID NOT NULL REFERENCES finance_migration_batches(id),
  row_id UUID REFERENCES finance_migration_rows(id),
  issue_code VARCHAR(64) NOT NULL,
  detail VARCHAR(1000) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED','IGNORED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at TIMESTAMPTZ
);
CREATE TABLE finance_audit_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  transaction_id UUID REFERENCES finance_transactions(id),
  action VARCHAR(60) NOT NULL,
  actor VARCHAR(180) NOT NULL,
  reason VARCHAR(500),
  evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE FUNCTION finance_protect_posted() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.status IN ('POSTED','REVERSED') THEN RAISE EXCEPTION 'Posted financial transactions cannot be deleted'; END IF;
    RETURN OLD;
  END IF;
  IF OLD.status IN ('POSTED','REVERSED') THEN
    IF OLD.status = 'POSTED' AND NEW.status = 'REVERSED'
       AND (to_jsonb(NEW) - 'status') = (to_jsonb(OLD) - 'status') THEN RETURN NEW; END IF;
    RAISE EXCEPTION 'Posted financial transactions are immutable';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER finance_transactions_immutable BEFORE UPDATE OR DELETE ON finance_transactions
FOR EACH ROW EXECUTE FUNCTION finance_protect_posted();

CREATE FUNCTION finance_protect_journal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Posted journal evidence is immutable'; END $$;
CREATE TRIGGER finance_journal_entries_immutable BEFORE UPDATE OR DELETE ON finance_journal_entries
FOR EACH ROW EXECUTE FUNCTION finance_protect_journal();
CREATE TRIGGER finance_journal_lines_immutable BEFORE UPDATE OR DELETE ON finance_journal_lines
FOR EACH ROW EXECUTE FUNCTION finance_protect_journal();

CREATE FUNCTION finance_check_journal_balance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE debits NUMERIC(19,2); credits NUMERIC(19,2); line_count INTEGER;
BEGIN
  SELECT coalesce(sum(debit),0),coalesce(sum(credit),0),count(*) INTO debits,credits,line_count
    FROM finance_journal_lines WHERE entry_id=NEW.id;
  IF line_count < 2 OR debits <> credits THEN
    RAISE EXCEPTION 'Financial journal entry % is unbalanced',NEW.id;
  END IF;
  RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER finance_journal_balanced AFTER INSERT ON finance_journal_entries
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION finance_check_journal_balance();
