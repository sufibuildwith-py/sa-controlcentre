import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { MapPin, Radio, Smartphone, Unplug } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useState } from "react";
import {
  EmptyState,
  SAButton,
  SABentoCard,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
import { navigatorApi } from "./navigator.api";
import { ageLabel } from "./navigator.types";
export function EmployeeNavigatorPanel({ employeeId }: { employeeId: string }) {
  const [message, setMessage] = useState("");
  const navigate = useNavigate(),
    cache = useQueryClient(),
    [live, pairing] = [
      useQuery({ queryKey: ["navigator", "live"], queryFn: navigatorApi.live }),
      useMutation({ mutationFn: () => navigatorApi.pairing(employeeId) }),
    ];
  const item = live.data?.items.find((i) => i.employeeRef === employeeId);
  const revoke = useMutation({
    mutationFn: () => navigatorApi.revoke(item!.deviceId),
    onSuccess: () =>
      cache.invalidateQueries({ queryKey: ["navigator", "live"] }),
  });
  const send = useMutation({
    mutationFn: () =>
      navigatorApi.mobileMessage(employeeId, "SA Productions update", message),
    onSuccess: () => setMessage(""),
  });
  if (live.isPending) return <SkeletonCard />;
  if (live.isError)
    return (
      <EmptyState
        title="Navigator unavailable"
        description="Device state could not be loaded."
      />
    );
  if (!item)
    return (
      <EmptyState
        title="No phone paired"
        description="Create a short-lived code for this employee's SA Navigator app."
        action={
          <SAButton onClick={() => pairing.mutate()}>Create pairing</SAButton>
        }
      />
    );
  return (
    <div className="employee-navigator">
      <SABentoCard>
        <Smartphone size={18} />
        <span>Device</span>
        <strong>{item.deviceLabel ?? "Connected phone"}</strong>
        <small>
          Paired{" "}
          {item.pairedAt
            ? new Date(item.pairedAt).toLocaleDateString("en-IN")
            : "recently"}
        </small>
      </SABentoCard>
      <SABentoCard>
        <Radio size={18} />
        <span>Sharing state</span>
        <StatusBadge
          tone={
            item.state === "LIVE"
              ? "success"
              : item.state === "STALE"
                ? "warning"
                : item.state === "OFFLINE"
                  ? "danger"
                  : "neutral"
          }
        >
          {item.state}
        </StatusBadge>
        <small>
          {ageLabel(item.recordedAt)}
          {item.accuracyMeters != null
            ? ` · ±${Math.round(item.accuracyMeters)}m`
            : ""}
        </small>
      </SABentoCard>
      {pairing.data && (
        <SABentoCard className="pairing-code">
          <span>Pairing code</span>
          <strong>
            {pairing.data.code.replace(/(\d{3})(\d{3})/, "$1 $2")}
          </strong>
          <small>
            Expires{" "}
            {new Date(pairing.data.expiresAt).toLocaleTimeString("en-IN")}
          </small>
        </SABentoCard>
      )}
      <div className="navigator-actions">
        <SAButton onClick={() => navigate(`/navigator?employee=${employeeId}`)}>
          <MapPin size={14} />
          Show on map
        </SAButton>
        <SAButton onClick={() => pairing.mutate()}>Create new pairing</SAButton>
        <SAButton variant="danger" onClick={() => revoke.mutate()}>
          <Unplug size={14} />
          Revoke device
        </SAButton>
      </div>
      <SABentoCard className="navigator-message">
        <span>Send update to phone</span>
        <textarea
          aria-label="Navigator employee message"
          maxLength={1600}
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          placeholder="Send a simple operational update"
        />
        <SAButton disabled={!message.trim() || send.isPending} onClick={() => send.mutate()}>
          {send.isPending ? "Sending…" : "Send message"}
        </SAButton>
      </SABentoCard>
    </div>
  );
}
