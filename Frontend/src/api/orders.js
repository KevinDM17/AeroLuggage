import { apiGet, apiPost, USE_MOCK } from "./client";
import { mockListOrders, mockCreateOrder } from "./mock";

export const listOrders = () =>
  USE_MOCK ? mockListOrders() : apiGet("/orders");

export const createOrder = (payload) =>
  USE_MOCK ? mockCreateOrder(payload) : apiPost("/orders", payload);
