import { api } from "../../lib/api";

export type MoneyValue = number;

export type CommandToday = {
  productions: number;
  tasks: number;
  attendanceExceptions: number;
  moneyMovement: {
    received: MoneyValue;
    disbursed: MoneyValue;
    net: MoneyValue;
    transactionCount: number;
  };
};

export type CommandMoney = {
  businessPosition: MoneyValue;
  azeemPosition: MoneyValue;
  akashPosition: MoneyValue;
  customerReceivable: MoneyValue;
  employeePayable: MoneyValue;
  invoiceReceivable: MoneyValue;
  totalReceivable: MoneyValue;
  reconciliationStatus: "RECONCILED" | "WARNING" | "BROKEN" | string;
};

export type CommandAttentionItem = {
  id: string;
  severity: "CRITICAL" | "HIGH" | "MEDIUM" | "INFO" | string;
  type: string;
  category: "RECONCILIATION" | "FINANCE" | "PAYROLL" | "PRODUCTION" | "WORK" | "ATTENDANCE" | string;
  title: string;
  reason: string;
  description: string;
  entityType: string;
  entityId?: string;
  amount?: MoneyValue;
  count?: number;
  route: string;
  queryParams?: string;
};

export type DashboardProduction = {
  id: string;
  title: string;
  clientName: string;
  venueName: string;
  eventDate: string;
  startTime?: string;
  endTime?: string;
  status: string;
  priority: string;
  progressPercent: number;
  contractedAmount: MoneyValue;
  receivedAmount: MoneyValue;
  taskCount: number;
  openTaskCount: number;
};

export type DashboardTask = {
  id: string;
  title: string;
  priority: string;
  status: string;
  dueAt?: string;
  assignedEmployeeName?: string;
  assignedEmployeeId?: string;
  productionTitle?: string;
  productionId?: string;
  isOverdue: boolean;
  bucket: "OVERDUE" | "DUE_TODAY" | "PENDING" | string;
};

export type DashboardAttendanceException = {
  employeeId: string;
  employeeName: string;
  status: string;
  checkInTime?: string;
  minutesLate?: number;
  notes?: string;
};

export type CommandOperations = {
  upcomingProductions: DashboardProduction[];
  pendingWork: DashboardTask[];
  attendanceExceptions: DashboardAttendanceException[];
};

export type CommandFinancialActivity = {
  id: string;
  transactionNo: number;
  date: string;
  type: string;
  amount: MoneyValue;
  description: string;
  counterpartyName?: string;
  employeeName?: string;
  productionTitle?: string;
  status: string;
};

export type CommandQuickAction = {
  id: string;
  label: string;
  icon: string;
  route: string;
};

export type CommandDashboardView = {
  date: string;
  today: CommandToday;
  money: CommandMoney;
  attention: CommandAttentionItem[];
  operations: CommandOperations;
  recentFinancialActivity: CommandFinancialActivity[];
  quickActions: CommandQuickAction[];
};

export const commandApi = {
  dashboard: (date?: string) =>
    api<CommandDashboardView>(
      `/command/dashboard${date ? `?date=${encodeURIComponent(date)}` : ""}`,
    ),
};
