/**
 * Cliente STOMP/SockJS singleton para hablar con el back de AeroLuggage.
 *
 * Convencion del back (definida en WebSocketConfig.java):
 * - Endpoint:          ${VITE_WS_BASE_URL}/ws  (SockJS handshake)
 * - App prefix (send): /app/...     -> el front publica acá
 * - Topic prefix (sub): /topic/...  -> el front se suscribe acá
 *
 * Topics implementados hoy en el back:
 * - POST  /api/simulacion/periodo/iniciar             (REST, devuelve sessionId)
 * - SEND  /app/simulacion/periodo/{pausar|reanudar|detener}  con { sessionId }
 * - SUB   /topic/simulacion/{sessionId}               -> PeriodoTickDTO
 * - SUB   /topic/simulacion/{sessionId}/estado        -> SimulacionEstadoDTO
 */

import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { USE_MOCK } from "./client";

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL ?? "http://localhost:8080";
const WS_ENDPOINT = `${WS_BASE_URL}/ws`;

let _client = null;
let _connected = false;
const _waiters = new Set();

function notifyWaiters() {
  _waiters.forEach((cb) => cb());
  _waiters.clear();
}

export function getStompClient() {
  if (USE_MOCK) {
    // En modo mock no conectamos. Los componentes que dependen de WS deben
    // chequear `isStompEnabled()` antes de suscribirse.
    return null;
  }
  if (_client) return _client;

  _client = new Client({
    webSocketFactory: () => new SockJS(WS_ENDPOINT),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {},
    onConnect: () => {
      _connected = true;
      notifyWaiters();
    },
    onWebSocketClose: () => { _connected = false; },
    onStompError: (frame) => {
      console.error("[STOMP] error", frame.headers["message"], frame.body);
    },
  });
  _client.activate();
  return _client;
}

export function isStompEnabled() {
  return !USE_MOCK;
}

export function isStompConnected() {
  return _connected;
}

/** Devuelve una promesa que se resuelve cuando el client este conectado. */
export function whenConnected() {
  const client = getStompClient();
  if (!client) return Promise.reject(new Error("STOMP deshabilitado (USE_MOCK)"));
  if (_connected) return Promise.resolve(client);
  return new Promise((resolve) => {
    _waiters.add(() => resolve(client));
  });
}
