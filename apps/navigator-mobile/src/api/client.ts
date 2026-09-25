import { gatewayUrl } from "../config";
import { credentials } from "../storage/credentials";

export type ApiErrorCode =
  | "INVALID_CODE" | "EXPIRED_CODE" | "ALREADY_USED" | "RATE_LIMITED"
  | "NETWORK_UNAVAILABLE" | "SERVER_UNAVAILABLE" | "AUTH_REQUIRED"
  | "DEVICE_REVOKED" | "TIMEOUT" | "INVALID_REQUEST" | "UNKNOWN";

export class ApiError extends Error {
  constructor(public code: ApiErrorCode, message: string, public status?: number, public retryAfter?: number) {
    super(message);
  }
}

const normalizedCode = (status: number, code?: string): ApiErrorCode => {
  if (code === "DEVICE_REVOKED" || code === "INVALID_DEVICE") return "DEVICE_REVOKED";
  if (status === 401 || status === 403) return "AUTH_REQUIRED";
  if (status === 429) return "RATE_LIMITED";
  if (code === "PAIRING_USED") return "ALREADY_USED";
  if (code === "PAIRING_EXPIRED") return "EXPIRED_CODE";
  if (code === "INVALID_PAIRING" || status === 400) return "INVALID_CODE";
  if (status >= 500) return "SERVER_UNAVAILABLE";
  return "INVALID_REQUEST";
};

export async function apiRequest<T>(path: string, init: RequestInit = {}, authenticated = true): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12_000);
  try {
    const token = authenticated ? await credentials.get() : null;
    const response = await fetch(`${gatewayUrl}${path}`, {
      ...init,
      signal: controller.signal,
      headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init.headers },
    });
    const payload = response.status === 204 ? undefined : await response.json().catch(() => undefined);
    if (!response.ok) {
      const remoteCode = payload?.error?.code as string | undefined;
      const code = normalizedCode(response.status, remoteCode);
      if (code === "DEVICE_REVOKED" || code === "AUTH_REQUIRED") await credentials.clear();
      throw new ApiError(code, payload?.error?.message ?? "Request failed.", response.status, Number(response.headers.get("Retry-After")) || undefined);
    }
    return payload as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error instanceof Error && error.name === "AbortError") throw new ApiError("TIMEOUT", "The request timed out.");
    if (__DEV__) {
      console.warn("[SA Employee] gateway request failed", {
        path,
        gatewayUrl,
        reason: error instanceof Error ? `${error.name}: ${error.message}` : "Unknown error"
      });
    }
    throw new ApiError("NETWORK_UNAVAILABLE", "SA Productions cannot be reached right now.");
  } finally {
    clearTimeout(timeout);
  }
}
