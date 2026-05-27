/**
 * Web Worker que parsea bodies de mensajes STOMP fuera del main thread.
 *
 * Los snapshots pesados de la simulacion (pedidos + maletas + rutas) pueden
 * pesar varios cientos de KB. Hacer `JSON.parse` en el main thread bloquea
 * el rAF del mapa por 30-50 ms y se ve como hiccup. Aca lo hacemos en un
 * worker dedicado: el main thread queda libre para animar a 60-120 fps sin
 * pausas, y solo recibimos el objeto ya parseado.
 *
 * Protocolo:
 *   main -> worker: { id: number, body: string }
 *   worker -> main: { id: number, data: object }     (exito)
 *                or { id: number, error: string }    (falla)
 */

self.onmessage = (e) => {
  const { id, body } = e.data || {};
  if (typeof id !== "number" || typeof body !== "string") return;
  try {
    const data = JSON.parse(body);
    self.postMessage({ id, data });
  } catch (err) {
    self.postMessage({ id, error: err && err.message ? err.message : String(err) });
  }
};
