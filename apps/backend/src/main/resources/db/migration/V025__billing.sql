CREATE TABLE billing_bills (
  id UUID PRIMARY KEY,
  bill_number VARCHAR(100) NOT NULL UNIQUE,
  bill_date DATE NOT NULL,
  financial_year VARCHAR(20) NOT NULL,
  counterparty_id UUID NOT NULL REFERENCES finance_counterparties(id),
  production_id UUID REFERENCES productions(id),
  event_name VARCHAR(240),
  venue VARCHAR(240),
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  tax_mode VARCHAR(24) NOT NULL DEFAULT 'NONE',
  gstin VARCHAR(40),
  discount NUMERIC(14,2) NOT NULL DEFAULT 0,
  freight NUMERIC(14,2) NOT NULL DEFAULT 0,
  advance_paid NUMERIC(14,2) NOT NULL DEFAULT 0,
  subtotal NUMERIC(14,2) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
  gross_total NUMERIC(14,2) NOT NULL DEFAULT 0,
  canonical_invoice_id UUID REFERENCES finance_invoices(id),
  notes TEXT,
  payment_terms VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  issued_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  CONSTRAINT billing_bill_status_ck CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','CANCELLED')),
  CONSTRAINT billing_bill_tax_mode_ck CHECK (tax_mode IN ('NONE','CGST_SGST','IGST','CUSTOM')),
  CONSTRAINT billing_bill_money_ck CHECK (discount >= 0 AND freight >= 0 AND advance_paid >= 0 AND subtotal >= 0 AND tax_amount >= 0 AND gross_total >= 0)
);

CREATE TABLE billing_bill_lines (
  id UUID PRIMARY KEY,
  bill_id UUID NOT NULL REFERENCES billing_bills(id) ON DELETE CASCADE,
  line_no INT NOT NULL,
  quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
  days NUMERIC(14,3) NOT NULL DEFAULT 1,
  description VARCHAR(500) NOT NULL,
  rate NUMERIC(14,2) NOT NULL DEFAULT 0,
  amount NUMERIC(14,2) NOT NULL DEFAULT 0,
  reference VARCHAR(180),
  CONSTRAINT billing_line_money_ck CHECK (quantity > 0 AND days > 0 AND rate >= 0 AND amount >= 0),
  CONSTRAINT billing_line_no_uq UNIQUE (bill_id, line_no)
);

CREATE INDEX billing_bills_status_idx ON billing_bills(status);
CREATE INDEX billing_bills_counterparty_idx ON billing_bills(counterparty_id);
CREATE INDEX billing_bills_date_idx ON billing_bills(bill_date DESC);
CREATE INDEX billing_bill_lines_bill_idx ON billing_bill_lines(bill_id);
