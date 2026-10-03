import {
  ArrowRight,
  ArrowUpRight,
  Calendar,
  Clapperboard,
  MapPin,
  Users,
} from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import { useNavigate } from "react-router-dom";
import type { DashboardProduction } from "../command.api";

interface UpcomingProductionsProps {
  productions: DashboardProduction[];
  formatInr?: (val?: number | null) => string;
}

function defaultFormatInr(val?: number | null): string {
  if (val == null || isNaN(val)) return "₹0";
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(val);
}

function formatEventDate(dateStr: string): string {
  if (!dateStr) return "";
  try {
    return new Date(`${dateStr}T00:00:00`).toLocaleDateString("en-IN", {
      day: "numeric",
      month: "short",
      year: "numeric",
    });
  } catch {
    return dateStr;
  }
}

export function UpcomingProductions({
  productions,
  formatInr = defaultFormatInr,
}: UpcomingProductionsProps) {
  const navigate = useNavigate();
  const reducedMotion = useReducedMotion();

  return (
    <div className="command-panel-card command-upcoming-panel">
      {/* Section Header */}
      <div className="command-upcoming-header">
        <div className="command-upcoming-header-left">
          <h3>Upcoming Productions</h3>
          <span
            className="command-upcoming-count-badge"
            aria-label={`${productions.length} upcoming productions`}
          >
            {productions.length}
          </span>
        </div>

        <button
          type="button"
          className="command-upcoming-view-all"
          onClick={() => navigate("/productions")}
          aria-label="View all productions"
        >
          <span>View all</span>
          <ArrowRight size={13} />
        </button>
      </div>

      {/* Production List or Empty State */}
      <div className="command-upcoming-list">
        {productions.length > 0 ? (
          productions.map((p, i) => {
            const hasPriority =
              p.priority === "URGENT" || p.priority === "HIGH";
            const dateText = formatEventDate(p.eventDate);
            const timeText = p.startTime ? p.startTime.slice(0, 5) : null;

            return (
              <motion.div
                key={p.id}
                initial={reducedMotion ? { opacity: 0 } : { opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{
                  duration: 0.18,
                  delay: reducedMotion ? 0 : i * 0.04,
                  ease: "easeOut",
                }}
              >
                <div
                  className="command-upcoming-item"
                  onClick={() => navigate(`/productions/${p.id}`)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      navigate(`/productions/${p.id}`);
                    }
                  }}
                  aria-label={`Upcoming production: ${p.title}`}
                >
                  {/* Top Row: Title + Priority + Date Pill */}
                  <div className="command-upcoming-item-top">
                    <div className="command-upcoming-title-group">
                      <strong
                        className="command-upcoming-title"
                        title={p.title}
                      >
                        {p.title}
                      </strong>
                      {hasPriority && (
                        <span
                          className={`command-upcoming-priority-pill priority-${p.priority.toLowerCase()}`}
                        >
                          {p.priority}
                        </span>
                      )}
                    </div>

                    <div className="command-upcoming-date-pill">
                      <Calendar size={12} />
                      <span>
                        {dateText}
                        {timeText ? ` · ${timeText}` : ""}
                      </span>
                      <ArrowUpRight
                        size={13}
                        className="command-upcoming-nav-icon"
                      />
                    </div>
                  </div>

                  {/* Middle Row: Client & Venue */}
                  <div className="command-upcoming-meta">
                    <div
                      className="command-upcoming-meta-item"
                      title={`Client: ${p.clientName}`}
                    >
                      <Users size={12} />
                      <span className="command-upcoming-client-name">
                        {p.clientName || "Direct Client"}
                      </span>
                    </div>

                    <span
                      className="command-upcoming-meta-divider"
                      aria-hidden="true"
                    >
                      ·
                    </span>

                    <div
                      className="command-upcoming-meta-item"
                      title={`Venue: ${p.venueName}`}
                    >
                      <MapPin size={12} />
                      <span className="command-upcoming-venue-name">
                        {p.venueName || "Venue TBD"}
                      </span>
                    </div>
                  </div>

                  {/* Bottom Strip: Financial Metrics (Contract & Advance) */}
                  <div className="command-upcoming-finances">
                    <div className="command-upcoming-finance-metric">
                      <span className="command-upcoming-finance-label">
                        Contract
                      </span>
                      <strong className="command-upcoming-finance-value">
                        {formatInr(p.contractedAmount)}
                      </strong>
                    </div>

                    <div
                      className="command-upcoming-finance-divider"
                      aria-hidden="true"
                    />

                    <div className="command-upcoming-finance-metric">
                      <span className="command-upcoming-finance-label">
                        Advance
                      </span>
                      <strong className="command-upcoming-finance-value advance">
                        {formatInr(p.receivedAmount)}
                      </strong>
                    </div>
                  </div>
                </div>
              </motion.div>
            );
          })
        ) : (
          <div className="command-upcoming-empty">
            <div className="command-upcoming-empty-icon">
              <Clapperboard size={24} strokeWidth={1.5} />
            </div>
            <strong>No upcoming productions</strong>
            <p>No productions scheduled for upcoming dates.</p>
            <button
              type="button"
              className="command-upcoming-empty-action"
              onClick={() => navigate("/productions")}
            >
              <span>View all productions</span>
              <ArrowRight size={13} />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
