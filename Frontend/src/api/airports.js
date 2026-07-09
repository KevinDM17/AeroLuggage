import { apiGet, apiPost, apiPut, USE_MOCK } from "./client";
import { mockListAirports, mockCreateAirport } from "./mock";

/**
 * Shape que recibimos del back (segun API_CONTRACT.md y la entidad Aeropuerto):
 *   {
 *     idAeropuerto: "SKBO",
 *     ciudad: { idCiudad, nombre, continente: "AMERICA_DEL_SUR" },
 *     capacidadAlmacen: 430,
 *     maletasActuales: 0,
 *     latitud: 4.701,
 *     longitud: -74.146,
 *     husoGMT: -5
 *   }
 *
 * Lo normalizamos al shape que ya consumen las pages/components:
 *   { iata, name, city, continent, gmt, capacity, used, lat, lng }
 */
export const normalizeContinente = (c) => {
  if (!c) return "";
  return c
    .toLowerCase()
    .split("_")
    .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
    .join(" ");
};

export const adaptAirport = (a) => ({
  iata:      a.idAeropuerto,
  name:      a.idAeropuerto,
  city:      a?.ciudad?.nombre ?? "",
  continent: normalizeContinente(a?.ciudad?.continente),
  gmt:       a.husoGMT ?? 0,
  capacity:  a.capacidadAlmacen ?? 0,
  used:      a.maletasActuales ?? 0,
  lat:       a.latitud,
  lng:       a.longitud,
});

const adaptAirportToBack = (payload) => ({
  idAeropuerto:     payload.iata,
  ciudad: { nombre: payload.city, continente: (payload.continent ?? "").toUpperCase().replace(/ /g, "_") },
  capacidadAlmacen: payload.capacity,
  maletasActuales:  payload.used ?? 0,
  husoGMT:          payload.gmt,
});

export const listAirports = async () => {
  if (USE_MOCK) return mockListAirports();
  const data = await apiGet("/aeropuertos");
  return Array.isArray(data) ? data.map(adaptAirport) : [];
};

export const getAirport = async (iata) => {
  if (USE_MOCK) return mockListAirports().find((a) => a.iata === iata) ?? null;
  const data = await apiGet(`/aeropuertos/${encodeURIComponent(iata)}`);
  return adaptAirport(data);
};

export const createAirport = (payload) =>
  USE_MOCK ? mockCreateAirport(payload) : apiPost("/aeropuertos", adaptAirportToBack(payload));

export const updateAirport = (iata, payload) =>
  USE_MOCK ? mockCreateAirport(payload) : apiPut(`/aeropuertos/${encodeURIComponent(iata)}`, adaptAirportToBack(payload));
