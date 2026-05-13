import { apiGet, USE_MOCK } from "./client";
import { mockListMaletas } from "./mock";

/**
 * Lista de maletas en el sistema.
 * Shape esperado (alineado con el back):
 * {
 *   idMaleta: string,
 *   idPedido: string,
 *   fechaRegistro: string ISO,
 *   fechaLlegada: string ISO | null,
 *   estado: "EN_ALMACEN" | "EN_TRASLADO" | "EN_VUELO" | "ENTREGADA" | "EXTRAVIADA",
 *   ubicacionActual: string ICAO | null,   // null si esta en vuelo
 * }
 */
export const listMaletas = () =>
  USE_MOCK ? mockListMaletas() : apiGet("/maletas");
