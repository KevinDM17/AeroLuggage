import L from "leaflet";

/**
 * Layer de Leaflet que dibuja todos los aviones en UN solo <canvas>
 * superpuesto al mapa. Reemplaza N markers DOM por 1 elemento DOM,
 * eliminando el costo de reconciliación / layout / paint del navegador
 * cuando hay decenas o cientos de aviones moviéndose.
 *
 * Bonus: virtualiza gratis los aviones fuera del viewport (skip draw).
 */

// Path del avión (mismo SVG que se usaba en los divIcons originales).
const PLANE_SVG_PATH =
  "M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.2-1.1.5l-1.3 1.5c-.3.4-.1 1 .4 1.2L9 12l-4 4-2.2-.6c-.4-.1-.8.1-1.1.4l-.8.8c-.3.4-.1 1 .4 1.2l4 1.5 1.5 4c.2.5.8.7 1.2.4l.8-.8c.3-.3.5-.7.4-1.1L8 19l4-4 2.6 6.2c.2.5.8.7 1.2.4l1.5-1.3c.3-.2.6-.6.5-1.1z";

const PLANE_SVG_VIEWBOX = 24;
const PLANE_DRAW_SIZE = 20;

// Cache de canvas offscreen pre-rasterizados por color.
// El SVG se "pinta" una sola vez por color y luego solo hacemos
// drawImage() en cada frame, lo cual es muy barato.
const offscreenCache = new Map();

function getOffscreenPlane(color) {
  const cached = offscreenCache.get(color);
  if (cached) return cached;

  // Renderizamos a 2x para mantener nitidez en pantallas hi-DPI.
  const upscale = 2;
  const size = PLANE_DRAW_SIZE * upscale;

  const canvas =
    typeof OffscreenCanvas !== "undefined"
      ? new OffscreenCanvas(size, size)
      : Object.assign(document.createElement("canvas"), { width: size, height: size });
  // Asegurar dimensiones para canvas HTML normal
  canvas.width = size;
  canvas.height = size;

  const ctx = canvas.getContext("2d");
  if (!ctx) return null;

  ctx.fillStyle = color;
  ctx.strokeStyle = color;
  ctx.lineWidth = 1;
  ctx.lineCap = "round";
  ctx.lineJoin = "round";

  ctx.scale(size / PLANE_SVG_VIEWBOX, size / PLANE_SVG_VIEWBOX);
  const path = new Path2D(PLANE_SVG_PATH);
  ctx.fill(path);
  ctx.stroke(path);

  offscreenCache.set(color, canvas);
  return canvas;
}

