import { apiGet, apiPost, USE_MOCK, ApiError } from "./client";
import {
  mockStartPeriodSim,
  mockStopPeriodSim,
  mockGetPeriodSimState,
  mockStartCollapseSim,
  mockStopCollapseSim,
  mockGetCollapseSimState,
  mockIniciarDiaADia,
  mockDetenerDiaADia,
  mockProcesarPedidoDiaADia,
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
 * SIMULACION DIA A DIA
 * Back: SimulacionDiaADiaService + SimulacionDiaADiaRestController
 * =========================================================================
 *
 * REST:
 *   POST   /api/operations/iniciar
 *   POST   /api/operations/{sessionId}/detener
 *   POST   /api/operations/{sessionId}/pedido     Body: PedidoRequest
 *   GET    /api/operations/{sessionId}/estado
 *   GET    /api/operations/{sessionId}/vuelos
 *   GET    /api/operations/{sessionId}/vuelo/{id}/manifiesto
 *   GET    /api/operations/{sessionId}/maleta/{id}/ruta
 *   GET    /api/operations/{sessionId}/aeropuertos
 *   GET    /api/operations/{sessionId}/pedidos
 *   GET    /api/operations/{sessionId}/maletas
 *
 * WebSocket (STOMP):
 *   SUB   /topic/operations/{sessionId}            -> SimulacionTickLigeroDTO
 *   SUB   /topic/operations/{sessionId}/estado     -> SimulacionEstadoDTO
 *
 * Session management:
 *   The backend expires the session after 20s of inactivity.
 *   Internally we auto-detect 404 (session expired) and re-create
 *   the session transparently via withReconnect().
 */

let currentSessionId = null;
let creatingSession = null;
const sessionChangeListeners = new Set();

export function onSessionChange(callback) {
  sessionChangeListeners.add(callback);
  return () => sessionChangeListeners.delete(callback);
}

function notifySessionChange(sessionId) {
  for (const cb of sessionChangeListeners) {
    try { cb(sessionId); } catch {}
  }
}

async function checkActiveSession() {
  try {
    const result = await apiGet("/operations/estado-actual");
    return result;
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      return null;
    }
    throw err;
  }
}

async function getOrCreateSession() {
  if (currentSessionId) return currentSessionId;
  if (creatingSession) return creatingSession;
  creatingSession = (async () => {
    const activeSession = await checkActiveSession();
    if (activeSession && activeSession.activa) {
      currentSessionId = activeSession.sessionId;
      notifySessionChange(currentSessionId);
      return currentSessionId;
    }
    const result = USE_MOCK
      ? await mockIniciarDiaADia()
      : await apiPost("/operations/iniciar");
    currentSessionId = result.sessionId;
    notifySessionChange(currentSessionId);
    return currentSessionId;
  })();
  try {
    return await creatingSession;
  } finally {
    creatingSession = null;
  }
}

async function withReconnect(apiFn) {
  const sid = await getOrCreateSession();
  try {
    return await apiFn(sid);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      creatingSession = null;
      currentSessionId = null;
      const newSid = await getOrCreateSession();
      return await apiFn(newSid);
    }
    throw err;
  }
}

export const iniciarSimulacionDiaADia = async () => {
  const sid = await getOrCreateSession();
  return { sessionId: sid };
};

export const detenerSimulacionDiaADia = (sessionId) =>
  USE_MOCK
    ? mockDetenerDiaADia()
    : apiPost(`/operations/${sessionId}/detener`);

export const procesarPedidoDiaADia = (pedido) =>
  USE_MOCK
    ? mockProcesarPedidoDiaADia(pedido)
    : withReconnect((sid) => apiPost(`/operations/${sid}/pedido`, pedido));

export const obtenerEstadoDiaADia = () =>
  withReconnect((sid) => apiGet(`/operations/${sid}/estado`));

export const obtenerVuelosDiaADia = () =>
  withReconnect((sid) => apiGet(`/operations/${sid}/vuelos`));

export const obtenerAeropuertosDiaADia = () =>
  withReconnect((sid) => apiGet(`/operations/${sid}/aeropuertos`));

export const obtenerPedidosDiaADia = () =>
  withReconnect((sid) => apiGet(`/operations/${sid}/pedidos`));

export const obtenerMaletasDiaADia = () =>
  withReconnect((sid) => apiGet(`/operations/${sid}/maletas`));

export const obtenerManifiestoVueloDiaADia = (idVuelo) =>
  withReconnect((sid) => apiGet(`/operations/${sid}/vuelo/${encodeURIComponent(idVuelo)}/manifiesto`));

export const obtenerRutaMaletaDiaADia = (idMaleta) =>
  withReconnect((sid) => apiGet(`/operations/${sid}/maleta/${encodeURIComponent(idMaleta)}/ruta`));

export const obtenerRutasDiaADia = () =>
  withReconnect((sid) => apiGet(`/operations/${sid}/rutas`));

export const confirmarConexionDiaADia = () =>
  withReconnect((sid) => apiPost(`/operations/${sid}/confirmar-conexion`));

export const obtenerVuelosNuevosDiaADia = () =>
  withReconnect((sid) => apiGet(`/operations/${sid}/vuelos-nuevos`));

export const obtenerEstadoActualDiaADia = () =>
  apiGet("/operations/estado-actual");

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
