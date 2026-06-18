let routes = [];
let running = false;
let rafTimer = 0;
let startMs = null;
let dayDurationMs = 86400000;
let realStartMs = 0;

const TICK_INTERVAL_MS = 33;

// Inversa de Web Mercator y → latitud (grados). Tiene que coincidir con la
// proyeccion que dibuja la LineLayer en deck.gl o el avion se desvia de la linea.
const invMercY = (y) => (2 * Math.atan(Math.exp(y)) - Math.PI / 2) * 180 / Math.PI;

function tick() {
  if (!running) return;

  const now = performance.now();
  const ratio = 86400000 / dayDurationMs;
  const simTime = startMs !== null ? startMs + (now - realStartMs) * ratio : null;

  const planes = [];
  for (let i = 0; i < routes.length; i++) {
    const r = routes[i];
    if (r.depMs == null || r.arrMs == null || simTime == null) continue;
    const duration = r.arrMs - r.depMs;
    if (duration <= 0) continue;
    const progress = (simTime - r.depMs) / duration;
    if (progress < 0 || progress >= 1) continue;
    // lng es lineal en Mercator; lat se interpola en mercY y se desproyecta.
    const lng = r.oLng + r.dLng * progress;
    const lat = (r.oMercY != null && r.dMercY != null)
      ? invMercY(r.oMercY + r.dMercY * progress)
      : r.oLat + r.dLat * progress;
    planes.push({
      id: r.id,
      lng,
      lat,
      angle: r.angle,
      color: r.color,
    });
  }

  self.postMessage({ type: 'positions', planes });
  rafTimer = setTimeout(tick, TICK_INTERVAL_MS);
}

function startLoop() {
  if (running) return;
  running = true;
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
      dayDurationMs = typeof msg.dayDurationMs === 'number' && msg.dayDurationMs > 0
        ? msg.dayDurationMs : 86400000;
      if (startMs == null) {
        startMs = typeof msg.simTime === 'number' && Number.isFinite(msg.simTime)
          ? msg.simTime : null;
        realStartMs = performance.now();
      }
      if (routes.length === 0) {
        stopLoop();
        self.postMessage({ type: 'positions', planes: [] });
      } else {
        startLoop();
      }
      break;
    case 'stop':
      stopLoop();
      routes = [];
      startMs = null;
      self.postMessage({ type: 'positions', planes: [] });
      break;
  }
};
