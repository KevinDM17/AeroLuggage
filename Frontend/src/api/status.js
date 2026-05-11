import { apiGet, USE_MOCK } from "./client";
import { mockGetStatus } from "./mock";

/**
 * Estado en tiempo real del sistema (KPIs globales + fecha/hora actual).
 * Pensado para polling cada VITE_POLL_INTERVAL_MS.
 *
 * Shape esperado:
 * {
 *   date: "18-03-26",
 *   time: "12:34:16 UTC",
 *   bagsInTransit: number,
 *   activeFlights: number,
 *   freeCapacityPct: number,     // 0-100
 *   activeAlerts: number,
 * }
 */
export const getStatus = () =>
  USE_MOCK ? mockGetStatus() : apiGet("/status");
