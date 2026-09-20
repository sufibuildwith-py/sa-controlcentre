import {
  CalendarPlus,
  ClipboardPlus,
  MessageSquarePlus,
  Plus,
  UserPlus,
  Video,
  WalletCards,
} from "lucide-react";
import { useUiStore } from "../../app/store/ui";
import { SAModal } from "../ui/sa";
import { useNavigate } from "react-router-dom";
const items = [
  { label: "Add employee", icon: UserPlus, action: "employee" },
  {
    label: "Create production",
    icon: Video,
    to: "/productions?create=production",
  },
  { label: "Create task", icon: ClipboardPlus, to: "/work?create=task" },
  { label: "Schedule event", icon: CalendarPlus, to: "/calendar?create=event" },
  {
    label: "Schedule meeting",
    icon: MessageSquarePlus,
    to: "/meetings?create=meeting",
  },
  {
    label: "Record payroll adjustment",
    icon: WalletCards,
    to: "/payroll?adjust=1",
  },
  {
    label: "Send message",
    icon: MessageSquarePlus,
    to: "/communications?compose=1",
  },
];
export function QuickCreate() {
  const navigate = useNavigate(),
    open = useUiStore((s) => s.quickCreateOpen),
    setOpen = useUiStore((s) => s.setQuickCreateOpen),
    openEmployee = useUiStore((s) => s.openEmployeeForm);
  const act = (item: (typeof items)[number]) => {
    if (item.action === "employee") openEmployee();
    else if (item.to) {
      setOpen(false);
      navigate(item.to);
    }
  };
  return (
    <SAModal
      open={open}
      onOpenChange={setOpen}
      title="Quick create"
      description="Start an operation without leaving your current view."
    >
      <div className="quick-create-list">
        {items.map((item) => {
          const Icon = item.icon;
          return (
            <button key={item.label} onClick={() => act(item)}>
              <span>
                <Icon size={17} />
              </span>
              <div>
                <strong>{item.label}</strong>
              </div>
              <Plus size={15} />
            </button>
          );
        })}
      </div>
    </SAModal>
  );
}
