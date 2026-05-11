import { apiGet, apiPost, apiDelete, USE_MOCK } from "./client";
import {
  mockListFlights,
  mockCreateFlight,
  mockCancelFlight,
  mockBulkUploadFlights,
} from "./mock";

export const listFlights = () =>
  USE_MOCK ? mockListFlights() : apiGet("/flights");

export const createFlight = (payload) =>
  USE_MOCK ? mockCreateFlight(payload) : apiPost("/flights", payload);

export const cancelFlight = (id) =>
  USE_MOCK ? mockCancelFlight(id) : apiDelete(`/flights/${id}`);

export const bulkUploadFlights = (textContent) =>
  USE_MOCK ? mockBulkUploadFlights(textContent) : apiPost("/flights/bulk", { content: textContent });
