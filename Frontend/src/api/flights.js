import { apiGet, apiPost, apiDelete, USE_MOCK } from "./client";
import {
  mockListFlights,
  mockCreateFlight,
  mockCancelFlight,
  mockBulkUploadFlights,
} from "./mock";

/**
 * Back -> Front:
 *   { idVueloProgramado, codigo, horaSalida, horaLlegada, capacidadBase,
 *     aeropuertoOrigen, aeropuertoDestino }  (los aeropuertos vienen como ICAO)
 * ->
 *   { id, origin, dest, depTime, arrTime, capacity, used, status }
 */
const adaptFlight = (f) => ({
  id:       f.codigo ?? f.idVueloProgramado,
  origin:   typeof f.aeropuertoOrigen === "string"  ? f.aeropuertoOrigen  : f?.aeropuertoOrigen?.idAeropuerto  ?? "",
  dest:     typeof f.aeropuertoDestino === "string" ? f.aeropuertoDestino : f?.aeropuertoDestino?.idAeropuerto ?? "",
  depTime:  f.horaSalida ?? "",
  arrTime:  f.horaLlegada ?? "",
  capacity: f.capacidadBase ?? f.capacidadMaxima ?? 0,
  used:     f.capacidadUsada ?? 0,
  status:   f.estado ?? "Programado",
});

const adaptFlightToBack = (payload) => ({
  codigo:            payload.id,
  horaSalida:        payload.depTime,
  horaLlegada:       payload.arrTime,
  capacidadBase:     payload.capacity,
  aeropuertoOrigen:  payload.origin,
  aeropuertoDestino: payload.dest,
});

export const listFlights = async () => {
  if (USE_MOCK) return mockListFlights();
  const data = await apiGet("/vuelos");
  return Array.isArray(data) ? data.map(adaptFlight) : [];
};

export const createFlight = (payload) =>
  USE_MOCK ? mockCreateFlight(payload) : apiPost("/vuelos", adaptFlightToBack(payload));

export const cancelFlight = (id) =>
  USE_MOCK ? mockCancelFlight(id) : apiDelete(`/vuelos/${id}`);

export const bulkUploadFlights = (textContent) =>
  USE_MOCK ? mockBulkUploadFlights(textContent) : apiPost("/vuelos/bulk", { content: textContent });
