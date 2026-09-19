export type EmployeeStatus = "ACTIVE" | "ON_LEAVE" | "INACTIVE";
export type AttendanceStatus =
  | "PRESENT"
  | "ABSENT"
  | "LATE"
  | "HALF_DAY"
  | "LEAVE"
  | "HOLIDAY";
export interface Owner {
  id: string;
  email: string;
  displayName: string;
  role: "OWNER";
}
export interface Employee {
  id: string;
  employeeCode: string;
  firstName: string;
  lastName?: string | null;
  displayName: string;
  phone: string;
  whatsappPhone?: string | null;
  email?: string | null;
  roleTitle: string;
  department: string;
  employmentType: string;
  joiningDate: string;
  baseSalaryMinor: number;
  salaryCurrency: string;
  status: EmployeeStatus;
  profilePhotoUrl?: string | null;
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
}
export type EmployeeInput = Omit<Employee, "id" | "createdAt" | "updatedAt">;
export interface AttendanceRecord {
  id: string;
  employeeId: string;
  date: string;
  status: AttendanceStatus;
  checkInTime?: string | null;
  checkOutTime?: string | null;
  minutesLate: number;
  notes?: string | null;
  updatedAt: string;
}
export interface AttendanceDay {
  date: string;
  rows: Array<{ employee: Employee; record: AttendanceRecord | null }>;
  summary: Record<AttendanceStatus, number>;
}
export interface AttendanceMonth {
  employeeId: string;
  from: string;
  to: string;
  records: AttendanceRecord[];
  summary: Record<AttendanceStatus, number>;
}
export type LeaveStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
export interface LeaveRequest {
  id: string;
  employeeId: string;
  employeeName: string;
  startDate: string;
  endDate: string;
  leaveType: string;
  reason: string;
  status: LeaveStatus;
  ownerNote?: string | null;
  createdAt: string;
  resolvedAt?: string | null;
}
export type ProductionStatus =
  | "DRAFT"
  | "PLANNING"
  | "PRE_PRODUCTION"
  | "PRODUCTION"
  | "POST_PRODUCTION"
  | "REVIEW"
  | "DELIVERED"
  | "CANCELLED";
export type Priority = "LOW" | "NORMAL" | "HIGH" | "URGENT";
export interface ProductionMember {
  id: string;
  employeeId: string;
  employeeName: string;
  productionRole: string;
  attendanceRequired: boolean;
  assignmentStatus: "PENDING" | "CONFIRMED" | "DECLINED";
  conflictOverridden: boolean;
  overrideReason?: string | null;
}
export interface Production {
  id: string;
  title: string;
  clientName: string;
  description?: string | null;
  eventDate: string;
  startTime: string;
  endTime: string;
  venueName: string;
  venueAddress?: string | null;
  status: ProductionStatus;
  priority: Priority;
  progressPercent: number;
  completedAt?: string | null;
  members: ProductionMember[];
  unfinishedTaskCount: number;
  createdAt: string;
  updatedAt: string;
}
export type TaskStatus =
  | "TODO"
  | "IN_PROGRESS"
  | "BLOCKED"
  | "DONE"
  | "CANCELLED";
export interface TaskUpdate {
  id: string;
  progressPercent: number;
  note?: string | null;
  createdAt: string;
}
export interface WorkTask {
  id: string;
  productionId?: string | null;
  productionTitle?: string | null;
  meetingOriginId?: string | null;
  title: string;
  description?: string | null;
  assignedEmployeeId?: string | null;
  assigneeName?: string | null;
  status: TaskStatus;
  priority: Priority;
  startDate?: string | null;
  dueAt?: string | null;
  completedAt?: string | null;
  progressPercent: number;
  overdue: boolean;
  updates: TaskUpdate[];
  createdAt: string;
  updatedAt: string;
}
export type CalendarEventType =
  | "PRODUCTION"
  | "SHOOT"
  | "MEETING"
  | "DEADLINE"
  | "INTERNAL"
  | "REMINDER";
export interface CalendarEvent {
  id: string;
  type: CalendarEventType;
  title: string;
  description?: string | null;
  startsAt: string;
  endsAt: string;
  locationName?: string | null;
  locationAddress?: string | null;
  productionId?: string | null;
  meetingId?: string | null;
  taskId?: string | null;
  status: "SCHEDULED" | "CANCELLED" | "COMPLETED";
  attendees: Array<{
    employeeId: string;
    employeeName: string;
    response: string;
  }>;
}
export interface Meeting {
  id: string;
  title: string;
  description?: string | null;
  agenda?: string | null;
  startsAt: string;
  endsAt: string;
  location?: string | null;
  status: "SCHEDULED" | "COMPLETED" | "CANCELLED";
  attendees: Array<{
    employeeId: string;
    employeeName: string;
    response: string;
  }>;
  notes: Array<{
    id: string;
    content: string;
    createdAt: string;
    updatedAt: string;
  }>;
  createdAt: string;
  updatedAt: string;
}
export type PayrollStatus =
  | "DRAFT"
  | "CALCULATED"
  | "APPROVED"
  | "PAID"
  | "LOCKED";
export interface PayrollAdjustment {
  id: string;
  type: "BONUS" | "DEDUCTION" | "OVERTIME" | "ADVANCE" | "CORRECTION" | "OTHER";
  amountMinor: number;
  reason: string;
  createdAt: string;
}
export interface PayrollItem {
  id: string;
  employeeId: string;
  employeeName: string;
  salaryCurrency: string;
  baseSalaryMinor: number;
  attendanceDeductionMinor: number;
  overtimeMinor: number;
  bonusMinor: number;
  advanceDeductionMinor: number;
  manualAdjustmentMinor: number;
  netSalaryMinor: number;
  paymentStatus: "PENDING" | "PAID";
  paidAt?: string | null;
  adjustments: PayrollAdjustment[];
}
export interface PayrollPeriod {
  id: string;
  year: number;
  month: number;
  status: PayrollStatus;
  policy: string;
  totalMinor: number;
  paidMinor: number;
  pendingMinor: number;
  items: PayrollItem[];
  calculatedAt?: string | null;
  approvedAt?: string | null;
  paidAt?: string | null;
  lockedAt?: string | null;
}
export interface EmployeeOperations {
  work: WorkTask[];
  payroll: PayrollPeriod[];
  performance: {
    attendanceRecords: number;
    attended: number;
    lateCount: number;
    tasksAssigned: number;
    tasksCompleted: number;
    overdueTasks: number;
    activeProductions: number;
    completedProductions: number;
    attendanceRate?: number | null;
    onTimeCompletionRate?: number | null;
  };
}
export interface Dashboard {
  team: {
    employees: number;
    present: number;
    late: number;
    absent: number;
    leave: number;
    incomplete: number;
  };
  today: Array<{
    id: string;
    type: string;
    title: string;
    startsAt: string;
    endsAt: string;
  }>;
  productions: Array<{
    id: string;
    title: string;
    status: string;
    progressPercent: number;
    eventDate: string;
  }>;
  workload: Array<{
    employeeId: string;
    employeeName: string;
    active: number;
    overdue: number;
  }>;
  payroll?: {
    id: string;
    year: number;
    month: number;
    status: string;
    totalMinor: number;
    paidMinor: number;
    pendingMinor: number;
  } | null;
  attention: Array<{
    code: string;
    title: string;
    detail: string;
    tone: "neutral" | "warning" | "danger";
    href: string;
  }>;
  communicationsAvailable: boolean;
}
