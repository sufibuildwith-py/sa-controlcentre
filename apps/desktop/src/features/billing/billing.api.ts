import { api, json } from "../../lib/api";

export type BillingLine = {
  id?: string; lineNo?: number; quantity: number; days: number; description: string; rate: number; reference: string; amount?: number;
};
export type BillingBill = {
  id: string; bill_number: string; billNumber?: string; bill_date: string; billDate?: string; financial_year: string; financialYear?: string; customer: string;
  counterparty_id?: string; counterpartyId?: string; production_id?: string | null; productionId?: string | null;
  event_name?: string | null; eventName?: string | null; venue?: string | null; tax_mode?: string; taxMode?: "NONE" | "CGST_SGST" | "IGST" | "CUSTOM";
  gstin?: string | null; cgst_rate?: number; cgstRate?: number; sgst_rate?: number; sgstRate?: number; igst_rate?: number; igstRate?: number;
  discount?: number; freight?: number; advance_paid?: number; advancePaid?: number; subtotal?: number; tax_amount?: number; taxAmount?: number;
  gross_total?: number; grossTotal?: number; balanceDue?: number; notes?: string | null; payment_terms?: string | null; paymentTerms?: string | null;
  status: "DRAFT" | "ISSUED" | "PARTIALLY_PAID" | "PAID" | "CANCELLED";
  canonical_invoice_id?: string | null; canonicalInvoiceId?: string | null;
};
export type BillingDetail = { bill: BillingBill & Record<string, unknown>; lines: BillingLine[] };
export type BillingCreate = {
  billNumber: string; billDate: string; financialYear: string; counterpartyId: string; productionId?: string | null;
  eventName?: string | null; venue?: string | null; taxMode: "NONE" | "CGST_SGST" | "IGST" | "CUSTOM"; gstin?: string | null;
  cgstRate: number; sgstRate: number; igstRate: number; discount: number; freight: number; advancePaid: number;
  notes?: string | null; paymentTerms?: string | null; lines: BillingLine[];
};
export const billingApi = {
  list: () => api<BillingBill[]>("/billing"),
  get: (id: string) => api<BillingDetail>("/billing/" + id),
  create: (body: BillingCreate) => api<BillingDetail>("/billing", { method: "POST", ...json(body) }),
  update: (id: string, body: BillingCreate) => api<BillingDetail>("/billing/" + id, { method: "PUT", ...json(body) }),
  issue: (id: string) => api<BillingDetail>("/billing/" + id + "/issue", { method: "POST", ...json({ idempotencyKey: crypto.randomUUID() }) }),
  cancel: (id: string) => api<BillingDetail>("/billing/" + id + "/cancel", { method: "POST" }),
  export: (id: string) => api<{ filename: string; contentType: string; base64: string }>("/billing/" + id + "/export"),
  exportPdf: (id: string) => api<{ filename: string; contentType: string; base64: string }>("/billing/" + id + "/export-pdf"),
};