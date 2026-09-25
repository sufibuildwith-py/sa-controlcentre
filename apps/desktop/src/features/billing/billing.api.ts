import { api, json } from "../../lib/api";

export type BillingLine = {
  id?: string; lineNo?: number; quantity: number; days: number; description: string; rate: number; reference: string; amount?: number;
};
export type BillingBill = {
  id: string; bill_number: string; billNumber?: string; bill_date: string; billDate?: string; financial_year: string; customer: string;
  status: "DRAFT" | "ISSUED" | "PARTIALLY_PAID" | "PAID" | "CANCELLED"; gross_total: number; grossTotal?: number; advance_paid: number; advancePaid?: number; balanceDue?: number; subtotal?: number; tax_amount?: number;
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
  issue: (id: string) => api<BillingDetail>("/billing/" + id + "/issue", { method: "POST", ...json({ idempotencyKey: crypto.randomUUID() }) }),
  cancel: (id: string) => api<BillingDetail>("/billing/" + id + "/cancel", { method: "POST" }),
  export: (id: string) => api<{ filename: string; contentType: string; base64: string }>("/billing/" + id + "/export"),
};