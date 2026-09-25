import { api, json } from "../../lib/api";
import type {
  HqAttention,
  HqConfig,
  HqEquipment,
  HqMovement,
  HqOverview,
  HqPage,
} from "./headquarters.types";
export const headquartersApi = {
  overview: () => api<HqOverview>("/headquarters/overview"),
  equipment: (page = 0, query = "") =>
    api<HqPage<HqEquipment>>(
      `/headquarters/equipment?page=${page}&size=50&query=${encodeURIComponent(query)}`,
    ),
  movements: (page = 0) =>
    api<HqPage<HqMovement>>(`/headquarters/movements?page=${page}&size=50`),
  attention: () => api<HqAttention[]>("/headquarters/attention"),
  config: () => api<HqConfig>("/headquarters/config"),
  createEquipment: (body: unknown) =>
    api("/headquarters/equipment", { method: "POST", ...json(body) }),
  createCategory: (body: unknown) =>
    api("/headquarters/categories", { method: "POST", ...json(body) }),
  createUnit: (body: unknown) =>
    api("/headquarters/units", { method: "POST", ...json(body) }),
  createLocation: (body: unknown) =>
    api("/headquarters/locations", { method: "POST", ...json(body) }),
  stock: (id: string, body: unknown) =>
    api(`/headquarters/equipment/${id}/stock-in`, {
      method: "POST",
      ...json(body),
    }),
  adjust: (id: string, body: unknown) =>
    api(`/headquarters/equipment/${id}/adjust`, {
      method: "POST",
      ...json(body),
    }),
  reserve: (body: unknown) =>
    api("/headquarters/reservations", { method: "POST", ...json(body) }),
  createDispatch: (body: unknown) =>
    api<{ id: string }>("/headquarters/dispatches", {
      method: "POST",
      ...json(body),
    }),
  confirmDispatch: (id: string, key: string) =>
    api(`/headquarters/dispatches/${id}/confirm`, {
      method: "POST",
      ...json({ idempotencyKey: key }),
    }),
  createTransfer: (body: unknown) =>
    api<{ id: string }>("/headquarters/transfers", {
      method: "POST",
      ...json(body),
    }),
  confirmTransfer: (id: string, key: string) =>
    api(`/headquarters/transfers/${id}/confirm`, {
      method: "POST",
      ...json({ idempotencyKey: key }),
    }),
  createReturn: (body: unknown) =>
    api<{ id: string }>("/headquarters/returns", {
      method: "POST",
      ...json(body),
    }),
  confirmReturn: (id: string, key: string) =>
    api(`/headquarters/returns/${id}/confirm`, {
      method: "POST",
      ...json({ idempotencyKey: key }),
    }),
  reconcile: () =>
    api<{ consistent: boolean; mismatches: number }>(
      "/headquarters/reconcile",
      { method: "POST" },
    ),
};
