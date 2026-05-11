import { apiGet, apiPost, USE_MOCK } from "./client";
import { mockListAirports, mockCreateAirport } from "./mock";

export const listAirports = () =>
  USE_MOCK ? mockListAirports() : apiGet("/airports");

export const createAirport = (payload) =>
  USE_MOCK ? mockCreateAirport(payload) : apiPost("/airports", payload);
