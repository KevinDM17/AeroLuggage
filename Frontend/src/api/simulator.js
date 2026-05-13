import { apiPost, USE_MOCK } from "./client";
import {
  mockStartPeriodSim,
  mockStopPeriodSim,
  mockGetPeriodSimState,
  mockStartCollapseSim,
  mockStopCollapseSim,
  mockGetCollapseSimState,
} from "./mock";

/* =========================================================================
 * SIMULACION POR PERIODO
 * Back: SimulacionRestController.iniciar + SimulacionController (STOMP)
 * =========================================================================
 *
 * REST (lo que el front llama directo):
 *   POST /api/simulacion/periodo/iniciar
 *     Body: SimulacionIniciarDTO { fechaInicio: "YYYY-MM-DD", totalDias, duracionDiaSimuladoMs }
 *     Response: SimulacionInicioResponse {
 *       sessionId, estado, mensaje, fechaInicio, totalDias, aeropuertos, vuelosInstancia
 *     }
 *
 * WebSocket (STOMP) — manejado por hooks/useStomp:
 *   SEND /app/simulacion/periodo/{pausar|reanudar|detener}   con { sessionId }
 *   SUB  /topic/simulacion/{sessionId}                       -> PeriodoTickDTO
 *   SUB  /topic/simulacion/{sessionId}/estado                -> SimulacionEstadoDTO
 */

export const iniciarSimulacionPeriodo = (payload) =>
  USE_MOCK
    ? mockStartPeriodSim(payload.fechaInicio)
    : apiPost("/simulacion/periodo/iniciar", payload);

/* Wrapper legacy para no romper imports actuales. */
export const startPeriodSim = (startDate) =>
  iniciarSimulacionPeriodo({
    fechaInicio: startDate,
    totalDias: 5,
    duracionDiaSimuladoMs: 1000,
  });

/* Stop/state legacy: en modo real ya no se usan; pausar/reanudar/detener van
 * por WS y el estado llega por suscripcion. Mantengo el shim para mocks. */
export const stopPeriodSim = () =>
  USE_MOCK ? mockStopPeriodSim() : Promise.reject(new Error("Usar WS /app/simulacion/periodo/detener"));

export const getPeriodSimState = () =>
  USE_MOCK ? mockGetPeriodSimState() : Promise.reject(new Error("Usar WS /topic/simulacion/{sessionId}/estado"));

/* =========================================================================
 * SIMULACION HASTA COLAPSO
 * Back: NO IMPLEMENTADO TODAVIA en esta rama (existe solo el ColapsoTickDTO).
 * Cuando el back exponga endpoints, replicamos el patron de periodo.
 * ========================================================================= */

export const iniciarSimulacionColapso = (payload) =>
  USE_MOCK
    ? mockStartCollapseSim(payload.fechaInicio)
    : apiPost("/simulacion/colapso/iniciar", payload);

export const startCollapseSim = (startDate) =>
  iniciarSimulacionColapso({ fechaInicio: startDate, intervaloTickMs: 1000 });

export const stopCollapseSim = () =>
  USE_MOCK ? mockStopCollapseSim() : Promise.reject(new Error("Pendiente en back"));

export const getCollapseSimState = () =>
  USE_MOCK ? mockGetCollapseSimState() : Promise.reject(new Error("Pendiente en back"));
