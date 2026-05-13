import { apiGet, apiPost, USE_MOCK } from "./client";
import { mockListOrders, mockCreateOrder } from "./mock";

/**
 * Back -> Front:
 *   { idPedido, aeropuertoOrigen, aeropuertoDestino, fechaRegistro,
 *     cantidadMaletas, estado }
 * ->
 *   { id, clientId, origin, dest, bags, date, time, status }
 */
const adaptOrder = (p) => {
  const fecha = p.fechaRegistro ?? "";
  const [date = "", timeFull = ""] = String(fecha).split("T");
  return {
    id:       p.idPedido,
    clientId: p.idCliente ?? p.clientId ?? "",
    origin:   typeof p.aeropuertoOrigen === "string"  ? p.aeropuertoOrigen  : p?.aeropuertoOrigen?.idAeropuerto  ?? "",
    dest:     typeof p.aeropuertoDestino === "string" ? p.aeropuertoDestino : p?.aeropuertoDestino?.idAeropuerto ?? "",
    bags:     p.cantidadMaletas ?? 0,
    date,
    time:     timeFull.slice(0, 5),
    status:   p.estado ?? "REGISTRADO",
  };
};

const adaptOrderToBack = (payload) => ({
  aeropuertoOrigen:  payload.origin,
  aeropuertoDestino: payload.dest,
  fechaRegistro:     `${payload.date}T${payload.time}:00`,
  cantidadMaletas:   payload.bags,
  idCliente:         payload.clientId,
});

export const listOrders = async () => {
  if (USE_MOCK) return mockListOrders();
  const data = await apiGet("/pedidos");
  return Array.isArray(data) ? data.map(adaptOrder) : [];
};

export const createOrder = (payload) =>
  USE_MOCK ? mockCreateOrder(payload) : apiPost("/pedidos", adaptOrderToBack(payload));
