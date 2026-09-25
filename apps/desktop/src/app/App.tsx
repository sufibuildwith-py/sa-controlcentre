import { useQuery } from "@tanstack/react-query";
import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "../components/layout/AppShell";
import { SkeletonCard, EmptyState, SAButton } from "../components/ui/sa";
import { api } from "../lib/api";
import type { Owner } from "../types/domain";
import { LoginPage } from "../features/auth/LoginPage";
import { OwnerSetupPage } from "../features/auth/OwnerSetupPage";
import { CommandPage } from "../features/command/CommandPage";
import { PeoplePage } from "../features/employees/PeoplePage";
import { EmployeeDetailPage } from "../features/employees/EmployeeDetailPage";
import { AttendancePage } from "../features/attendance/AttendancePage";
import { SettingsPage } from "../features/settings/SettingsPage";
import { ProductionsPage } from "../features/productions/ProductionsPage";
import { WorkPage } from "../features/work/WorkPage";
import {
  MeetingsPage,
  MeetingDetailPage,
} from "../features/meetings/MeetingsPage";
const ProductionDetailPage = lazy(() =>
    import("../features/productions/ProductionDetailPage").then((m) => ({
      default: m.ProductionDetailPage,
    })),
  ),
  CalendarPage = lazy(() =>
    import("../features/calendar/CalendarPage").then((m) => ({
      default: m.CalendarPage,
    })),
  ),
  PayrollPage = lazy(() =>
    import("../features/payroll/PayrollPage").then((m) => ({
      default: m.PayrollPage,
    })),
  ),
  CommunicationsPage = lazy(() =>
    import("../features/communications/CommunicationsPage").then((m) => ({
      default: m.CommunicationsPage,
    })),
  ),
  HeadquartersPage = lazy(() =>
    import("../features/headquarters/HeadquartersPage").then((m) => ({
      default: m.HeadquartersPage,
    })),
  ),
  FinancePage = lazy(() =>
    import("../features/finance/FinancePage").then((m) => ({
      default: m.FinancePage,
    })),
  ),
  NavigatorPage =
    import.meta.env.VITE_NAVIGATOR_ENABLED === "true"
      ? lazy(() =>
          import("../features/navigator/NavigatorPage").then((m) => ({
            default: m.NavigatorPage,
          })),
        )
      : null;
export function App() {
  const setup = useQuery({
    queryKey: ["owner-setup"],
    queryFn: () => api<{ required: boolean }>("/owner-setup"),
    enabled: import.meta.env.VITE_DESKTOP_RELEASE === "true",
    retry: false,
  });
  const me = useQuery({
    queryKey: ["auth", "me"],
    queryFn: () => api<Owner>("/auth/me"),
    retry: false,
  });
  if (import.meta.env.VITE_DESKTOP_RELEASE === "true" && setup.isError)
    return (
      <main className="login-page">
        <EmptyState
          title="Your workspace could not open"
          description={String(setup.error.message ?? setup.error)}
          action={
            <SAButton
              onClick={() => {
                setup.refetch();
                me.refetch();
              }}
            >
              Try again
            </SAButton>
          }
        />
      </main>
    );
  if (
    me.isPending ||
    (import.meta.env.VITE_DESKTOP_RELEASE === "true" && setup.isPending)
  )
    return (
      <div className="auth-loading">
        <SkeletonCard />
      </div>
    );
  if (setup.data?.required) return <OwnerSetupPage />;
  if (me.isError) return <LoginPage />;
  return (
    <AppShell>
      <Suspense fallback={<SkeletonCard />}>
        <Routes>
          <Route path="/" element={<CommandPage />} />
          <Route path="/people" element={<PeoplePage />} />
          <Route path="/people/:id" element={<EmployeeDetailPage />} />
          <Route path="/attendance" element={<AttendancePage />} />
          <Route path="/productions" element={<ProductionsPage />} />
          <Route path="/productions/:id" element={<ProductionDetailPage />} />
          <Route path="/headquarters" element={<HeadquartersPage />} />
          <Route path="/finance" element={<FinancePage />} />
          {NavigatorPage && (
            <Route path="/navigator" element={<NavigatorPage />} />
          )}
          <Route path="/work" element={<WorkPage />} />
          <Route path="/calendar" element={<CalendarPage />} />
          <Route path="/meetings" element={<MeetingsPage />} />
          <Route path="/meetings/:id" element={<MeetingDetailPage />} />
          <Route path="/payroll" element={<PayrollPage />} />
          <Route path="/payroll/:id" element={<PayrollPage />} />
          <Route path="/communications" element={<CommunicationsPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </AppShell>
  );
}
