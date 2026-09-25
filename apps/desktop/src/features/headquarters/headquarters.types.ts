export type HqTab =
  "OVERVIEW" | "INVENTORY" | "PRODUCTIONS" | "MOVEMENTS" | "ATTENTION";
export type HqEquipment = {
  id: string;
  name: string;
  internalCode?: string;
  category?: string;
  trackingMode: "QUANTITY" | "SERIALIZED" | "CONSUMABLE";
  symbol: string;
  ownership: string;
  controlled: number;
  available: number;
  reserved: number;
  updatedAt: string;
};
export type HqPage<T> = {
  items: T[];
  page: number;
  size: number;
  total: number;
};
export type HqConfig = {
  categories: Array<{ id: string; name: string }>;
  units: Array<{
    id: string;
    name: string;
    symbol: string;
    decimalAllowed: boolean;
  }>;
  locations: Array<{
    id: string;
    name: string;
    locationType: string;
    productionId?: string;
  }>;
};
export type HqOverview = {
  controlled: number;
  available: number;
  reserved: number;
  deployed: number;
  attention: number;
  today: Array<{
    id: string;
    reference: string;
    status: string;
    scheduledAt?: string;
    type: string;
  }>;
  activeProductions: Array<{ id: string; title: string; quantity: number }>;
};
export type HqMovement = {
  id: string;
  movementType: string;
  equipment: string;
  quantity: number;
  symbol: string;
  sourceLocation?: string;
  destinationLocation?: string;
  production?: string;
  reason?: string;
  recordedBy: string;
  recordedAt: string;
};
export type HqAttention = {
  id: string;
  type: string;
  severity: "INFO" | "WARNING" | "CRITICAL";
  title: string;
  detail: string;
  createdAt: string;
};
