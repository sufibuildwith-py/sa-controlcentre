export type EmployeeStatus='ACTIVE'|'ON_LEAVE'|'INACTIVE';
export type AttendanceStatus='PRESENT'|'ABSENT'|'LATE'|'HALF_DAY'|'LEAVE'|'HOLIDAY';
export interface Owner {id:string;email:string;displayName:string;role:'OWNER'}
export interface Employee {id:string;employeeCode:string;firstName:string;lastName?:string|null;displayName:string;phone:string;whatsappPhone?:string|null;email?:string|null;roleTitle:string;department:string;employmentType:string;joiningDate:string;baseSalaryMinor:number;salaryCurrency:string;status:EmployeeStatus;profilePhotoUrl?:string|null;notes?:string|null;createdAt:string;updatedAt:string}
export type EmployeeInput=Omit<Employee,'id'|'createdAt'|'updatedAt'>;
export interface AttendanceRecord {id:string;employeeId:string;date:string;status:AttendanceStatus;checkInTime?:string|null;checkOutTime?:string|null;minutesLate:number;notes?:string|null;updatedAt:string}
export interface AttendanceDay {date:string;rows:Array<{employee:Employee;record:AttendanceRecord|null}>;summary:Record<AttendanceStatus,number>}
export interface AttendanceMonth {employeeId:string;from:string;to:string;records:AttendanceRecord[];summary:Record<AttendanceStatus,number>}
export type LeaveStatus='PENDING'|'APPROVED'|'REJECTED'|'CANCELLED';
export interface LeaveRequest {id:string;employeeId:string;employeeName:string;startDate:string;endDate:string;leaveType:string;reason:string;status:LeaveStatus;ownerNote?:string|null;createdAt:string;resolvedAt?:string|null}

