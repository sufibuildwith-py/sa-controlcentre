import { type PropsWithChildren } from "react";
import { Navigate } from "react-router-dom";
import { useFinanceAccess } from "./financeAccess.api";
import { SkeletonCard } from "../../components/ui/sa";

export function FinanceRouteGuard({ children }: PropsWithChildren) {
  const access = useFinanceAccess();

  if (access.isPending) {
    return (
      <div style={{ padding: "40px", maxWidth: "600px", margin: "0 auto" }}>
        <SkeletonCard />
      </div>
    );
  }

  if (!access.data?.unlocked) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}
