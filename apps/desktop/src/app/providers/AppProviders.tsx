import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import * as Tooltip from "@radix-ui/react-tooltip";
import { useState, type PropsWithChildren } from "react";
import { SAToastProvider } from "../../components/ui/toast";
import { MotionConfig } from "motion/react";
export function AppProviders({ children }: PropsWithChildren) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 20_000, retry: 1, refetchOnWindowFocus: false },
          mutations: { retry: 0 },
        },
      }),
  );
  return (
    <QueryClientProvider client={client}>
      <MotionConfig reducedMotion="user">
        <Tooltip.Provider delayDuration={250}>
          <SAToastProvider>{children}</SAToastProvider>
        </Tooltip.Provider>
      </MotionConfig>
    </QueryClientProvider>
  );
}
