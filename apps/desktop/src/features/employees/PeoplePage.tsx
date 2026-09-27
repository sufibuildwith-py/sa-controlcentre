import { useQuery } from "@tanstack/react-query";
import { ArrowUpRight, Plus, Search } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useUiStore } from "../../app/store/ui";
import {
  EmptyState,
  SAButton,
  SABentoCard,
  SABentoGrid,
  SASegmentedControl,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
import { api } from "../../lib/api";
import type { Employee, EmployeeStatus, PeopleSummary } from "../../types/domain";

type Filter = "ALL" | EmployeeStatus;

export function PeoplePage() {
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<Filter>("ACTIVE");
  const openEmployee = useUiStore((s) => s.openEmployeeForm);
  const navigate = useNavigate();

  const summary = useQuery({
    queryKey: ["employees", "summary"],
    queryFn: () => api<PeopleSummary>("/employees/summary"),
  });

  const employees = useQuery({
    queryKey: ["employees", search, filter],
    queryFn: () =>
      api<Employee[]>(
        `/employees?${new URLSearchParams({
          ...(search && { search }),
          ...(filter !== "ALL" && { status: filter }),
        })}`,
      ),
  });

  return (
    <>
      <div className="page-title">
        <div>
          <h1>People</h1>
          <p>Your workforce, assignments and operating state.</p>
        </div>
        <SAButton variant="primary" onClick={() => openEmployee()}>
          <Plus size={16} />
          Employee
        </SAButton>
      </div>

      <div className="people-summary-strip">
        <div className="summary-pill">
          <span className="summary-label">Active</span>
          <strong className="summary-value">{summary.data?.activeEmployees ?? "—"}</strong>
        </div>
        <div className="summary-pill">
          <span className="summary-label">On Leave</span>
          <strong className="summary-value">{summary.data?.onLeaveEmployees ?? "—"}</strong>
        </div>
        <div className="summary-pill">
          <span className="summary-label">Attendance today</span>
          <strong className="summary-value">{summary.data?.attendanceToday ?? "—"}</strong>
        </div>
        <div className="summary-pill">
          <span className="summary-label">Open tasks</span>
          <strong className="summary-value">{summary.data?.openTasks ?? "—"}</strong>
        </div>
        <div
          className={`summary-pill ${(summary.data?.overdueTasks ?? 0) > 0 ? "summary-pill--alert" : ""}`}
        >
          <span className="summary-label">Overdue tasks</span>
          <strong className="summary-value">{summary.data?.overdueTasks ?? "—"}</strong>
        </div>
      </div>

      <div className="people-toolbar">
        <label>
          <Search size={15} />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search people"
            aria-label="Search people"
          />
        </label>
        <SASegmentedControl
          value={filter}
          onChange={setFilter}
          label="Employee status"
          items={[
            { value: "ALL", label: "All" },
            { value: "ACTIVE", label: "Active" },
            { value: "ON_LEAVE", label: "Leave" },
            { value: "INACTIVE", label: "Inactive" },
          ]}
        />
      </div>

      {employees.isPending ? (
        <SABentoGrid className="people-grid">
          {Array.from({ length: 8 }, (_, i) => (
            <SkeletonCard key={i} />
          ))}
        </SABentoGrid>
      ) : employees.isError ? (
        <EmptyState
          title="People could not be loaded"
          description="Check the local API connection, then try again."
          action={
            <SAButton onClick={() => employees.refetch()}>Try again</SAButton>
          }
        />
      ) : !employees.data?.length ? (
        <EmptyState
          title="No employees found"
          description={
            search
              ? "No person matches this search and status."
              : "Add your first employee to begin managing attendance and company operations."
          }
          action={
            !search ? (
              <SAButton variant="primary" onClick={() => openEmployee()}>
                Add employee
              </SAButton>
            ) : undefined
          }
        />
      ) : (
        <SABentoGrid className="people-grid">
          {employees.data.map((e, index) => (
            <EmployeeCard
              key={e.id}
              employee={e}
              index={index}
              onClick={() => navigate(`/people/${e.id}`)}
            />
          ))}
        </SABentoGrid>
      )}
    </>
  );
}

function EmployeeCard({
  employee,
  index,
  onClick,
}: {
  employee: Employee;
  index: number;
  onClick: () => void;
}) {
  return (
    <SABentoCard
      interactive
      className={
        index % 7 === 0 ? "employee-card employee-card--wide" : "employee-card"
      }
      onClick={onClick}
      onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && onClick()}
      role="button"
      tabIndex={0}
    >
      <div className="employee-card__top">
        <div className="employee-avatar">{initials(employee.displayName)}</div>
        <div className="employee-card__badges">
          {employee.todayAttendance && (
            <span
              className={`attendance-chip attendance-chip--${employee.todayAttendance.toLowerCase()}`}
            >
              {employee.todayAttendance.replace("_", " ")}
            </span>
          )}
          <StatusBadge
            tone={
              employee.status === "ACTIVE"
                ? "success"
                : employee.status === "ON_LEAVE"
                  ? "warning"
                  : "neutral"
            }
          >
            {employee.status === "ON_LEAVE"
              ? "On leave"
              : capitalize(employee.status)}
          </StatusBadge>
        </div>
      </div>
      <div className="employee-card__name">
        <h2>{employee.displayName}</h2>
        <p>{employee.roleTitle}</p>
      </div>
      <div className="employee-stats">
        <div>
          <span>Department</span>
          <strong style={{ fontSize: "13px", fontWeight: 500, marginTop: "4px" }}>
            {employee.department}
          </strong>
        </div>
        <div>
          <span>Active work</span>
          <strong style={{ fontSize: "16px", marginTop: "3px" }}>
            {employee.activeTasksCount ?? 0} {employee.activeTasksCount === 1 ? "task" : "tasks"}
          </strong>
        </div>
      </div>
      <footer>
        <span>
          {employee.activeProductionsCount && employee.activeProductionsCount > 0
            ? `${employee.activeProductionsCount} active ${employee.activeProductionsCount === 1 ? "production" : "productions"}`
            : employee.employeeCode}
        </span>
        <ArrowUpRight size={15} />
      </footer>
    </SABentoCard>
  );
}

export const initials = (name: string) =>
  name
    .split(/\s+/)
    .map((x) => x[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

const capitalize = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
