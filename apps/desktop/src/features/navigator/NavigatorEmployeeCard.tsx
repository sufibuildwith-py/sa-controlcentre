import { MapPin, Route } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { SAButton, SABentoCard, StatusBadge } from "../../components/ui/sa";
import { ageLabel, type NavigatorItem } from "./navigator.types";
export function NavigatorEmployeeCard({ item }: { item: NavigatorItem }) {
  const navigate = useNavigate();
  return (
    <SABentoCard className="navigator-selection">
      <div>
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
          {item.state === "OFF_DUTY" ? "OFF DUTY" : item.state}
        </StatusBadge>
        <small>{ageLabel(item.recordedAt)}</small>
      </div>
      <h2>{item.employeeName ?? "Employee"}</h2>
      <p>{item.roleTitle ?? "Field team"}</p>
      <dl>
        <div>
          <dt>Accuracy</dt>
          <dd>
            {item.accuracyMeters == null
              ? "Unavailable"
              : `±${Math.round(item.accuracyMeters)}m`}
          </dd>
        </div>
        <div>
          <dt>Production</dt>
          <dd>{item.productionTitle ?? "Not assigned"}</dd>
        </div>
        <div>
          <dt>Tracking</dt>
          <dd>
            {item.trackingStartedAt
              ? new Date(item.trackingStartedAt).toLocaleTimeString("en-IN", {
                  hour: "numeric",
                  minute: "2-digit",
                })
              : "Not active"}
          </dd>
        </div>
      </dl>
      <div>
        <SAButton
          size="sm"
          onClick={() => navigate(`/people/${item.employeeRef}?tab=navigator`)}
        >
          <MapPin size={14} />
          Open employee
        </SAButton>
        <SAButton size="sm" disabled>
          <Route size={14} />
          History · N2
        </SAButton>
      </div>
    </SABentoCard>
  );
}
