/**
 * Worker que ticka la simulación de aviones fuera del hilo principal.
 * Recibe la geometría de rutas y emite posiciones a ~60fps. El main thread
 * solo recibe el snapshot ya calculado y lo pasa a deck.gl.
 *
 * Protocolo:
 *   main → worker: { type: 'init', routes: [{ id, oLng, oLat, dLng, dLat, angle, color }] }
 *   main → worker: { type: 'pause' } | { type: 'resume' } | { type: 'stop' }
 *   worker → main: { type: 'positions', planes: [{ id, lng, lat, angle, color }] }
 */

let routes = [];
let progresses = [];
let running = false;
let rafTimer = 0;

const TICK_INTERVAL_MS = 33; // ~30fps — buen balance entre fluidez visual y CPU.
const PROGRESS_PER_MS = 0.00005; // misma velocidad que el código viejo

let lastTime = 0;

function tick() {
  if (!running) return;

  const now = performance.now();
  const dt = lastTime ? now - lastTime : TICK_INTERVAL_MS;
  lastTime = now;

  const planes = new Array(routes.length);
  for (let i = 0; i < routes.length; i++) {
    progresses[i] = (progresses[i] + dt * PROGRESS_PER_MS) % 1;
    const r = routes[i];
    const p = progresses[i];
    planes[i] = {
      id: r.id,
      lng: r.oLng + r.dLng * p,
      lat: r.oLat + r.dLat * p,
      angle: r.angle,
      color: r.color,
    };
  }

  self.postMessage({ type: 'positions', planes });
  rafTimer = setTimeout(tick, TICK_INTERVAL_MS);
}

function startLoop() {
  if (running) return;
  running = true;
  lastTime = 0;
  tick();
}

function stopLoop() {
  running = false;
  if (rafTimer) {
    clearTimeout(rafTimer);
    rafTimer = 0;
  }
}

self.onmessage = (e) => {
  const msg = e.data;
  switch (msg.type) {
    case 'init':
      routes = msg.routes || [];
      progresses = routes.map(() => Math.random());
      if (routes.length === 0) {
        stopLoop();
        self.postMessage({ type: 'positions', planes: [] });
      } else {
        startLoop();
      }
      break;
    case 'pause':
      stopLoop();
      break;
    case 'resume':
      if (routes.length > 0) startLoop();
      break;
    case 'stop':
      stopLoop();
      routes = [];
      progresses = [];
      self.postMessage({ type: 'positions', planes: [] });
      break;
  }
};
