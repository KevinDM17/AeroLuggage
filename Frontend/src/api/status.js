import { apiGet, USE_MOCK } from "./client";
import { mockGetStatus } from "./mock";

/**
 * Estado en tiempo real del sistema (KPIs globales + fecha/hora actual).
 * Polling cada VITE_POLL_INTERVAL_MS.
 *
 * Shape que consumen los componentes:
 *   { date, time, bagsInTransit, bagsDelivered, activeFlights }
 *
 * El back puede devolver:
 *   a) Mismos nombres en ingles (acordado en API_CONTRACT.md), o
 *   b) Nombres en espanol (maletasEnTransito, vuelosActivos, ...)
 * El adapter tolera ambos.
 */
const adaptStatus = (s) => {
  if (!s || typeof s !== "object") return s;
  return {
    date:            s.date ?? s.fechaActual ?? s.fecha ?? "",
    time:            s.time ?? s.horaActual  ?? s.hora  ?? "",
    bagsInTransit:   s.bagsInTransit   ?? s.maletasEnTransito   ?? 0,
    bagsDelivered:   s.bagsDelivered   ?? s.maletasEntregadas   ?? 0,
    activeFlights:   s.activeFlights   ?? s.vuelosActivos       ?? 0,
  };
};

export const getStatus = async () => {
  if (USE_MOCK) return mockGetStatus();
  const data = await apiGet("/status");
  return adaptStatus(data);
};
