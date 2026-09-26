import React, { useState, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import {
  X,
  FileText,
  Receipt,
  Calendar,
  Clock,
  ArrowUpRight,
  AlertCircle,
  Building,
} from "lucide-react";
import { SAButton } from "../../components/ui/sa";
import { financeApi, Party360View } from "./finance.api";

interface Party360DrawerProps {
  partyId: string | null;
  onClose: () => void;
}

const generateKey = () =>
  typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

const rupees = (n: number | undefined | null) =>
  `₹${Number(n ?? 0).toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;

export const Party360Drawer: React.FC<Party360DrawerProps> = ({
  partyId,
  onClose,
}) => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [activeTab, setActiveTab] = useState<
    "TIMELINE" | "INVOICES" | "CHARGES" | "PRODUCTIONS"
  >("TIMELINE");

  // Settlement modal state
  const [settlementOpen, setSettlementOpen] = useState(false);
  const [settlementKey, setSettlementKey] = useState<string>(generateKey);
  const [targetType, setTargetType] = useState<"INVOICE" | "CHARGE">("INVOICE");
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<string>("");
  const [payerAccount, setPayerAccount] = useState<string>("");
  const [amount, setAmount] = useState<string>("");
  const [date, setDate] = useState<string>(() =>
    new Date().toISOString().slice(0, 10),
  );
  const [description, setDescription] = useState<string>("");
  const [error, setError] = useState<string>("");

  const query = useQuery({
    queryKey: ["finance", "party-360", partyId],
    queryFn: () => financeApi.party360(partyId!),
    enabled: !!partyId,
  });

  const unpaidInvoices = useMemo(() => {
    return (query.data?.invoices ?? []).filter((i) => i.outstanding > 0);
  }, [query.data?.invoices]);

  const openSettlementForInvoice = (invoiceId: string, outstanding: number) => {
    setSettlementKey(generateKey());
    setTargetType("INVOICE");
    setSelectedInvoiceId(invoiceId);
    setAmount(outstanding.toFixed(2));
    setDate(new Date().toISOString().slice(0, 10));
    setDescription(`Invoice payment for ${query.data?.party.displayName}`);
    setPayerAccount("");
    setError("");
    setSettlementOpen(true);
  };

  const openGeneralSettlement = () => {
    setSettlementKey(generateKey());
    if (unpaidInvoices.length > 0) {
      setTargetType("INVOICE");
      setSelectedInvoiceId(unpaidInvoices[0].id);
      setAmount(unpaidInvoices[0].outstanding.toFixed(2));
    } else {
      setTargetType("CHARGE");
      setSelectedInvoiceId("");
      const chargeBal = query.data?.chargeOutstanding ?? 0;
      setAmount(chargeBal > 0 ? chargeBal.toFixed(2) : "");
    }
    setDate(new Date().toISOString().slice(0, 10));
    setDescription(`Settlement from ${query.data?.party.displayName}`);
    setPayerAccount("");
    setError("");
    setSettlementOpen(true);
  };

  const settlementMutation = useMutation({
    mutationFn: async () => {
      const numAmount = Number(amount);
      if (isNaN(numAmount) || numAmount <= 0) {
        throw new Error("Enter a valid positive amount.");
      }
      if (!payerAccount || !["AZ-2", "AK-2"].includes(payerAccount)) {
        throw new Error("Explicit owner account (AZ-2 or AK-2) is required.");
      }

      if (targetType === "INVOICE") {
        if (!selectedInvoiceId) {
          throw new Error("Select an invoice to settle.");
        }
        return financeApi.recordInvoicePayment({
          idempotencyKey: settlementKey,
          invoiceId: selectedInvoiceId,
          amount: numAmount,
          date,
          description:
            description.trim() || `Invoice payment - ${payerAccount}`,
          receiverAccount: payerAccount,
        });
      } else {
        return financeApi.recordPartyReceipt({
          idempotencyKey: settlementKey,
          counterpartyId: partyId!,
          amount: numAmount,
          date,
          description: description.trim() || `Charge receipt - ${payerAccount}`,
          receiverAccount: payerAccount,
        });
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["finance", "party-360", partyId],
      });
      queryClient.invalidateQueries({ queryKey: ["finance", "parties"] });
      queryClient.invalidateQueries({ queryKey: ["finance", "invoices"] });
      queryClient.invalidateQueries({ queryKey: ["billing"] });
      setSettlementOpen(false);
    },
    onError: (err: any) => {
      setError(err?.message || "Failed to post canonical settlement.");
    },
  });

  const handleSettlementSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (settlementMutation.isPending) return;
    setError("");
    settlementMutation.mutate();
  };

  if (!partyId) return null;

  const data: Party360View | undefined = query.data;

  return (
    <div
      className="party-360-backdrop"
      onClick={onClose}
      data-testid="party-360-backdrop"
    >
      <div
        className="party-360-drawer"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Party 360"
        data-testid="party-360-drawer"
      >
        {query.isPending ? (
          <div style={{ padding: "40px", textAlign: "center" }}>
            <p>Loading customer profile...</p>
          </div>
        ) : query.isError || !data ? (
          <div style={{ padding: "40px", textAlign: "center" }}>
            <AlertCircle
              size={32}
              color="var(--danger)"
              style={{ margin: "0 auto 12px" }}
            />
            <h3>Unable to load customer</h3>
            <p style={{ color: "var(--text-3)", fontSize: "13px" }}>
              {query.error instanceof Error
                ? query.error.message
                : "Party ledger unavailable."}
            </p>
            <SAButton onClick={onClose} style={{ marginTop: "16px" }}>
              Close
            </SAButton>
          </div>
        ) : (
          <>
            <header className="party-360-header">
              <div className="party-360-title-area">
                <div
                  style={{ display: "flex", alignItems: "center", gap: "10px" }}
                >
                  <h2>{data.party.displayName}</h2>
                  <span className="party-360-badge">{data.party.role}</span>
                  {data.party.active ? (
                    <span
                      className="party-360-badge"
                      style={{
                        background: "rgba(16, 185, 129, 0.1)",
                        color: "#10b981",
                        borderColor: "rgba(16, 185, 129, 0.2)",
                      }}
                    >
                      Active
                    </span>
                  ) : (
                    <span
                      className="party-360-badge"
                      style={{ color: "var(--text-3)" }}
                    >
                      Inactive
                    </span>
                  )}
                </div>
                <div className="party-360-meta">
                  {data.party.legalName && (
                    <span>Legal: {data.party.legalName}</span>
                  )}
                  {data.party.gstin && <span>GSTIN: {data.party.gstin}</span>}
                  {data.party.notes && <span>· {data.party.notes}</span>}
                </div>
              </div>

              <div className="party-360-header-actions">
                <SAButton
                  size="sm"
                  onClick={() => {
                    onClose();
                    navigate(`/billing?counterpartyId=${partyId}`);
                  }}
                  data-testid="create-bill-btn"
                >
                  <ArrowUpRight size={14} style={{ marginRight: "4px" }} />
                  Create Bill
                </SAButton>
                <SAButton
                  variant="primary"
                  size="sm"
                  onClick={openGeneralSettlement}
                  data-testid="record-settlement-btn"
                >
                  <Receipt size={14} style={{ marginRight: "4px" }} />
                  Record Settlement
                </SAButton>
                <button
                  type="button"
                  onClick={onClose}
                  style={{
                    background: "transparent",
                    border: "none",
                    color: "var(--text-3)",
                    cursor: "pointer",
                    padding: "4px",
                    display: "flex",
                  }}
                  aria-label="Close drawer"
                >
                  <X size={20} />
                </button>
              </div>
            </header>

            <div className="party-360-body">
              {/* Dual-Track Overview Bento Grid */}
              <div className="party-360-kpis">
                <div className="party-360-card primary">
                  <span className="label">Total Outstanding</span>
                  <strong className="value" data-testid="total-outstanding">
                    {rupees(data.totalOutstanding)}
                  </strong>
                  <small className="hint">
                    Formal Invoices: {rupees(data.invoiceOutstanding)} · Direct
                    Charges: {rupees(data.chargeOutstanding)}
                  </small>
                </div>

                <div className="party-360-card">
                  <span className="label">Total Received</span>
                  <strong className="value" data-testid="total-received">
                    {rupees(data.totalReceived)}
                  </strong>
                  <small className="hint">
                    Invoice Paid: {rupees(data.invoicePaid)} · Charge Paid:{" "}
                    {rupees(data.chargeReceived)}
                  </small>
                </div>

                <div className="party-360-card">
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                    }}
                  >
                    <span className="label">Formal Invoices</span>
                    <span className="party-360-badge track-invoice">
                      Track 1
                    </span>
                  </div>
                  <strong className="value">{rupees(data.invoiceTotal)}</strong>
                  <small className="hint">
                    {data.invoices.length} posted invoices · Remaining:{" "}
                    {rupees(data.invoiceOutstanding)}
                  </small>
                </div>

                <div className="party-360-card">
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                    }}
                  >
                    <span className="label">Direct Charges</span>
                    <span className="party-360-badge track-charge">
                      Track 2
                    </span>
                  </div>
                  <strong className="value">{rupees(data.chargeTotal)}</strong>
                  <small className="hint">
                    {data.charges.length} direct charges · Remaining:{" "}
                    {rupees(data.chargeOutstanding)}
                  </small>
                </div>
              </div>

              {/* Sub-navigation tabs */}
              <div className="party-360-tabs" role="tablist">
                <button
                  type="button"
                  className={`party-360-tab ${activeTab === "TIMELINE" ? "active" : ""}`}
                  onClick={() => setActiveTab("TIMELINE")}
                  data-testid="tab-timeline"
                >
                  Financial Timeline ({data.timeline.length})
                </button>
                <button
                  type="button"
                  className={`party-360-tab ${activeTab === "INVOICES" ? "active" : ""}`}
                  onClick={() => setActiveTab("INVOICES")}
                  data-testid="tab-invoices"
                >
                  Invoices & Bills ({data.invoices.length})
                </button>
                <button
                  type="button"
                  className={`party-360-tab ${activeTab === "CHARGES" ? "active" : ""}`}
                  onClick={() => setActiveTab("CHARGES")}
                  data-testid="tab-charges"
                >
                  Direct Charges ({data.charges.length})
                </button>
                <button
                  type="button"
                  className={`party-360-tab ${activeTab === "PRODUCTIONS" ? "active" : ""}`}
                  onClick={() => setActiveTab("PRODUCTIONS")}
                  data-testid="tab-productions"
                >
                  Productions ({data.productions.length})
                </button>
              </div>

              {/* Tab 1: Financial Timeline */}
              {activeTab === "TIMELINE" && (
                <div
                  style={{ display: "grid", gap: "10px" }}
                  data-testid="timeline-pane"
                >
                  {data.timeline.length === 0 ? (
                    <div
                      style={{
                        padding: "30px",
                        textAlign: "center",
                        color: "var(--text-3)",
                      }}
                    >
                      No financial transactions recorded for this party.
                    </div>
                  ) : (
                    data.timeline.map((tx) => (
                      <div key={tx.id} className="production-timeline-item">
                        <div className="production-timeline-left">
                          <div className="production-timeline-date">
                            {tx.date}
                          </div>
                          <div className="production-timeline-content">
                            <div
                              style={{
                                display: "flex",
                                alignItems: "center",
                                gap: "6px",
                              }}
                            >
                              <strong>{tx.description}</strong>
                              <span
                                className={`party-360-badge ${
                                  tx.track === "FORMAL_INVOICE"
                                    ? "track-invoice"
                                    : "track-charge"
                                }`}
                              >
                                {tx.type.replace(/_/g, " ")}
                              </span>
                            </div>
                            <span>
                              #{tx.transactionNo} · {tx.status}
                              {tx.ownerAccount
                                ? ` · Owner: ${tx.ownerAccount}`
                                : ""}
                              {tx.productionTitle
                                ? ` · Job: ${tx.productionTitle}`
                                : ""}
                            </span>
                          </div>
                        </div>
                        <div className="production-timeline-right">
                          <div
                            className={`production-timeline-amount ${
                              tx.type.includes("RECEIPT") ||
                              tx.type.includes("PAYMENT")
                                ? "positive"
                                : "negative"
                            }`}
                          >
                            {rupees(tx.amount)}
                          </div>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              )}

              {/* Tab 2: Formal Invoices */}
              {activeTab === "INVOICES" && (
                <div
                  style={{ display: "grid", gap: "10px" }}
                  data-testid="invoices-pane"
                >
                  {data.invoices.length === 0 ? (
                    <div
                      style={{
                        padding: "30px",
                        textAlign: "center",
                        color: "var(--text-3)",
                      }}
                    >
                      No formal invoices issued for this customer.
                    </div>
                  ) : (
                    data.invoices.map((inv) => (
                      <div key={inv.id} className="party-360-item">
                        <div className="party-360-item-info">
                          <div
                            style={{
                              display: "flex",
                              alignItems: "center",
                              gap: "8px",
                            }}
                          >
                            <strong>{inv.invoiceNumber}</strong>
                            <span className="party-360-badge track-invoice">
                              {inv.taxMode}
                            </span>
                            {inv.billStatus && (
                              <span
                                className="party-360-badge"
                                style={{
                                  background:
                                    inv.billStatus === "PAID"
                                      ? "rgba(16,185,129,0.1)"
                                      : "rgba(245,158,11,0.1)",
                                  color:
                                    inv.billStatus === "PAID"
                                      ? "#10b981"
                                      : "#f59e0b",
                                }}
                              >
                                {inv.billStatus}
                              </span>
                            )}
                          </div>
                          <span>
                            Date: {inv.date} · FY: {inv.financialYear}
                            {inv.productionTitle
                              ? ` · ${inv.productionTitle}`
                              : ""}
                          </span>
                          <span
                            style={{ fontSize: "11px", color: "var(--text-3)" }}
                          >
                            Total: {rupees(inv.total)} · Paid:{" "}
                            {rupees(inv.paid)} · Outstanding:{" "}
                            <b
                              style={{
                                color:
                                  inv.outstanding > 0
                                    ? "var(--text-1)"
                                    : "#10b981",
                              }}
                            >
                              {rupees(inv.outstanding)}
                            </b>
                          </span>
                        </div>

                        <div className="party-360-item-actions">
                          {inv.outstanding > 0 && (
                            <SAButton
                              size="sm"
                              onClick={() =>
                                openSettlementForInvoice(
                                  inv.id,
                                  inv.outstanding,
                                )
                              }
                              data-testid={`settle-invoice-${inv.id}`}
                            >
                              Settle
                            </SAButton>
                          )}
                        </div>
                      </div>
                    ))
                  )}
                </div>
              )}

              {/* Tab 3: Direct Charges */}
              {activeTab === "CHARGES" && (
                <div
                  style={{ display: "grid", gap: "10px" }}
                  data-testid="charges-pane"
                >
                  {data.charges.length === 0 ? (
                    <div
                      style={{
                        padding: "30px",
                        textAlign: "center",
                        color: "var(--text-3)",
                      }}
                    >
                      No direct un-invoiced charges posted.
                    </div>
                  ) : (
                    data.charges.map((ch) => (
                      <div key={ch.id} className="party-360-item">
                        <div className="party-360-item-info">
                          <div
                            style={{
                              display: "flex",
                              alignItems: "center",
                              gap: "8px",
                            }}
                          >
                            <strong>{ch.description}</strong>
                            <span className="party-360-badge track-charge">
                              Charge
                            </span>
                          </div>
                          <span>
                            Date: {ch.date}
                            {ch.productionTitle
                              ? ` · Job: ${ch.productionTitle}`
                              : ""}
                          </span>
                          <span
                            style={{ fontSize: "11px", color: "var(--text-3)" }}
                          >
                            Amount: {rupees(ch.amount)} · Paid:{" "}
                            {rupees(ch.paid)} · Remaining:{" "}
                            <b
                              style={{
                                color:
                                  ch.outstanding > 0
                                    ? "var(--text-1)"
                                    : "#10b981",
                              }}
                            >
                              {rupees(ch.outstanding)}
                            </b>
                          </span>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              )}

              {/* Tab 4: Productions Context */}
              {activeTab === "PRODUCTIONS" && (
                <div
                  style={{ display: "grid", gap: "10px" }}
                  data-testid="productions-pane"
                >
                  {data.productions.length === 0 ? (
                    <div
                      style={{
                        padding: "30px",
                        textAlign: "center",
                        color: "var(--text-3)",
                      }}
                    >
                      No productions linked to this counterparty.
                    </div>
                  ) : (
                    data.productions.map((p) => (
                      <div key={p.id} className="party-360-item">
                        <div className="party-360-item-info">
                          <strong>{p.title}</strong>
                          <span>
                            Date: {p.eventDate} · Venue: {p.venueName} · Client:{" "}
                            {p.clientName}
                          </span>
                        </div>
                        <div className="party-360-item-actions">
                          <span className="party-360-badge">{p.status}</span>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              )}
            </div>
          </>
        )}

        {/* Settlement Modal */}
        {settlementOpen && (
          <div
            className="party-360-settlement-modal"
            onClick={() => setSettlementOpen(false)}
            data-testid="settlement-modal"
          >
            <div
              className="party-360-settlement-content"
              onClick={(e) => e.stopPropagation()}
            >
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                }}
              >
                <div>
                  <h3 style={{ margin: 0, fontSize: "18px" }}>
                    Record Settlement
                  </h3>
                  <small style={{ color: "var(--text-3)", fontSize: "11px" }}>
                    Idempotency Key: {settlementKey.slice(0, 8)}... (preserved
                    across retries)
                  </small>
                </div>
                <button
                  type="button"
                  onClick={() => setSettlementOpen(false)}
                  style={{
                    background: "transparent",
                    border: "none",
                    color: "var(--text-3)",
                    cursor: "pointer",
                  }}
                >
                  <X size={18} />
                </button>
              </div>

              {error && (
                <div
                  style={{
                    padding: "10px 14px",
                    borderRadius: "10px",
                    background: "rgba(239, 68, 68, 0.1)",
                    border: "1px solid rgba(239, 68, 68, 0.2)",
                    color: "var(--danger)",
                    fontSize: "12.5px",
                  }}
                  data-testid="settlement-error"
                >
                  {error}
                </div>
              )}

              <form
                onSubmit={handleSettlementSubmit}
                style={{ display: "grid", gap: "14px" }}
              >
                {/* Dual Track Selection */}
                <div>
                  <label
                    style={{
                      fontSize: "12px",
                      color: "var(--text-3)",
                      display: "block",
                      marginBottom: "6px",
                    }}
                  >
                    Settlement Track & Target
                  </label>
                  <div style={{ display: "flex", gap: "8px" }}>
                    <button
                      type="button"
                      className={`party-360-tab ${targetType === "INVOICE" ? "active" : ""}`}
                      onClick={() => {
                        setTargetType("INVOICE");
                        if (unpaidInvoices.length > 0) {
                          setSelectedInvoiceId(unpaidInvoices[0].id);
                          setAmount(unpaidInvoices[0].outstanding.toFixed(2));
                        }
                      }}
                      data-testid="target-invoice-btn"
                    >
                      Formal Invoice
                    </button>
                    <button
                      type="button"
                      className={`party-360-tab ${targetType === "CHARGE" ? "active" : ""}`}
                      onClick={() => {
                        setTargetType("CHARGE");
                        setSelectedInvoiceId("");
                        const bal = data?.chargeOutstanding ?? 0;
                        setAmount(bal > 0 ? bal.toFixed(2) : "");
                      }}
                      data-testid="target-charge-btn"
                    >
                      Direct Charge Balance
                    </button>
                  </div>
                </div>

                {/* If invoice target, select invoice */}
                {targetType === "INVOICE" && (
                  <div>
                    <label
                      htmlFor="party-invoice-select"
                      style={{
                        fontSize: "12px",
                        color: "var(--text-3)",
                        display: "block",
                        marginBottom: "4px",
                      }}
                    >
                      Select Invoice *
                    </label>
                    {unpaidInvoices.length === 0 ? (
                      <p
                        style={{
                          fontSize: "12px",
                          color: "var(--text-3)",
                          margin: "4px 0",
                        }}
                      >
                        No unpaid formal invoices found for this party.
                      </p>
                    ) : (
                      <select
                        id="party-invoice-select"
                        value={selectedInvoiceId}
                        onChange={(e) => {
                          const id = e.target.value;
                          setSelectedInvoiceId(id);
                          const inv = unpaidInvoices.find((i) => i.id === id);
                          if (inv) setAmount(inv.outstanding.toFixed(2));
                        }}
                        style={{
                          width: "100%",
                          padding: "9px 12px",
                          borderRadius: "10px",
                          background: "var(--surface-soft)",
                          border: "1px solid var(--border)",
                          color: "var(--text-1)",
                        }}
                        data-testid="invoice-select"
                      >
                        {unpaidInvoices.map((inv) => (
                          <option key={inv.id} value={inv.id}>
                            {inv.invoiceNumber} — Balance:{" "}
                            {rupees(inv.outstanding)} (Total:{" "}
                            {rupees(inv.total)})
                          </option>
                        ))}
                      </select>
                    )}
                  </div>
                )}

                {/* If charge target, show available charge balance */}
                {targetType === "CHARGE" && (
                  <div
                    style={{
                      padding: "10px 14px",
                      borderRadius: "10px",
                      background: "var(--surface-soft)",
                      fontSize: "12px",
                      color: "var(--text-2)",
                    }}
                  >
                    Allocates FIFO across open direct charges. Available
                    uncollected charge balance:{" "}
                    <b>{rupees(data?.chargeOutstanding)}</b>.
                  </div>
                )}

                {/* Explicit Owner Account (AZ-2 / AK-2) */}
                <div>
                  <label
                    htmlFor="party-payer-select"
                    style={{
                      fontSize: "12px",
                      color: "var(--text-3)",
                      display: "block",
                      marginBottom: "4px",
                    }}
                  >
                    Received into Owner Account *
                  </label>
                  <select
                    id="party-payer-select"
                    value={payerAccount}
                    onChange={(e) => setPayerAccount(e.target.value)}
                    style={{
                      width: "100%",
                      padding: "9px 12px",
                      borderRadius: "10px",
                      background: "var(--surface-soft)",
                      border: "1px solid var(--border)",
                      color: "var(--text-1)",
                    }}
                    data-testid="payer-account-select"
                  >
                    <option value="">Select owner account...</option>
                    <option value="AZ-2">Azeem — AZ-2</option>
                    <option value="AK-2">Akash — AK-2</option>
                  </select>
                </div>

                {/* Amount */}
                <div>
                  <label
                    htmlFor="party-amount-input"
                    style={{
                      fontSize: "12px",
                      color: "var(--text-3)",
                      display: "block",
                      marginBottom: "4px",
                    }}
                  >
                    Settlement Amount (INR) *
                  </label>
                  <input
                    id="party-amount-input"
                    type="number"
                    step="0.01"
                    min="0.01"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    placeholder="0.00"
                    style={{
                      width: "100%",
                      padding: "9px 12px",
                      borderRadius: "10px",
                      background: "var(--surface-soft)",
                      border: "1px solid var(--border)",
                      color: "var(--text-1)",
                    }}
                    data-testid="amount-input"
                    required
                  />
                </div>

                {/* Date */}
                <div>
                  <label
                    htmlFor="party-date-input"
                    style={{
                      fontSize: "12px",
                      color: "var(--text-3)",
                      display: "block",
                      marginBottom: "4px",
                    }}
                  >
                    Settlement Date *
                  </label>
                  <input
                    id="party-date-input"
                    type="date"
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    style={{
                      width: "100%",
                      padding: "9px 12px",
                      borderRadius: "10px",
                      background: "var(--surface-soft)",
                      border: "1px solid var(--border)",
                      color: "var(--text-1)",
                    }}
                    data-testid="date-input"
                    required
                  />
                </div>

                {/* Description */}
                <div>
                  <label
                    htmlFor="party-desc-input"
                    style={{
                      fontSize: "12px",
                      color: "var(--text-3)",
                      display: "block",
                      marginBottom: "4px",
                    }}
                  >
                    Reference / Description
                  </label>
                  <input
                    id="party-desc-input"
                    type="text"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Bank reference, NEFT/UPI reference, note"
                    style={{
                      width: "100%",
                      padding: "9px 12px",
                      borderRadius: "10px",
                      background: "var(--surface-soft)",
                      border: "1px solid var(--border)",
                      color: "var(--text-1)",
                    }}
                    data-testid="description-input"
                  />
                </div>

                {/* Actions */}
                <div
                  style={{
                    display: "flex",
                    justifyContent: "flex-end",
                    gap: "10px",
                    marginTop: "10px",
                  }}
                >
                  <SAButton
                    type="button"
                    onClick={() => setSettlementOpen(false)}
                    disabled={settlementMutation.isPending}
                  >
                    Cancel
                  </SAButton>
                  <SAButton
                    type="submit"
                    variant="primary"
                    disabled={
                      settlementMutation.isPending ||
                      !payerAccount ||
                      !amount ||
                      Number(amount) <= 0 ||
                      (targetType === "INVOICE" && !selectedInvoiceId)
                    }
                    data-testid="submit-settlement-btn"
                  >
                    {settlementMutation.isPending
                      ? "Posting..."
                      : "Confirm Settlement"}
                  </SAButton>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
