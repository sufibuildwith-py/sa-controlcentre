import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Copy, Radio, Search, Smartphone, UserCheck, Users } from "lucide-react";
import { useMemo, useState } from "react";
import { FormField, SAButton, SABentoCard, SAModal, StatusBadge } from "../../components/ui/sa";
import { api } from "../../lib/api";
import { initials } from "../employees/PeoplePage";
import { navigatorApi } from "./navigator.api";
import type { NavigatorItem } from "./navigator.types";
import type { Employee } from "../../types/domain";

interface AddEmployeeModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  pairedItems: NavigatorItem[];
}

export function AddEmployeeModal({
  open,
  onOpenChange,
  pairedItems,
}: AddEmployeeModalProps) {
  const [search, setSearch] = useState("");
  const [copied, setCopied] = useState(false);
  const [activePairing, setActivePairing] = useState<{
    employeeName: string;
    code: string;
    expiresAt: string;
  } | null>(null);

  const client = useQueryClient();

  const employeesQuery = useQuery({
    queryKey: ["employees", "active-roster"],
    queryFn: () => api<Employee[]>("/employees?status=ACTIVE"),
    enabled: open,
  });

  const pairMutation = useMutation({
    mutationFn: (emp: Employee) =>
      navigatorApi.pairing(emp.id).then((res) => ({
        employeeName: emp.displayName,
        ...res,
      })),
    onSuccess: (data) => {
      setActivePairing({
        employeeName: data.employeeName,
        code: data.code,
        expiresAt: data.expiresAt,
      });
      client.invalidateQueries({ queryKey: ["navigator", "live"] });
    },
  });

  const pairedMap = useMemo(() => {
    const map = new Map<string, NavigatorItem>();
    for (const item of pairedItems) {
      if (item.state !== "UNPAIRED") {
        map.set(item.employeeRef, item);
      }
    }
    return map;
  }, [pairedItems]);

  const filteredEmployees = useMemo(() => {
    const list = employeesQuery.data ?? [];
    if (!search.trim()) return list;
    const q = search.toLowerCase();
    return list.filter(
      (e) =>
        (e.displayName ?? "").toLowerCase().includes(q) ||
        (e.roleTitle ?? "").toLowerCase().includes(q) ||
        (e.department ?? "").toLowerCase().includes(q),
    );
  }, [employeesQuery.data, search]);

  const handleCopyCode = () => {
    if (!activePairing) return;
    navigator.clipboard.writeText(activePairing.code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleClose = (v: boolean) => {
    if (!v) {
      setActivePairing(null);
      setSearch("");
      pairMutation.reset();
    }
    onOpenChange(v);
  };

  return (
    <SAModal
      open={open}
      onOpenChange={handleClose}
      title="Pair Employee to Navigator"
      description="Connect existing team members to live field tracking using the SA Navigator mobile app."
    >
      {activePairing ? (
        <div className="navigator-pairing-success">
          <SABentoCard className="navigator-pairing-card">
            <div className="navigator-pairing-header">
              <Radio size={20} className="text-accent" />
              <div>
                <strong>Pairing Code for {activePairing.employeeName}</strong>
                <span>Valid for 10 minutes</span>
              </div>
            </div>

            <div className="navigator-code-display">
              <code>{activePairing.code.replace(/(\d{3})(\d{3})/, "$1 $2")}</code>
              <SAButton
                size="sm"
                variant="secondary"
                onClick={handleCopyCode}
                aria-label="Copy pairing code"
              >
                {copied ? <Check size={14} /> : <Copy size={14} />}
                {copied ? "Copied" : "Copy code"}
              </SAButton>
            </div>

            <div className="navigator-pairing-instructions">
              <p>
                <strong>Next steps on mobile:</strong>
              </p>
              <ol>
                <li>Open <em>SA Navigator</em> on the employee&apos;s phone.</li>
                <li>Tap <strong>&ldquo;Pair with Code&rdquo;</strong>.</li>
                <li>Enter the 6-digit code above.</li>
              </ol>
              <small className="muted">
                Expires{" "}
                {new Date(activePairing.expiresAt).toLocaleTimeString("en-IN", {
                  hour: "2-digit",
                  minute: "2-digit",
                })}
              </small>
            </div>
          </SABentoCard>

          <div className="modal-actions">
            <SAButton variant="primary" onClick={() => setActivePairing(null)}>
              Pair Another Employee
            </SAButton>
            <SAButton onClick={() => handleClose(false)}>Done</SAButton>
          </div>
        </div>
      ) : (
        <div className="navigator-employee-picker">
          <div className="navigator-picker-search">
            <label>
              <Search size={15} />
              <input
                aria-label="Search people"
                placeholder="Search people by name or role..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                autoFocus
              />
            </label>
          </div>

          {employeesQuery.isPending ? (
            <div className="navigator-picker-loading">Loading team members...</div>
          ) : employeesQuery.isError ? (
            <div className="navigator-picker-error">
              Unable to load employees. Please try again.
            </div>
          ) : !filteredEmployees.length ? (
            <div className="navigator-picker-empty">
              <Users size={24} />
              <p>No matching employees found.</p>
            </div>
          ) : (
            <div className="navigator-picker-list" role="list">
              {filteredEmployees.map((emp) => {
                const paired = pairedMap.get(emp.id);
                const isPairingThis =
                  pairMutation.isPending &&
                  pairMutation.variables?.id === emp.id;

                return (
                  <div
                    key={emp.id}
                    className={`navigator-picker-row ${paired ? "paired" : ""}`}
                    role="listitem"
                  >
                    <div className="navigator-picker-person">
                      <span className="navigator-avatar">
                        {initials(emp.displayName)}
                      </span>
                      <div>
                        <strong>{emp.displayName}</strong>
                        <small>
                          {emp.roleTitle || "Field team"}
                          {emp.department ? ` · ${emp.department}` : ""}
                        </small>
                      </div>
                    </div>

                    <div className="navigator-picker-status">
                      {paired ? (
                        <div className="navigator-paired-badge">
                          <StatusBadge tone="success">
                            <UserCheck size={12} style={{ marginRight: "4px" }} />
                            Paired
                          </StatusBadge>
                          <small className="muted">
                            {paired.deviceLabel || "Phone connected"}
                          </small>
                        </div>
                      ) : (
                        <SAButton
                          size="sm"
                          variant="secondary"
                          disabled={isPairingThis}
                          onClick={() => pairMutation.mutate(emp)}
                        >
                          <Smartphone size={14} />
                          {isPairingThis ? "Generating…" : "Pair Device"}
                        </SAButton>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {pairMutation.isError && (
            <p className="form-error">
              Pairing initiation failed. Please ensure the Navigator service is running.
            </p>
          )}

          <div className="modal-actions">
            <SAButton onClick={() => handleClose(false)}>Close</SAButton>
          </div>
        </div>
      )}
    </SAModal>
  );
}
