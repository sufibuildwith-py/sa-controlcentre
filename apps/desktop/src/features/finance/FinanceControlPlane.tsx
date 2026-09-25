import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RefreshCw, ShieldCheck, TriangleAlert } from "lucide-react";
import { Link } from "react-router-dom";
import {
  EmptyState,
  MetricCard,
  SAButton,
  SABentoCard,
  SABentoGrid,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
import { financeApi } from "./finance.api";

const money = (value: number | null | undefined) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(Number(value ?? 0));

const signedMoney = (value: number | null | undefined) => {
  const numeric = Number(value ?? 0);
  return `${numeric >= 0 ? "+" : "−"}${money(Math.abs(numeric))}`;
};

export function FinanceControlPlane() {
  const client = useQueryClient();
  const overview = useQuery({
    queryKey: ["finance", "overview"],
    queryFn: financeApi.overview,
  });
  const reconciliation = useQuery({
    queryKey: ["finance", "reconciliation"],
    queryFn: financeApi.reconciliation,
  });
  const rebuild = useMutation({
    mutationFn: financeApi.rebuild,
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["finance", "overview"] });
      client.invalidateQueries({ queryKey: ["finance", "reconciliation"] });
    },
  });

  if (overview.isPending || reconciliation.isPending) return <SkeletonCard />;

  if (overview.isError || reconciliation.isError || !overview.data || !reconciliation.data) {
    return (
      <SABentoCard className="finance-control-plane" aria-label="Financial control plane">
        <EmptyState
          title="Financial control unavailable"
          description="The owner control surface could not load the current finance position."
          action={
            <SAButton
              onClick={() => {
                void overview.refetch();
                void reconciliation.refetch();
              }}
            >
              Retry
            </SAButton>
          }
        />
      </SABentoCard>
    );
  }

  const data = reconciliation.data;
  const isReconciled = data.status === "RECONCILED";
  const tone = data.status === "RECONCILED" ? "success" : "warning";

  return (
    <section className="finance-control-plane" aria-label="Financial control plane">
      <div className="finance-section-head">
        <div>
          <span className="eyebrow">Owner control surface</span>
          <h2>Financial Control</h2>
          <p>Current position, outstanding obligations and accounting integrity.</p>
        </div>
        <div className="finance-actions">
          <StatusBadge tone={tone}>{data.status}</StatusBadge>
          <SAButton
            size="sm"
            disabled={rebuild.isPending}
            onClick={() => rebuild.mutate()}
          >
            <RefreshCw size={14} />
            {rebuild.isPending ? "Rebuilding…" : "Rebuild positions"}
          </SAButton>
          <Link className="sa-button sa-button--secondary sa-button--sm" to="/finance?tab=RECONCILIATION">
            Open reconciliation →
          </Link>
        </div>
      </div>

      <SABentoGrid className="finance-metrics">
        <MetricCard
          label="Overall realized result"
          value={signedMoney(overview.data.overallResult)}
          detail="Received less incurred expense"
        />
        <MetricCard
          label="Contracted"
          value={money(overview.data.contracted)}
          detail="Recorded production commitments"
        />
        <MetricCard
          label="Received"
          value={money(overview.data.received)}
          detail="Posted cash receipts"
        />
        <MetricCard
          label="Incurred expense"
          value={money(overview.data.incurredExpense)}
          detail="Posted expense"
        />
      </SABentoGrid>

      <div className="finance-two">
        <SABentoCard>
          <span className="eyebrow">Owner positions</span>
          <div className="finance-kpis">
            <div>
              <span>Azeem · AZ-2</span>
              <strong className={data.azeemPosition < 0 ? "finance-negative" : ""}>
                {signedMoney(data.azeemPosition)}
              </strong>
            </div>
            <div>
              <span>Akash · AK-2</span>
              <strong className={data.akashPosition < 0 ? "finance-negative" : ""}>
                {signedMoney(data.akashPosition)}
              </strong>
            </div>
          </div>
          <small>Signed account positions. A negative position is shown as a position, not automatically classified as a debt.</small>
          <Link to="/finance?owner=AZ">Open owner history →</Link>
        </SABentoCard>

        <SABentoCard>
          <span className="eyebrow">Outstanding</span>
          <div className="finance-kpis">
            <div>
              <span>Customer receivables</span>
              <strong>{money(data.receivables)}</strong>
            </div>
            <div>
              <span>Employee payables</span>
              <strong>{money(data.employeePayables)}</strong>
            </div>
            <div>
              <span>Invoice receivables</span>
              <strong>{money(data.invoiceReceivables)}</strong>
            </div>
            <div>
              <span>Equipment payables</span>
              <strong>{money(data.equipmentPayables)}</strong>
            </div>
          </div>
        </SABentoCard>
      </div>

      <SABentoCard>
        <div className="finance-section-head">
          <div>
            <span className="eyebrow">Control check</span>
            <h2>{isReconciled ? <ShieldCheck size={18} /> : <TriangleAlert size={18} />} Journal integrity</h2>
          </div>
          <strong className={data.controlDifference === 0 ? "" : "finance-negative"}>
            Difference {signedMoney(data.controlDifference)}
          </strong>
        </div>
        <div className="finance-kpis">
          <div>
            <span>Control difference</span>
            <strong>{money(data.controlDifference)}</strong>
          </div>
          <div>
            <span>Migration open issues</span>
            <strong>{data.migrationOpenCount ?? 0}</strong>
          </div>
          <div>
            <span>Posted records</span>
            <strong>{overview.data.postedCount}</strong>
          </div>
          <div>
            <span>Production receivables</span>
            <strong>{money(data.receivables)}</strong>
          </div>
        </div>
        {!isReconciled && (
          <p role="alert">
            Finance controls need review before treating the projection as fully reconciled.
            Open the reconciliation workspace for the detailed evidence.
          </p>
        )}
        {rebuild.error && (
          <p role="alert" className="finance-error">
            {rebuild.error.message}
          </p>
        )}
      </SABentoCard>
    </section>
  );
}
