import { api, json } from "../../lib/api";

export type Money = number;
export const financeAmount = (value: Money) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(value);
export type FinanceAccount = {
  id: string;
  code: string;
  displayName: string;
  position: Money;
};
export type FinancePageResult<T> = {
  items: T[];
  page: number;
  size: number;
  total: number;
};
export type FinanceOverview = {
  received: Money;
  incurredExpense: Money;
  overallResult: Money;
  contracted: Money;
  postedCount: number;
  receivables: Money;
  accounts: FinanceAccount[];
};
export type FinanceTransaction = {
  id: string;
  transactionNo: number;
  date: string;
  type: string;
  status: string;
  description: string;
  amount: Money;
  productionTitle?: string;
  employeeName?: string;
  counterpartyName?: string;
  reversalOf?: string;
};
export type FinanceProduction = {
  id: string;
  title: string;
  clientName: string;
  eventDate: string;
  contracted: Money;
  received: Money;
  expense: Money;
};
export type FinanceEmployee = {
  id: string;
  displayName: string;
  earned: Money;
  paid: Money;
};
export type FinanceParty = {
  id: string;
  displayName: string;
  role: string;
  gstin?: string;
  charged: Money;
  received: Money;
};

export type WorkbookPartySummary = {
  available: boolean;
  workbookSha256?: string;
  partyBlocks: number;
  parties: number;
  business: number;
  received: number;
  outstanding: number;
  duplicateBlocks: number;
  parityMismatches: number;
  outstandingParties: number;
  settledParties: number;
  negativeParties: number;
  productionLinks: number;
  ownerLinks: number;
  threeWayReceipts: number;
  activeEntries: number;
  duplicateRepresentations: number;
  unlinkedPayments: number;
  discountSettlement: number;
};
export type WorkbookParty = {
  key: string;
  name: string;
  business: number;
  received: number;
  outstanding: number;
  blocks: number;
  entries: number;
  lastActivity: string | null;
  productionLinks: number;
  ownerLinks: number;
};
export type WorkbookPartyBlock = {
  id: string;
  blockIndex: number;
  name: string;
  disposition: string;
  sourceRange: string;
  workbookAmount: number;
  workbookPayment: number;
  workbookBalance: number;
  projectionAmount: number;
  projectionPayment: number;
  projectionBalance: number;
  parityStatus: string;
  layout?: {
    columns: Record<string, string>;
    controlCells: Record<string, string>;
    controlFormulas: Record<string, string>;
  };
};
export type WorkbookPartyDetail = {
  key: string;
  name: string;
  role: string;
  business: number;
  received: number;
  outstanding: number;
  blocks: WorkbookPartyBlock[];
};
export type WorkbookPartyEntry = {
  id: string;
  date: string | null;
  rawDate: string | null;
  venue: string | null;
  service: string | null;
  rate: string | null;
  amount: number;
  payment: number;
  balanceAfter: number;
  sourceRow: number;
  blockId: string;
  blockIndex: number;
  partyName: string;
  disposition: string;
  duplicateOfEntryId: string | null;
  productionLinks: number;
  ownerLinks: number;
};
export type WorkbookPartyEntryDetail = WorkbookPartyEntry & {
  partyKey: string;
  blockRange: string;
  workbookSha256: string;
  historicalReceiptId?: string;
  receiptEvidenceCount?: number;
  receiptContribution?: number;
  settlementKind?: "PAYMENT" | "DISCOUNT";
  raw: { cells: Record<string, unknown>; formulas: Record<string, string> };
  links: {
    relationType: string;
    amount: number;
    confidence: string;
    factId: string;
    linkedRowId: string;
    accountCode: string | null;
    sheet: string;
    sourceRange: string;
    sourceCell: string;
  }[];
};
export type FinanceInvoice = {
  id: string;
  invoiceNumber: string;
  date: string;
  counterpartyName: string;
  total: Money;
  tds: Money;
  paid: Money;
};
export type FinancePurchase = {
  id: string;
  date: string;
  description: string;
  total: Money;
  paid: Money;
  counterpartyName?: string;
};
export type FinanceReconciliation = {
  status: "RECONCILED" | "WARNING" | "BROKEN";
  controlDifference: Money;
  overallResult: Money;
  azeemPosition: Money;
  akashPosition: Money;
  receivables: Money;
  employeePayables: Money;
  invoiceReceivables: Money;
  equipmentPayables: Money;
  migrationOpenCount?: number;
};
export type FinanceConfig = {
  accounts: FinanceAccount[];
  expenseCategories: { id: string; code: string; displayName: string }[];
  profitSplit: { effectiveFrom: string; azeem: number; akash: number }[];
};
export type FinanceMigrationPreview = {
  batch: {
    id: string;
    workbook_name: string;
    workbook_sha256: string;
    status: string;
  };
  sheets: { sheetName: string; rows: number; reviewRows: number }[];
  openIssueCount: number;
  unmappedRowCount: number;
  issues: {
    id: string;
    sheetName: string;
    sourceRow: number;
    code: string;
    detail: string;
  }[];
  resolution: {
    sourceRows: number;
    financialRows: number;
    nonFinancialRows: number;
    facts: number;
    linkedFacts: number;
    duplicateSourceLinks: number;
    ownerTransferLinks: number;
    canonicalPosted: number;
    unallocatedAmount: number;
    classifications: {
      classification: string;
      count: number;
      amount: number;
    }[];
    exactOrHighLinks: { confidence: string; count: number }[];
    accountAttribution: {
      account_code: string;
      facts: number;
      amount: number;
    }[];
    issueGroups: { reason: string; count: number; amount: number }[];
  };
  parity: {
    metric: string;
    workbook: number | null;
    finance: number | null;
    difference: number | null;
    status: string;
    sourceFactCount: number;
    postedFactCount: number;
    unresolvedContribution: number | null;
  }[];
};
export type FinanceMigrationReviewItem = {
  id: string;
  slot: string;
  sourceRange: string;
  eventType: string;
  amount: number | null;
  date: string | null;
  rawName: string | null;
  accountCode: string | null;
  classification: string;
  reason: string;
  evidence: unknown;
  sheetName: string;
  sourceRow: number;
  original: unknown;
};
export type WorkbookProduction = {
  id: string;
  sheet: string;
  sourceRow: number;
  sourceRange: string;
  date: string | null;
  venue: string | null;
  client: string | null;
  service: string | string[] | null;
  total: number;
  add: number;
  payment: number;
  received: number;
  outstanding: number;
  expense: number | null;
  legacyBudget: number | null;
  contractedMargin: number | null;
  sourceBalance: number | null;
  balanceParity: string;
  sourceBudget: number | null;
  budgetParity: string;
};
export type WorkbookProductionDetail = WorkbookProduction & {
  cells: Record<string, string | number | boolean>;
  formulas: Record<string, string>;
  sourceFacts: {
    slot: string;
    event_type: string;
    amount: number | null;
    event_date: string | null;
    account_code: string | null;
    classification: string;
    issue_code: string | null;
  }[];
  linkedOwnerEvidence: {
    accountCode: string;
    sheet: string;
    sourceRange: string;
    sourceCell: string;
    movementId: string;
    amount: number;
    confidence: string;
  }[];
  employeeEvidence: {
    id: string;
    employeeKey: string;
    name: string;
    kind: string;
    amount: number;
    sourceColumn: string;
    identityStatus: string;
  }[];
  partyEvidence: {
    entryId: string;
    partyKey: string;
    partyName: string;
    relationType: string;
    amount: number;
    confidence: string;
    disposition: string;
  }[];
};
export type WorkbookOwnerSegment = {
  sheet: string;
  block: string;
  label: string;
  month?: string;
  monthKey?: string;
  moneyIn: number;
  moneyOut: number;
  projection: number;
  workbookPosition: number;
  difference: number;
  movementCount: number;
  controlCells: { position: string; in: string; out: string };
};
export type WorkbookOwner = {
  code: "AZ" | "AK";
  name: string;
  previousPosition: number;
  openingReference: number;
  continuity: string;
  position: number;
  moneyIn: number;
  moneyOut: number;
  newMoneyIn: number;
  segments: WorkbookOwnerSegment[];
  earlierRows: number;
  currentRows: number;
  linkedMovements: number;
  unlinkedMovements: number;
};
export type WorkbookOwnerSummary = {
  available: boolean;
  workbookSha256?: string;
  owners: WorkbookOwner[];
  transferCount: number;
  transferTotal: number;
  parity: {
    metric: string;
    workbook: number;
    projection: number;
    difference: number;
    status: string;
  }[];
};
export type WorkbookOwnerMovement = {
  id: string;
  rowId: string;
  sheet: string;
  sourceRow: number;
  sourceRange: string;
  sourceCell: string;
  slot: string;
  date: string | null;
  description: string | null;
  amount: number;
  direction: "IN" | "OUT";
  positionAfter: number;
  period: string;
  status: string;
  businessType: string;
  owner: "AZ" | "AK";
};
export type WorkbookOwnerMovementDetail = WorkbookOwnerMovement & {
  accountCode: string;
  workbookSha256: string;
  raw: {
    cells: Record<string, string | number | boolean>;
    formulas?: Record<string, string>;
  };
  linkedBusiness: {
    factId: string;
    eventType: string;
    amount: number;
    rowId: string;
    sheet: string;
    sourceRow: number;
    sourceRange: string;
    venue?: string;
    client?: string;
    confidence: string;
    employeeEventId?: string;
    employeeKey?: string;
  }[];
  transferPartner: {
    movementId: string;
    confidence: string;
    evidence: string;
  }[];
  partyEvidence: {
    entryId: string;
    partyKey: string;
    partyName: string;
    amount: number;
    confidence: string;
  }[];
};
export type WorkbookTransfer = {
  id: string;
  primaryMovementId: string;
  otherMovementId: string;
  fromAccount: string;
  toAccount: string;
  amount: number;
  date: string | null;
  confidence: string;
  evidence: string;
  primarySheet: string;
  primaryRange: string;
  otherSheet: string;
  otherRange: string;
};
export type WorkbookEmployee = {
  key: string;
  name: string;
  earned: number;
  paid: number;
  outstanding: number;
  facts: number;
};
export type WorkbookEmployeeSummary = {
  available: boolean;
  workbookSha256?: string;
  employees: WorkbookEmployee[];
  earned: number;
  paid: number;
  outstanding: number;
  reviewFacts: number;
  linkedPayments: number;
  parity: {
    sheet: string;
    column: string;
    employee: string;
    kind: string;
    workbook: number;
    projection: number;
    difference: number;
    status: string;
  }[];
};
export type WorkbookEmployeeEvent = {
  id: string;
  employeeKey: string;
  employeeName: string;
  kind: "EARNING" | "PAYMENT";
  amount: number;
  date: string | null;
  identityStatus: string;
  sourceColumn: string;
  sourceHeading: string;
  productionRowId: string;
  sheet: string;
  sourceRow: number;
  venue: string | null;
  client: string | null;
  balanceAfter: number | null;
};
export type WorkbookEmployeeEventDetail = WorkbookEmployeeEvent & {
  sourceRange: string;
  workbookSha256: string;
  raw: {
    cells: Record<string, string | number | boolean>;
    formulas: Record<string, string>;
  };
  linkedOwnerEvidence: {
    movementId: string;
    accountCode: string;
    sheet: string;
    sourceCell: string;
    amount: number;
    confidence: string;
  }[];
};
export type WorkbookSalaryEvidence = {
  productionRowId: string;
  sheet: string;
  sourceRow: number;
  date: string | null;
  name: string | null;
  amount: number;
};
export type WorkbookGstSummary = {
  available: boolean;
  workbookSha256?: string;
  invoiceCount: number;
  base: number;
  tax: number;
  total: number;
  tds: number;
  cash: number;
  outstanding: number;
  parityMismatches: number;
  partyLinks: number;
  productionLinks: number;
  ownerLinks: number;
};
export type WorkbookGstInvoice = {
  id: string;
  invoiceNumber: string;
  date: string | null;
  rawDate: string | null;
  partyName: string;
  gstin: string | null;
  base: number;
  igst: number;
  cgst: number;
  sgst: number;
  total: number;
  tds: number;
  cash: number;
  outstanding: number;
  workbookOutstanding: number | null;
  parityStatus: string;
  partyKey: string | null;
  productionRowId: string | null;
  sourceRow: number;
};
export type WorkbookGstInvoiceDetail = WorkbookGstInvoice & {
  partyKeyLink: string | null;
  workbookSha256: string;
  raw: { cells: Record<string, unknown>; formulas: Record<string, string> };
  settlements: {
    id: string;
    kind: "CASH" | "TDS";
    slot: string;
    amount: number;
    accountCode: string | null;
    ownerRowId: string | null;
    ownerSheet: string | null;
    ownerCell: string | null;
  }[];
};
export type WorkbookPurchase = {
  id: string;
  book: "LED" | "SOUND";
  sourceRow: number;
  date: string | null;
  rawDate: string | null;
  description: string;
  rate: number | null;
  tax: number | null;
  quantity: number | null;
  purchaseAmount: number;
  paid: number;
  outstanding: number;
  hqEquipmentId: string | null;
  hqLinkConfidence: string | null;
};
export type WorkbookPurchaseDetail = WorkbookPurchase & {
  workbookSha256: string;
  raw: { cells: Record<string, unknown>; formulas: Record<string, string> };
  payments: {
    id: string;
    date: string | null;
    rawDate: string | null;
    description: string | null;
    amount: number;
    sourceRow: number;
    accountCode: string | null;
    ownerRowId: string | null;
    ownerSheet: string | null;
    ownerCell: string | null;
  }[];
};
export type WorkbookPurchaseSummary = {
  available: boolean;
  workbookSha256?: string;
  books: {
    book: "LED" | "SOUND";
    records: number;
    purchased: number;
    allocated_paid: number;
    line_outstanding: number;
    paid: number;
    outstanding: number;
    paymentRecords: number;
    ownerLinked: number;
  }[];
  accessories: {
    records: number;
    accessory_quantity: number;
    equipment_quantity: number;
    duplicate_snapshots: number;
    hq_links: number;
  };
};
export type WorkbookAccessory = {
  id: string;
  kind: "ACCESSORY" | "EQUIPMENT_REFERENCE";
  sourceRow: number;
  sourceBlock: string;
  description: string;
  quantity: number;
  listPrice: number | null;
  preTaxPrice: number | null;
  valueAmount: number | null;
  hqEquipmentId: string | null;
  hqLinkConfidence: string | null;
};
export type WorkbookProductionPage = FinancePageResult<WorkbookProduction> & {
  available: boolean;
  batchId?: string;
  sheets: string[];
  sourceControl?: {
    sourceRow: number;
    contracted: number | null;
    add: number | null;
    payment: number | null;
    balance: number | null;
    expense: number | null;
    legacyBudget: number | null;
  };
};

