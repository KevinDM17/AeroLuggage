let routes = [];
let running = false;
let rafTimer = 0;
let simTimeAnchor = null;
let realAnchor = 0;

const TICK_INTERVAL_MS = 33;

function tick() {
  if (!running) return;

  const now = performance.now();
  const realElapsed = now - realAnchor;
  const simTime = simTimeAnchor !== null ? simTimeAnchor + realElapsed : null;

  const planes = [];
  for (let i = 0; i < routes.length; i++) {
    const r = routes[i];
    if (r.depMs == null || r.arrMs == null || simTime == null) continue;
    const duration = r.arrMs - r.depMs;
    if (duration <= 0) continue;
    const progress = (simTime - r.depMs) / duration;
    if (progress < 0 || progress >= 1) continue;
    planes.push({
      id: r.id,
      lng: r.oLng + r.dLng * progress,
      lat: r.oLat + r.dLat * progress,
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
      simTimeAnchor = typeof msg.simTime === 'number' && Number.isFinite(msg.simTime)
        ? msg.simTime : null;
      realAnchor = performance.now();
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
      self.postMessage({ type: 'positions', planes: [] });
      break;
  }
};