export const PlanesCanvasLayer = L.Layer.extend({
  initialize(options = {}) {
    L.setOptions(this, options);
    this._planes = [];
    this._routesGeometry = [];
    this._progresses = [];
    this._animationFrame = 0;
    this._animationActive = false;
  },

  onAdd(map) {
    this._map = map;

    const pane = map.getPane(this.options.pane || "overlayPane");
    const canvas = L.DomUtil.create("canvas", "leaflet-planes-canvas-layer");
    canvas.style.position = "absolute";
    canvas.style.pointerEvents = "none";
    canvas.style.willChange = "transform";
    pane.appendChild(canvas);

    this._canvas = canvas;
    this._ctx = canvas.getContext("2d");

    map.on("moveend", this._reset, this);
    map.on("zoomend", this._reset, this);
    map.on("resize", this._reset, this);
    map.on("zoomanim", this._animateZoom, this);

    this._reset();
    return this;
  },

  onRemove(map) {
    this.stopAnimation();
    const targetMap = map || this._map;
    if (targetMap) {
      targetMap.off("moveend", this._reset, this);
      targetMap.off("zoomend", this._reset, this);
      targetMap.off("resize", this._reset, this);
      targetMap.off("zoomanim", this._animateZoom, this);
    }
    if (this._canvas?.parentNode) L.DomUtil.remove(this._canvas);
    this._canvas = null;
    this._ctx = null;
    this._map = null;
  },

  /**
   * Llamado desde React con la lista de aviones a dibujar.
   * planes: [{ lat, lng, angle, color, key? }]
   */
  setPlanes(planes) {
    if (!this._map || !this._canvas) return;
    this._planes = planes || [];
    this._draw();
  },

  startAnimation(routesGeometry = []) {
    this.stopAnimation();
    this._routesGeometry = routesGeometry;
    this._progresses = routesGeometry.map(() => Math.random());

    if (!this._map || !this._canvas || routesGeometry.length === 0) {
      this.setPlanes([]);
      return;
    }

    this._animationActive = true;
    let lastTime = performance.now();
    let acc = 0;
    const frameBudgetMs = 33;

    const tick = (time) => {
      if (!this._animationActive) return;

      const dt = time - lastTime;
      lastTime = time;
      acc += dt;

      for (let i = 0; i < this._progresses.length; i++) {
        this._progresses[i] = (this._progresses[i] + dt * 0.00005) % 1;
      }

      if (acc >= frameBudgetMs) {
        acc = 0;
        this.setPlanes(this._buildPlaneSnapshots());
      }

      this._animationFrame = requestAnimationFrame(tick);
    };

    this.setPlanes(this._buildPlaneSnapshots());
    this._animationFrame = requestAnimationFrame(tick);
  },

  stopAnimation() {
    this._animationActive = false;
    if (this._animationFrame) {
      cancelAnimationFrame(this._animationFrame);
      this._animationFrame = 0;
    }
    this._routesGeometry = [];
    this._progresses = [];
    this._planes = [];
    this._draw();
  },

  _buildPlaneSnapshots() {
    return this._routesGeometry.map((geo, index) => {
      const progress = this._progresses[index] ?? 0;
      return {
        lat: geo.origin.lat + geo.dLat * progress,
        lng: geo.origin.lng + geo.dLng * progress,
        angle: geo.planeAngle,
        color: geo.color,
      };
    });
  },

  _reset() {
    if (!this._canvas || !this._map) return;

    const size = this._map.getSize();
    const topLeft = this._map.containerPointToLayerPoint([0, 0]);
    L.DomUtil.setPosition(this._canvas, topLeft);

    const dpr = window.devicePixelRatio || 1;
    this._canvas.width = Math.max(1, Math.floor(size.x * dpr));
    this._canvas.height = Math.max(1, Math.floor(size.y * dpr));
    this._canvas.style.width = size.x + "px";
    this._canvas.style.height = size.y + "px";
    this._ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    this._draw();
  },

  _animateZoom(e) {
    if (!this._canvas || !this._map) return;
    // Mantener el canvas alineado mientras el mapa hace zoom animado.
    const scale = this._map.getZoomScale(e.zoom, this._map.getZoom());
    const offset = this._map
      ._latLngBoundsToNewLayerBounds(this._map.getBounds(), e.zoom, e.center)
      .min;
    L.DomUtil.setTransform(this._canvas, offset, scale);
  },

  _draw() {
    const ctx = this._ctx;
    const map = this._map;
    if (!ctx || !map) return;

    const size = map.getSize();
    ctx.clearRect(0, 0, size.x, size.y);

    const bounds = map.getBounds();
    const half = PLANE_DRAW_SIZE / 2;

    for (let i = 0; i < this._planes.length; i++) {
      const plane = this._planes[i];
      const lat = plane.lat;
      const lng = plane.lng;

      // Virtualización gratis: si el avión no está visible, no lo dibujamos.
      if (!bounds.contains([lat, lng])) continue;

      const offscreen = getOffscreenPlane(plane.color);
      if (!offscreen) continue;

      const point = map.latLngToContainerPoint([lat, lng]);
      const rad = (plane.angle * Math.PI) / 180;

      ctx.save();
      ctx.translate(point.x, point.y);
      ctx.rotate(rad);
      ctx.drawImage(offscreen, -half, -half, PLANE_DRAW_SIZE, PLANE_DRAW_SIZE);
      ctx.restore();
    }
  },
});

export function createPlanesCanvasLayer(options) {
  return new PlanesCanvasLayer(options);
}
