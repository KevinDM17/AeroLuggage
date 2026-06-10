import { apiGet, apiPost, USE_MOCK } from "./client";
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
 *     Body: SimulacionIniciarDTO { fechaInicio: "YYYY-MM-DD", totalDias }
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
    ? mockStartPeriodSim(payload.fechaHoraInicio ?? payload.fechaInicio)
    : apiPost("/simulacion/periodo/iniciar", payload);

export const obtenerBaseSimulacion = (sessionId) =>
  apiGet(`/simulacion/periodo/${sessionId}/base`);

export const obtenerVentanaSimulacion = (sessionId, windowId) =>
  apiGet(`/simulacion/periodo/${sessionId}/ventana/${windowId}`);

export const obtenerVuelosSimulacion = (sessionId, desde, hasta) =>
  apiGet(`/simulacion/periodo/${sessionId}/vuelos?desde=${desde}&hasta=${hasta}`);

/* Manifiesto de una UT: envios (pedidos) y productos (maletas) que traslada.
 * Lo calcula el back desde las rutas globales de la sesion. */
export const obtenerManifiestoVuelo = (sessionId, idVuelo) =>
  apiGet(`/simulacion/periodo/${sessionId}/vuelo/${encodeURIComponent(idVuelo)}/manifiesto`);

/* Contenido de un almacen: envios (pedidos) y productos (maletas) que estan en
 * el aeropuerto, separados en destino final vs en transito. */
export const obtenerContenidoAlmacen = (sessionId, idAeropuerto) =>
  apiGet(`/simulacion/periodo/${sessionId}/almacen/${encodeURIComponent(idAeropuerto)}/contenido`);

/* Envios del panel agrupados: planificados / en vuelos / entregados ultimas 4h,
 * cada uno con su UT (vuelos) y los aeropuertos de su ruta. */
export const obtenerEnviosPanel = (sessionId) =>
  apiGet(`/simulacion/periodo/${sessionId}/envios`);

/* Ruta (con escalas) de una maleta por su ID, para resaltarla en el mapa. */
export const obtenerRutaMaleta = (sessionId, idMaleta) =>
  apiGet(`/simulacion/periodo/${sessionId}/maleta/${encodeURIComponent(idMaleta)}/ruta`);

/* Rutas (una por maleta) de un envio por su ID. */
export const obtenerRutasEnvio = (sessionId, idPedido) =>
  apiGet(`/simulacion/periodo/${sessionId}/envio/${encodeURIComponent(idPedido)}/rutas`);

export const obtenerSnapshotSimulacionPeriodo = (sessionId) =>
  apiGet(`/simulacion/periodo/${sessionId}/snapshot`);

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
  iniciarSimulacionColapso({ fechaInicio: startDate, intervaloTickMs: 500 });

export const stopCollapseSim = () =>
  USE_MOCK ? mockStopCollapseSim() : Promise.reject(new Error("Pendiente en back"));

export const getCollapseSimState = () =>
  USE_MOCK ? mockGetCollapseSimState() : Promise.reject(new Error("Pendiente en back"));
