import * as Dialog from "@radix-ui/react-dialog";
import { Command as CommandMenu } from "cmdk";
import {
  CalendarDays,
  CheckSquare,
  Home,
  MessageCircle,
  Palette,
  Search,
  Settings,
  UserPlus,
  Users,
  Video,
  WalletCards,
  X,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../../lib/api";
import type { Employee, Production, WorkTask } from "../../types/domain";
import { useUiStore } from "../../app/store/ui";
import type { NavigateFunction } from "react-router-dom";
import { useOverlayFocusRestore } from "../ui/sa";
export function SACommandPalette({
  onNavigate,
}: {
  onNavigate: NavigateFunction;
}) {
  const focus = useOverlayFocusRestore();
  const open = useUiStore((s) => s.paletteOpen),
    setOpen = useUiStore((s) => s.setPaletteOpen),
    openEmployee = useUiStore((s) => s.openEmployeeForm),
    theme = useUiStore((s) => s.theme),
    setTheme = useUiStore((s) => s.setTheme);
  const employees = useQuery({
    queryKey: ["employees", "palette"],
    queryFn: () => api<Employee[]>("/employees"),
  });
  const productions = useQuery({
    queryKey: ["productions", "palette"],
    queryFn: () => api<Production[]>("/productions"),
  });
  const tasks = useQuery({
    queryKey: ["tasks", "palette"],
    queryFn: () => api<WorkTask[]>("/tasks"),
  });
  const act = (fn: () => void) => {
    fn();
    setOpen(false);
  };
  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Dialog.Portal>
        <Dialog.Overlay className="modal-overlay" />
        <Dialog.Content
          className="command-dialog"
          aria-describedby={undefined}
          {...focus}
        >
          <Dialog.Title className="sr-only">Command palette</Dialog.Title>
          <CommandMenu label="SA Command">
            <div className="command-input">
              <Search size={17} />
              <CommandMenu.Input placeholder="Search people, productions, tasks or actions…" />
              <Dialog.Close aria-label="Close">
                <X size={16} />
              </Dialog.Close>
            </div>
            <CommandMenu.List>
              <CommandMenu.Empty>No matching command.</CommandMenu.Empty>
              <CommandMenu.Group heading="Navigate">
                <Item icon={Home} onSelect={() => act(() => onNavigate("/"))}>
                  Open Command
                </Item>
                <Item
                  icon={Users}
                  onSelect={() => act(() => onNavigate("/people"))}
                >
                  Open People
                </Item>
                <Item
                  icon={Video}
                  onSelect={() => act(() => onNavigate("/productions"))}
                >
                  Open Productions
                </Item>
                <Item
                  icon={CheckSquare}
                  onSelect={() => act(() => onNavigate("/work"))}
                >
                  Open Work
                </Item>
                <Item
                  icon={CalendarDays}
                  onSelect={() => act(() => onNavigate("/calendar"))}
                >
                  Open Calendar
                </Item>
                <Item
                  icon={WalletCards}
                  onSelect={() => act(() => onNavigate("/payroll"))}
                >
                  Open Payroll
                </Item>
                <Item
                  icon={MessageCircle}
                  onSelect={() => act(() => onNavigate("/communications"))}
                >
                  Open Communications
                </Item>
                <Item
                  icon={Settings}
                  onSelect={() => act(() => onNavigate("/settings"))}
                >
                  Open Settings
                </Item>
              </CommandMenu.Group>
              <CommandMenu.Group heading="Create & settings">
                <Item
                  icon={UserPlus}
                  onSelect={() => act(() => openEmployee())}
                >
                  Add employee
                </Item>
                <Item
                  icon={Video}
                  onSelect={() =>
                    act(() => onNavigate("/productions?create=production"))
                  }
                >
                  Create production
                </Item>
                <Item
                  icon={CheckSquare}
                  onSelect={() => act(() => onNavigate("/work?create=task"))}
                >
                  Create task
                </Item>
                <Item
                  icon={CalendarDays}
                  onSelect={() =>
                    act(() => onNavigate("/meetings?create=meeting"))
                  }
                >
                  Create meeting
                </Item>
                <Item
                  icon={Palette}
                  onSelect={() =>
                    act(() =>
                      setTheme(theme === "pearl" ? "charcoal" : "pearl"),
                    )
                  }
                >
                  Switch to {theme === "pearl" ? "Charcoal" : "Pearl"} theme
                </Item>
              </CommandMenu.Group>
              {employees.data && (
                <CommandMenu.Group heading="People">
                  {employees.data.flatMap((employee) => [
                    <Item
                      key={employee.id}
                      value={`${employee.displayName} ${employee.employeeCode} ${employee.roleTitle}`}
                      icon={Users}
                      onSelect={() =>
                        act(() => onNavigate(`/people/${employee.id}`))
                      }
                    >
                      <span>{employee.displayName}</span>
                      <small>{employee.roleTitle} · Open employee</small>
                    </Item>,
                    <Item
                      key={`${employee.id}-message`}
                      value={`${employee.displayName} message whatsapp communication`}
                      icon={MessageCircle}
                      onSelect={() =>
                        act(() =>
                          onNavigate(
                            `/communications?compose=1&employeeId=${employee.id}`,
                          ),
                        )
                      }
                    >
                      <span>Message {employee.displayName}</span>
                      <small>Queue a WhatsApp notice</small>
                    </Item>,
                    <Item
                      key={`${employee.id}-work`}
                      value={`${employee.displayName} work tasks`}
                      icon={CheckSquare}
                      onSelect={() =>
                        act(() => onNavigate(`/people/${employee.id}?tab=work`))
                      }
                    >
                      <span>{employee.displayName} — Work</span>
                      <small>Open assigned work</small>
                    </Item>,
                    <Item
                      key={`${employee.id}-payroll`}
                      value={`${employee.displayName} payroll`}
                      icon={WalletCards}
                      onSelect={() =>
                        act(() =>
                          onNavigate(`/people/${employee.id}?tab=payroll`),
                        )
                      }
                    >
                      <span>{employee.displayName} — Payroll</span>
                      <small>Open private payroll history</small>
                    </Item>,
                  ])}
                </CommandMenu.Group>
              )}
              {productions.data && (
                <CommandMenu.Group heading="Productions">
                  {productions.data.map((p) => (
                    <Item
                      key={p.id}
                      value={`${p.title} ${p.clientName}`}
                      icon={Video}
                      onSelect={() =>
                        act(() => onNavigate(`/productions/${p.id}`))
                      }
                    >
                      <span>{p.title}</span>
                      <small>
                        {p.status.replaceAll("_", " ")} · Open production
                      </small>
                    </Item>
                  ))}
                </CommandMenu.Group>
              )}
              {tasks.data && (
                <CommandMenu.Group heading="Work">
                  {tasks.data.slice(0, 12).map((t) => (
                    <Item
                      key={t.id}
                      value={`${t.title} ${t.assigneeName ?? ""}`}
                      icon={CheckSquare}
                      onSelect={() => act(() => onNavigate("/work"))}
                    >
                      <span>{t.title}</span>
                      <small>{t.status.replaceAll("_", " ")} · Open Work</small>
                    </Item>
                  ))}
                </CommandMenu.Group>
              )}
            </CommandMenu.List>
            <footer>
              <span>↑↓ Navigate</span>
              <span>↵ Open</span>
              <span>Esc Close</span>
            </footer>
          </CommandMenu>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
function Item({
  icon: Icon,
  children,
  ...props
}: {
  icon: typeof Home;
  children: React.ReactNode;
  value?: string;
  onSelect: () => void;
}) {
  return (
    <CommandMenu.Item {...props}>
      <Icon size={16} />
      <div>{children}</div>
    </CommandMenu.Item>
  );
}
