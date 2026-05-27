/**
 * Parser de bodies STOMP con fallback a Web Worker para payloads pesados.
 *
 *   parseStompBody(body) -> Promise<object>
 *
 * Decision de ruteo:
 *  - Si el body es < WORKER_THRESHOLD_BYTES, parsea inline (sync wrapper en
 *    Promise.resolve). El overhead de postMessage no compensa para mensajes
 *    pequenos (light ticks pesan < 10 KB).
 *  - Si es >=, lo manda al worker. Asi un snapshot pesado de cientos de KB
 *    NO bloquea el main thread durante el JSON.parse.
 *
 * El worker es singleton (uno por pestana). Si la creacion falla (entornos
 * sin Web Worker), caemos a parse inline silenciosamente.
 */

import ParserWorker from "./stomp-parser.worker.js?worker";

// 30 KB: punto donde el costo de postMessage roundtrip iguala al ahorro de
// no bloquear el main thread. Por arriba conviene worker, por abajo inline.
const WORKER_THRESHOLD_BYTES = 30 * 1024;

let _worker = null;
let _counter = 0;
const _pending = new Map();

function getWorker() {
  if (_worker) return _worker;
  try {
    _worker = new ParserWorker();
    _worker.onmessage = (event) => {
      const { id, data, error } = event.data || {};
      const cb = _pending.get(id);
      if (!cb) return;
      _pending.delete(id);
      if (error) cb(new Error(error), null);
      else cb(null, data);
    };
    _worker.onerror = (event) => {
      // Si el worker explota, rechazamos TODOS los pendientes y lo reseteamos.
      const err = new Error(`stomp parser worker error: ${event.message || "unknown"}`);
      for (const cb of _pending.values()) cb(err, null);
      _pending.clear();
      _worker = null;
    };
  } catch {
    _worker = null;
  }
  return _worker;
}

export function parseStompBody(body) {
  if (typeof body !== "string" || body.length === 0) {
    return Promise.resolve(null);
  }
  // Inline para payloads chicos: cero overhead.
  if (body.length < WORKER_THRESHOLD_BYTES) {
    try {
      return Promise.resolve(JSON.parse(body));
    } catch (err) {
      return Promise.reject(err);
    }
  }
  const worker = getWorker();
  if (!worker) {
    // Fallback: si no hay worker, parseamos inline (peor para fluidez pero
    // funcionalmente correcto).
    try {
      return Promise.resolve(JSON.parse(body));
    } catch (err) {
      return Promise.reject(err);
    }
  }
  return new Promise((resolve, reject) => {
    const id = ++_counter;
    _pending.set(id, (err, data) => {
      if (err) reject(err);
      else resolve(data);
    });
    worker.postMessage({ id, body });
  });
}