const list = <T>(path: string, page: number, search = "") =>
  api<FinancePageResult<T>>(
    `/finance/${path}?page=${page}&size=50&search=${encodeURIComponent(search)}`,
  );
export const financeApi = {
  overview: () => api<FinanceOverview>("/finance/overview"),
  reconciliation: () => api<FinanceReconciliation>("/finance/reconciliation"),
  config: () => api<FinanceConfig>("/finance/config"),
  transactions: (page = 0, search = "") =>
    list<FinanceTransaction>("transactions", page, search),
  productions: (page = 0, search = "") =>
    list<FinanceProduction>("productions", page, search),
  workbookProductions: (page = 0, sheet = "", search = "") =>
    api<WorkbookProductionPage>(
      `/finance/workbook/productions?page=${page}&size=50&sheet=${encodeURIComponent(sheet)}&search=${encodeURIComponent(search)}`,
    ),
  workbookProduction: (id: string) =>
    api<WorkbookProductionDetail>(`/finance/workbook/productions/${id}`),
  workbookOwners: () => api<WorkbookOwnerSummary>("/finance/workbook/owners"),
  workbookOwnerMovements: (
    owner: "AZ" | "AK",
    params: Record<string, string | number>,
  ) =>
    api<FinancePageResult<WorkbookOwnerMovement> & { available: boolean }>(
      `/finance/workbook/owners/${owner}/movements?${new URLSearchParams(Object.entries(params).map(([key, value]) => [key, String(value)])).toString()}`,
    ),
  workbookOwnerMovement: (id: string) =>
    api<WorkbookOwnerMovementDetail>(
      `/finance/workbook/owners/movements/${id}`,
    ),
  workbookTransfers: (page = 0) =>
    api<FinancePageResult<WorkbookTransfer> & { available: boolean }>(
      `/finance/workbook/owners/transfers?page=${page}&size=50`,
    ),
  workbookEmployees: () =>
    api<WorkbookEmployeeSummary>("/finance/workbook/employees"),
  workbookEmployeeEvents: (params: Record<string, string | number>) =>
    api<FinancePageResult<WorkbookEmployeeEvent> & { available: boolean }>(
      `/finance/workbook/employees/events?${new URLSearchParams(Object.entries(params).map(([key, value]) => [key, String(value)])).toString()}`,
    ),
  workbookEmployeeEvent: (id: string) =>
    api<WorkbookEmployeeEventDetail>(
      `/finance/workbook/employees/events/${id}`,
    ),
  workbookSalaryEvidence: (page = 0) =>
    api<FinancePageResult<WorkbookSalaryEvidence> & { available: boolean }>(
      `/finance/workbook/employees/salary-evidence?page=${page}&size=50`,
    ),
  workbookPartySummary: () =>
    api<WorkbookPartySummary>("/finance/workbook/parties/summary"),
  workbookParties: (params: Record<string, string | number>) =>
    api<FinancePageResult<WorkbookParty> & { available: boolean }>(
      `/finance/workbook/parties?${new URLSearchParams(Object.entries(params).map(([key, value]) => [key, String(value)])).toString()}`,
    ),
  workbookParty: (key: string) =>
    api<WorkbookPartyDetail>(
      `/finance/workbook/parties/${encodeURIComponent(key)}`,
    ),
  workbookPartyEntries: (
    key: string,
    params: Record<string, string | number>,
  ) =>
    api<FinancePageResult<WorkbookPartyEntry> & { available: boolean }>(
      `/finance/workbook/parties/${encodeURIComponent(key)}/entries?${new URLSearchParams(Object.entries(params).map(([name, value]) => [name, String(value)])).toString()}`,
    ),
  workbookPartyEntry: (id: string) =>
    api<WorkbookPartyEntryDetail>(`/finance/workbook/parties/entries/${id}`),
  workbookPartyBlocks: (page = 0) =>
    api<FinancePageResult<WorkbookPartyBlock> & { available: boolean }>(
      `/finance/workbook/parties/blocks?page=${page}&size=50`,
    ),
  workbookGstSummary: () =>
    api<WorkbookGstSummary>("/finance/workbook/commercial/gst/summary"),
  workbookGstInvoices: (params: Record<string, string | number>) =>
    api<FinancePageResult<WorkbookGstInvoice> & { available: boolean }>(
      `/finance/workbook/commercial/gst/invoices?${new URLSearchParams(Object.entries(params).map(([key, value]) => [key, String(value)])).toString()}`,
    ),
  workbookGstInvoice: (id: string) =>
    api<WorkbookGstInvoiceDetail>(
      `/finance/workbook/commercial/gst/invoices/${id}`,
    ),
  workbookPurchaseSummary: () =>
    api<WorkbookPurchaseSummary>(
      "/finance/workbook/commercial/purchases/summary",
    ),
  workbookPurchases: (params: Record<string, string | number>) =>
    api<FinancePageResult<WorkbookPurchase> & { available: boolean }>(
      `/finance/workbook/commercial/purchases?${new URLSearchParams(Object.entries(params).map(([key, value]) => [key, String(value)])).toString()}`,
    ),
  workbookPurchase: (id: string) =>
    api<WorkbookPurchaseDetail>(`/finance/workbook/commercial/purchases/${id}`),
  workbookAccessories: (params: Record<string, string | number>) =>
    api<FinancePageResult<WorkbookAccessory> & { available: boolean }>(
      `/finance/workbook/commercial/accessories?${new URLSearchParams(Object.entries(params).map(([key, value]) => [key, String(value)])).toString()}`,
    ),
  production: (id: string) =>
    api<{
      production: {
        id: string;
        title: string;
        clientName: string;
        contracted: Money;
      };
      received: Money;
      outstanding: Money;
      incurredExpense: Money;
      contractedMargin: Money;
      realizedMargin: Money;
      transactions: FinanceTransaction[];
    }>(`/finance/productions/${id}`),
  employees: (page = 0) => list<FinanceEmployee>("employee-payables", page),
  employee: (id: string) =>
    api<{
      employee: { id: string; displayName: string };
      earned: Money;
      paid: Money;
      outstanding: Money;
      obligations: {
        id: string;
        date: string;
        type: string;
        amount: Money;
        description: string;
      }[];
    }>(`/finance/employees/${id}`),
  counterparties: (page = 0, search = "") =>
    list<FinanceParty>("counterparties", page, search),
  invoices: (page = 0) => list<FinanceInvoice>("invoices", page),
  purchases: (page = 0) => list<FinancePurchase>("equipment-purchases", page),
  transaction: (id: string) =>
    api<{
      transaction: FinanceTransaction;
      journal: {
        ledgerCode: string;
        debit: Money;
        credit: Money;
        ownerAccount?: string;
      }[];
    }>(`/finance/transactions/${id}`),
  setContract: (data: {
    idempotencyKey: string;
    productionId: string;
    amount: Money;
    date: string;
    description: string;
  }) =>
    api<{ id: string }>("/finance/contracts", {
      method: "POST",
      ...json(data),
    }),
  recordReceipt: (data: {
    idempotencyKey: string;
    productionId: string;
    amount: Money;
    date: string;
    description: string;
    receiverAccount: string;
    counterpartyId?: string;
    legacyType?: string;
  }) =>
    api<{ id: string }>("/finance/receipts", { method: "POST", ...json(data) }),
  logExpense: (data: {
    idempotencyKey: string;
    amount: Money;
    date: string;
    description: string;
    payerAccount: string;
    productionId?: string;
    counterpartyId?: string;
    employeeId?: string;
    equipmentId?: string;
    categoryCode?: string;
  }) =>
    api<{ id: string }>("/finance/expenses", { method: "POST", ...json(data) }),
  addEarning: (data: {
    idempotencyKey: string;
    employeeId: string;
    productionId?: string;
    amount: Money;
    date: string;
    description: string;
  }) =>
    api<{ id: string }>("/finance/earnings", { method: "POST", ...json(data) }),
  post: (path: string, body: unknown) =>
    api<{ id: string }>(`/finance/${path}`, { method: "POST", ...json(body) }),
  reverse: (id: string, body: unknown) =>
    api<{ id: string }>(`/finance/transactions/${id}/reverse`, {
      method: "POST",
      ...json(body),
    }),
  rebuild: () =>
    api<FinanceReconciliation>("/finance/reconciliation/rebuild", {
      method: "POST",
    }),
  previewWorkbook: (filename: string, base64: string) =>
    api<FinanceMigrationPreview>("/finance/migrations/excel/preview", {
      method: "POST",
      ...json({ filename, base64 }),
    }),
  validateWorkbook: (id: string) =>
    api<FinanceMigrationPreview>(`/finance/migrations/${id}/validate`, {
      method: "POST",
    }),
  postProvenWorkbook: (id: string) =>
    api<FinanceMigrationPreview>(`/finance/migrations/${id}/post-proven`, {
      method: "POST",
    }),
  migrationReport: (id: string) =>
    api<FinanceMigrationPreview>(`/finance/migrations/${id}`),
  migrationReview: (id: string, page: number) =>
    api<{ total: number; page: number; items: FinanceMigrationReviewItem[] }>(
      `/finance/migrations/${id}/review?page=${page}`,
    ),
  decideMigration: (
    id: string,
    decision: {
      sheetName: string;
      sourceRow: number;
      slot: string;
      action: string;
      target?: string;
      reason: string;
    },
  ) =>
    api<{ overrideId: string }>(`/finance/migrations/${id}/review`, {
      method: "POST",
      ...json(decision),
    }),
  resetMigration: () =>
    api<{ reset: boolean; preservedReviewedOverrides: number }>(
      "/finance/migrations/reset",
      { method: "POST" },
    ),
};
