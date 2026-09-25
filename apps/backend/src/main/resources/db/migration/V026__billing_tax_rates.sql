ALTER TABLE billing_bills
  ADD COLUMN cgst_rate NUMERIC(7,3) NOT NULL DEFAULT 0,
  ADD COLUMN sgst_rate NUMERIC(7,3) NOT NULL DEFAULT 0,
  ADD COLUMN igst_rate NUMERIC(7,3) NOT NULL DEFAULT 0;

ALTER TABLE billing_bills
  ADD CONSTRAINT billing_bill_tax_rate_ck CHECK (cgst_rate >= 0 AND sgst_rate >= 0 AND igst_rate >= 0);
