import * as Toast from "@radix-ui/react-toast";
import { CheckCircle2, Info, X } from "lucide-react";
import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type PropsWithChildren,
} from "react";

type ToastTone = "success" | "info";
type ToastMessage = {
  id: number;
  title: string;
  description?: string;
  tone: ToastTone;
};
type ToastInput = { title: string; description?: string; tone?: ToastTone };
const ToastContext = createContext<((message: ToastInput) => void) | null>(
  null,
);

export function SAToastProvider({ children }: PropsWithChildren) {
  const [messages, setMessages] = useState<ToastMessage[]>([]);
  const notify = useCallback(
    (message: ToastInput) =>
      setMessages((current) => [
        ...current,
        {
          ...message,
          id: Date.now() + Math.random(),
          tone: message.tone ?? "success",
        },
      ]),
    [],
  );
  const value = useMemo(() => notify, [notify]);
  return (
    <Toast.Provider swipeDirection="right">
      <ToastContext.Provider value={value}>
        {children}
        {messages.map((message) => (
          <Toast.Root
            key={message.id}
            className={`sa-toast sa-toast--${message.tone}`}
            duration={3600}
            onOpenChange={(open) =>
              !open &&
              setMessages((current) =>
                current.filter((item) => item.id !== message.id),
              )
            }
          >
            <span className="sa-toast__icon">
              {message.tone === "success" ? (
                <CheckCircle2 size={17} />
              ) : (
                <Info size={17} />
              )}
            </span>
            <div>
              <Toast.Title>{message.title}</Toast.Title>
              {message.description && (
                <Toast.Description>{message.description}</Toast.Description>
              )}
            </div>
            <Toast.Close aria-label="Dismiss notification">
              <X size={15} />
            </Toast.Close>
          </Toast.Root>
        ))}
      </ToastContext.Provider>
      <Toast.Viewport className="sa-toast-viewport" />
    </Toast.Provider>
  );
}

export function useSAToast() {
  const context = useContext(ToastContext);
  if (!context)
    throw new Error("useSAToast must be used inside SAToastProvider");
  return context;
}
