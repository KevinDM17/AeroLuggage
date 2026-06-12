import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { USE_MOCK } from "./client";

const WS_BASE_URL = import.meta.env.BACKEND_WS_BASE_URL ?? "http://localhost:8080";
const WS_ENDPOINT = `${WS_BASE_URL}/ws`;

let _client = null;
let _connected = false;
const _waiters = new Set();
let _connectionVersion = 0;
const _reconnectListeners = new Set();

function notifyWaiters() {
  _waiters.forEach((cb) => cb());
  _waiters.clear();
}

export function getStompClient() {
  if (USE_MOCK) {
    return null;
  }
  if (_client) return _client;

  _client = new Client({
    webSocketFactory: () => new SockJS(WS_ENDPOINT),
    reconnectDelay: Number(import.meta.env.VITE_WS_RECONNECT_DELAY_MS) || 5000,
    heartbeatIncoming: Number(import.meta.env.VITE_WS_HEARTBEAT_IN_MS) || 10000,
    heartbeatOutgoing: Number(import.meta.env.VITE_WS_HEARTBEAT_OUT_MS) || 10000,
    debug: () => {},
    onConnect: () => {
      _connected = true;
      _connectionVersion++;
      _reconnectListeners.forEach((fn) => fn());
      notifyWaiters();
    },
    onWebSocketClose: () => { _connected = false; },
    onStompError: () => {},
  });
  _client.activate();
  return _client;
}

export function subscribeToReconnects(fn) {
  _reconnectListeners.add(fn);
  return () => _reconnectListeners.delete(fn);
}

export function getConnectionVersion() {
  return _connectionVersion;
}

export function isStompEnabled() {
  return !USE_MOCK;
}

export function isStompConnected() {
  return _connected;
}

export function whenConnected() {
  const client = getStompClient();
  if (!client) return Promise.reject(new Error("STOMP deshabilitado (USE_MOCK)"));
  if (_connected) return Promise.resolve(client);
  return new Promise((resolve) => {
    _waiters.add(() => resolve(client));
  });
}
