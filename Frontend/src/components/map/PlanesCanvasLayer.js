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
    this._routesRef = null;
    this._simClockRef = null;
    this._animating = false;
    this._raf = null;
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
    this._stopLoop();
    if (this._canvas) L.DomUtil.remove(this._canvas);
    map.off("moveend", this._reset, this);
    map.off("zoomend", this._reset, this);
    map.off("resize", this._reset, this);
    map.off("zoomanim", this._animateZoom, this);
    this._canvas = null;
    this._ctx = null;
  },

  /**
   * API IMPERATIVA — el componente React entrega refs estables al layer y
   * este maneja su propio requestAnimationFrame internamente. Asi la
   * animacion sobrevive a re-renders de React y a desmontajes parciales
   * del arbol siempre que el layer no se remueva del mapa.
   */
  setSources(routesRef, simClockRef) {
    this._routesRef = routesRef || null;
    this._simClockRef = simClockRef || null;
    // Si no estamos animando, dibujamos un frame fijo con la posicion actual
    // (util para mostrar planos congelados en pausa o despues de un tick).
    if (!this._animating) {
      this._computeAndDraw();
    }
  },

  setAnimating(animating) {
    const next = Boolean(animating);
    if (next === this._animating) return;
    this._animating = next;
    if (next) {
      this._startLoop();
    } else {
      this._stopLoop();
      this._computeAndDraw(); // un frame final con posiciones congeladas
    }
  },

  /**
   * Legacy: API push (lista pre-computada de planos). Se mantiene por
   * compatibilidad pero el camino preferido es setSources + setAnimating.
   */
  setPlanes(planes) {
    this._planes = planes || [];
    this._draw();
  },

  _startLoop() {
    if (this._raf) return;
    const loop = () => {
      this._computeAndDraw();
      this._raf = requestAnimationFrame(loop);
    };
    this._raf = requestAnimationFrame(loop);
  },

  _stopLoop() {
    if (this._raf) {
      cancelAnimationFrame(this._raf);
      this._raf = null;
    }
  },

  /* Computa las posiciones de los aviones contra el reloj simulado y dibuja.
   * Vive enteramente dentro del layer: no toca React. */
  _computeAndDraw() {
    const routes = this._routesRef ? this._routesRef.current : null;
    const clock = this._simClockRef ? this._simClockRef.current : null;
    if (!routes || routes.length === 0 || !clock || clock.baseSimMs == null) {
      if (this._planes.length !== 0) {
        this._planes = [];
      }
      this._draw();
      return;
    }
    const speed = clock.speed || 1;
    const simNow = clock.running
      ? clock.baseSimMs + (performance.now() - clock.baseRealMs) * speed
      : clock.baseSimMs;

    const out = [];
    for (let i = 0; i < routes.length; i++) {
      const geo = routes[i];
      const total = geo.arrMs - geo.depMs;
      if (total <= 0) continue;
      const p = (simNow - geo.depMs) / total;
      if (p < 0 || p > 1) continue;
      out.push({
        lat: geo.origin.lat + geo.dLat * p,
        lng: geo.origin.lng + geo.dLng * p,
        angle: geo.planeAngle,
        color: geo.color,
      });
    }
    this._planes = out;
    this._draw();
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

    /* Extraemos los limites como numeros una vez por frame: evita crear
     * objetos LatLng dentro de bounds.contains() para cada avion. Con N
     * aviones a 60 Hz son N*60 allocations menos por segundo. */
    const bounds = map.getBounds();
    const minLat = bounds.getSouth();
    const maxLat = bounds.getNorth();
    const minLng = bounds.getWest();
    const maxLng = bounds.getEast();
    const half = PLANE_DRAW_SIZE / 2;
    const planes = this._planes;
    const len = planes.length;

    for (let i = 0; i < len; i++) {
      const plane = planes[i];
      const lat = plane.lat;
      const lng = plane.lng;

      // Virtualizacion: chequeo numerico inline (sin objetos LatLng).
      if (lat < minLat || lat > maxLat || lng < minLng || lng > maxLng) continue;

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
