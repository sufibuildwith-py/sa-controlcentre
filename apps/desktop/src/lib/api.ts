const API_URL =
  import.meta.env.VITE_API_URL ??
  (import.meta.env.PROD
    ? "https://api.saproduction.in/api/v1"
    : "http://localhost:8080/api/v1");
type ApiErrorBody = {
  error: {
    code: string;
    message: string;
    traceId?: string;
    fields?: Record<string, string>;
  };
};
export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public fields: Record<string, string> = {},
    public traceId?: string,
  ) {
    super(message);
  }
}
let sessionToken: string | null = null;
let restored = false;
let restoring: Promise<string | null> | null = null;
const isTauri = () =>
  typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;
async function restoreToken() {
  if (restoring) return restoring;
  if (restored) return sessionToken;
  restoring = (async () => {
    if (isTauri()) {
      try {
        const { invoke } = await import("@tauri-apps/api/core");
        sessionToken = await invoke<string | null>("load_session_token");
      } catch {
        sessionToken = null;
      }
    } else if (import.meta.env.VITE_APP_MODE === "demo") {
      sessionToken = sessionStorage.getItem("sa-command-demo-session");
    }
    restored = true;
    return sessionToken;
  })();
  try {
    return await restoring;
  } finally {
    restoring = null;
  }
}
export async function setSessionToken(token: string) {
  sessionToken = token;
  restored = true;
  if (isTauri()) {
    const { invoke } = await import("@tauri-apps/api/core");
    await invoke("store_session_token", { token });
  } else if (import.meta.env.VITE_APP_MODE === "demo") {
    sessionStorage.setItem("sa-command-demo-session", token);
  }
}
export async function clearSessionToken() {
  sessionToken = null;
  restored = true;
  if (isTauri()) {
    const { invoke } = await import("@tauri-apps/api/core");
    await invoke("delete_session_token");
  } else {
    sessionStorage.removeItem("sa-command-demo-session");
  }
}
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const token = await restoreToken();
  const response =
    import.meta.env.VITE_DESKTOP_RELEASE === "true"
      ? await (async () => {
          if (!isTauri())
            throw new Error(
              "Please open the installed SA Command application.",
            );
          const { invoke } = await import("@tauri-apps/api/core");
          const reply = await invoke<{ status: number; body: string }>(
            "desktop_request",
            {
              path,
              method: init?.method ?? "GET",
              body: typeof init?.body === "string" ? init.body : null,
              token,
            },
          );
          return new Response(reply.body, {
            status: reply.status,
            headers: { "Content-Type": "application/json" },
          });
        })()
      : await fetch(`${API_URL}${path}`, {
          ...init,
          credentials: "omit",
          headers: {
            "Content-Type": "application/json",
            Accept: "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
            ...init?.headers,
          },
        });
  const body = (await response.json().catch(() => null)) as
    { data: T } | ApiErrorBody | null;
  if (!response.ok) {
    const e = (body as ApiErrorBody | null)?.error;
    throw new ApiError(
      e?.code ?? "NETWORK_ERROR",
      e?.message ?? "Unable to complete the request.",
      e?.fields ?? {},
      e?.traceId,
    );
  }
  return (body as { data: T }).data;
}
export const json = (value: unknown): RequestInit => ({
  body: JSON.stringify(value),
});
