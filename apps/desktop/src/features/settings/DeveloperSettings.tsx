import { useState } from "react";
import { Code2, Heart, Lock, ShieldCheck } from "lucide-react";
import { SABentoCard, SAButton, StatusBadge } from "../../components/ui/sa";
import {
  useFinanceAccess,
  useFinanceLock,
} from "../finance/financeAccess.api";
import { DonateDeveloperModal } from "./DonateDeveloperModal";

export function DeveloperSettings() {
  const [modalOpen, setModalOpen] = useState(false);
  const access = useFinanceAccess();
  const lockMutation = useFinanceLock();

  const isUnlocked = access.data?.unlocked ?? false;
  const expiresAt = access.data?.expiresAt;

  const formattedExpiry = expiresAt
    ? new Date(expiresAt).toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
      })
    : null;

  return (
    <>
      <SABentoCard>
        <div className="settings-section-title">
          <span>
            <Code2 size={17} />
            <div>
              <strong>Developer Options</strong>
              <small>
                Application configuration and developer utilities.
              </small>
            </div>
          </span>
          {isUnlocked && (
            <StatusBadge tone="success">Finance Unlocked</StatusBadge>
          )}
        </div>

        <div className="settings-row" style={{ marginTop: "12px" }}>
          <span>
            <Heart size={16} style={{ color: isUnlocked ? "#10b981" : "var(--text-3)" }} />
            <div>
              <strong>
                {isUnlocked ? "Finance Surface Active" : "Developer Support"}
              </strong>
              <small>
                {isUnlocked
                  ? `Authenticated session active. Expires at ${formattedExpiry}.`
                  : "Support ongoing engineering of SA Command."}
              </small>
            </div>
          </span>

          <div style={{ display: "flex", gap: "8px" }}>
            {isUnlocked ? (
              <SAButton
                size="sm"
                variant="ghost"
                onClick={() => lockMutation.mutate()}
                disabled={lockMutation.isPending}
              >
                <Lock size={13} style={{ marginRight: 5 }} />
                {lockMutation.isPending ? "Locking…" : "Lock Finance"}
              </SAButton>
            ) : (
              <SAButton
                size="sm"
                variant="primary"
                onClick={() => setModalOpen(true)}
              >
                <Heart size={13} style={{ marginRight: 5, fill: "currentColor" }} />
                Donate Developer
              </SAButton>
            )}
          </div>
        </div>
      </SABentoCard>

      <DonateDeveloperModal
        open={modalOpen}
        onOpenChange={setModalOpen}
      />
    </>
  );
}
