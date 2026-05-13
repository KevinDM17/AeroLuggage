import { apiGet, USE_MOCK } from "./client";
import { mockListRutas } from "./mock";

/**
 * Lista de planes de ruta (output del Planificador).
 * Solo existen despues de correr una simulacion.
 *
 * Shape esperado:
 * {
 *   idRuta: string,
 *   idMaleta: string,
 *   plazoMaximoDias: number,
 *   duracion: number,                 // dias
 *   estado: "PLANIFICADA" | "EN_CURSO" | "COMPLETADA" | "FALLIDA",
 *   vuelos: Array<{
 *     idVueloInstancia: string,
 *     codigo: string,
 *     fechaSalida: string ISO,
 *     fechaLlegada: string ISO,
 *     aeropuertoOrigen: string ICAO,
 *     aeropuertoDestino: string ICAO,
 *   }>,
 * }
 */
export const listRutas = () =>
  USE_MOCK ? mockListRutas() : apiGet("/rutas");
