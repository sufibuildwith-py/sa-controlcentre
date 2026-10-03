import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, json } from "../../lib/api";

export type FinanceAccessStatus = {
  eligible: boolean;
  unlocked: boolean;
  expiresAt: string | null;
};

export type FinanceUnlockResult = {
  unlocked: boolean;
  expiresAt: string;
};

export const financeAccessApi = {
  status: () => api<FinanceAccessStatus>("/finance-access/status"),
  unlock: (code: string) =>
    api<FinanceUnlockResult>("/finance-access/unlock", {
      method: "POST",
      ...json({ code }),
    }),
  lock: () => api<void>("/finance-access/lock", { method: "POST" }),
};

export function useFinanceAccess() {
  return useQuery({
    queryKey: ["finance-access", "status"],
    queryFn: () => financeAccessApi.status(),
    staleTime: 15_000,
    refetchInterval: 30_000,
  });
}

export function useFinanceUnlock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => financeAccessApi.unlock(code),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["finance-access"] });
      queryClient.invalidateQueries({ queryKey: ["command-dashboard"] });
      queryClient.invalidateQueries({ queryKey: ["finance"] });
      queryClient.invalidateQueries({ queryKey: ["billing"] });
      queryClient.invalidateQueries({ queryKey: ["payroll"] });
      queryClient.invalidateQueries({ queryKey: ["production"] });
      queryClient.invalidateQueries({ queryKey: ["employees"] });
    },
  });
}

export function useFinanceLock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => financeAccessApi.lock(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["finance-access"] });
      queryClient.invalidateQueries({ queryKey: ["command-dashboard"] });
      queryClient.invalidateQueries({ queryKey: ["finance"] });
      queryClient.invalidateQueries({ queryKey: ["billing"] });
      queryClient.invalidateQueries({ queryKey: ["payroll"] });
      queryClient.invalidateQueries({ queryKey: ["production"] });
      queryClient.invalidateQueries({ queryKey: ["employees"] });
    },
  });
}
