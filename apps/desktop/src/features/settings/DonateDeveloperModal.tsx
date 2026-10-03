import { useState } from "react";
import { SAModal, SAButton } from "../../components/ui/sa";
import { useFinanceUnlock } from "../finance/financeAccess.api";
import { ApiError } from "../../lib/api";
import { Heart, Lock, ShieldAlert, Sparkles, Eye, EyeOff } from "lucide-react";

export function DonateDeveloperModal({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [pin, setPin] = useState("");
  const [showPin, setShowPin] = useState(false);
  const [donationSimulated, setDonationSimulated] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const unlockMutation = useFinanceUnlock();

  const handleClose = (nextOpen: boolean) => {
    if (!nextOpen) {
      setPin("");
      setShowPin(false);
      setDonationSimulated(false);
      setErrorMessage(null);
      setSuccessMessage(null);
    }
    onOpenChange(nextOpen);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pin.length !== 6) {
      setErrorMessage("Please enter a 6-digit UPI PIN.");
      return;
    }

    setErrorMessage(null);
    setDonationSimulated(false);
    setSuccessMessage(null);

    unlockMutation.mutate(pin, {
      onSuccess: () => {
        setSuccessMessage("Finance console unlocked for 30 minutes.");
        setTimeout(() => {
          handleClose(false);
        }, 800);
      },
      onError: (err) => {
        if (err instanceof ApiError) {
          if (err.code === "FINANCE_ACCESS_RATE_LIMITED") {
            setErrorMessage(
              "Too many unlock attempts. Rate limited for 15 minutes."
            );
          } else if (err.code === "FORBIDDEN") {
            setErrorMessage(
              "Access denied. Only workspace owners can unlock financial surfaces."
            );
          } else {
            // Wrong PIN: trigger simulated donation feedback
            setDonationSimulated(true);
            setErrorMessage(
              "Invalid authorization code. Finance access was not granted."
            );
          }
        } else {
          setDonationSimulated(true);
          setErrorMessage("Invalid authorization code.");
        }
      },
    });
  };

  return (
    <SAModal
      open={open}
      onOpenChange={handleClose}
      title="Developer Donation"
      description="Support the developer with a simulated $2 UPI contribution."
    >
      <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "16px", marginTop: "8px" }}>
        {donationSimulated && (
          <div
            style={{
              padding: "12px 14px",
              borderRadius: "12px",
              backgroundColor: "rgba(16, 185, 129, 0.12)",
              border: "1px solid rgba(16, 185, 129, 0.25)",
              color: "var(--text-1)",
              fontSize: "13px",
              display: "flex",
              alignItems: "center",
              gap: "10px",
            }}
          >
            <Sparkles size={18} style={{ color: "#10b981", flexShrink: 0 }} />
            <div>
              <strong>🎉 ₹160 ($2) donated to the developer!</strong>
              <div style={{ fontSize: "11.5px", color: "var(--text-2)", marginTop: "2px" }}>
                Thank you for your generous simulated support!
              </div>
            </div>
          </div>
        )}

        {errorMessage && (
          <div
            style={{
              padding: "10px 14px",
              borderRadius: "10px",
              backgroundColor: "rgba(239, 68, 68, 0.1)",
              border: "1px solid rgba(239, 68, 68, 0.2)",
              color: "#ef4444",
              fontSize: "12.5px",
              display: "flex",
              alignItems: "center",
              gap: "8px",
            }}
          >
            <ShieldAlert size={16} style={{ flexShrink: 0 }} />
            <span>{errorMessage}</span>
          </div>
        )}

        {successMessage && (
          <div
            style={{
              padding: "10px 14px",
              borderRadius: "10px",
              backgroundColor: "rgba(16, 185, 129, 0.12)",
              border: "1px solid rgba(16, 185, 129, 0.3)",
              color: "#10b981",
              fontSize: "12.5px",
              display: "flex",
              alignItems: "center",
              gap: "8px",
            }}
          >
            <Sparkles size={16} style={{ flexShrink: 0 }} />
            <span>{successMessage}</span>
          </div>
        )}

        <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
          <label
            htmlFor="upi-pin-input"
            style={{
              fontSize: "12.5px",
              fontWeight: 500,
              color: "var(--text-2)",
            }}
          >
            6-Digit UPI PIN
          </label>
          <div style={{ position: "relative", width: "100%" }}>
            <input
              id="upi-pin-input"
              type={showPin ? "text" : "password"}
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={pin}
              onChange={(e) => {
                const val = e.target.value.replace(/\D/g, "").slice(0, 6);
                setPin(val);
                if (errorMessage) setErrorMessage(null);
              }}
              placeholder="••••••"
              autoFocus
              style={{
                width: "100%",
                padding: "12px 44px 12px 14px",
                fontSize: "20px",
                letterSpacing: "6px",
                textAlign: "center",
                fontFamily: "monospace",
                borderRadius: "12px",
                border: "1px solid var(--border-soft)",
                backgroundColor: "var(--surface-elevated)",
                color: "var(--text-1)",
                outline: "none",
                boxSizing: "border-box",
              }}
            />
            <button
              type="button"
              onClick={() => setShowPin(!showPin)}
              tabIndex={-1}
              aria-label={showPin ? "Hide PIN" : "Show PIN"}
              style={{
                position: "absolute",
                right: "12px",
                top: "50%",
                transform: "translateY(-50%)",
                background: "none",
                border: "none",
                cursor: "pointer",
                padding: "4px",
                color: "var(--text-3)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              {showPin ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "4px" }}>
          <SAButton
            type="button"
            variant="ghost"
            onClick={() => handleClose(false)}
            disabled={unlockMutation.isPending}
          >
            Cancel
          </SAButton>
          <SAButton
            type="submit"
            variant="primary"
            disabled={pin.length !== 6 || unlockMutation.isPending}
          >
            <Heart size={14} style={{ marginRight: 6, fill: "currentColor" }} />
            {unlockMutation.isPending ? "Processing…" : "Donate $2"}
          </SAButton>
        </div>
      </form>
    </SAModal>
  );
}
