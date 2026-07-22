import { apiGet, apiPost, apiPut, apiDelete, USE_MOCK } from "./client";
import {
  mockListFlights,
  mockListFlightsByAirport,
  mockCreateFlight,
  mockCancelFlight,
  mockUpdateFlight,
  mockDeleteFlightPlan,
  mockBulkUploadFlights,
} from "./mock";

const adaptFlight = (f) => ({
  id:       f.codigo ?? f.idVueloProgramado,
  idVueloProgramado: f.idVueloProgramado ?? f.codigo,
  origin:   f.idAeropuertoOrigen
    ?? (typeof f.aeropuertoOrigen === "string" ? f.aeropuertoOrigen : f?.aeropuertoOrigen?.idAeropuerto)
    ?? "",
  dest:     f.idAeropuertoDestino
    ?? (typeof f.aeropuertoDestino === "string" ? f.aeropuertoDestino : f?.aeropuertoDestino?.idAeropuerto)
    ?? "",
  destCity: f.nombreCiudadDestino ?? "",
  destCont: f.continenteDestino ?? "",
  depTime:  f.horaSalida ?? "",
  arrTime:  f.horaLlegada ?? "",
  gmtOrigin: f.husoOrigen ?? 0,
  gmtDest:   f.husoDestino ?? 0,
  capacity: f.capacidadBase ?? f.capacidadMaxima ?? 0,
  used:     f.capacidadUsada ?? 0,
  status:   f.estado ?? "Programado",
});

const adaptFlightToBack = (payload) => ({
  codigo:            payload.id,
  horaSalida:        payload.depTime,
  horaLlegada:       payload.arrTime,
  capacidadBase:     payload.capacity,
  idAeropuertoOrigen: payload.origin,
  idAeropuertoDestino: payload.dest,
});

export const listFlightPlans = async (airport) => {
  if (USE_MOCK) return airport ? mockListFlightsByAirport(airport) : mockListFlights();
  const path = airport
    ? `/vuelos-programados?airport=${encodeURIComponent(airport)}`
    : "/vuelos-programados";
  const data = await apiGet(path);
  return Array.isArray(data) ? data.map(adaptFlight) : [];
};

export const createFlightPlan = (payload) =>
  USE_MOCK ? mockCreateFlight(payload) : apiPost("/vuelos-programados", adaptFlightToBack(payload));

export const updateFlightPlan = (id, payload) =>
  USE_MOCK ? mockUpdateFlight(id, payload) : apiPut(`/vuelos-programados/${encodeURIComponent(id)}`, adaptFlightToBack(payload));

export const deleteFlightPlan = (id) =>
  USE_MOCK ? mockDeleteFlightPlan(id) : apiDelete(`/vuelos-programados/${encodeURIComponent(id)}`);

export const bulkUploadFlights = (textContent) =>
  USE_MOCK ? mockBulkUploadFlights(textContent) : apiPost("/vuelos-programados/bulk", { content: textContent });

export const listFlights = (airport) => listFlightPlans(airport);

export const cancelFlight = (id) =>
  USE_MOCK ? mockCancelFlight(id) : apiDelete(`/vuelos-programados/${encodeURIComponent(id)}`);
