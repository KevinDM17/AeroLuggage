import { memo, useEffect, useMemo, useRef, useState } from "react";
import { MapContainer, TileLayer, Marker, useMap } from "react-leaflet";
import L from "leaflet";
import { LeafletLayer } from "deck.gl-leaflet";
import { IconLayer, PathLayer } from "@deck.gl/layers";
import { tokens, semaphoreColor } from "../../utils/tokens";
import { useFetch } from "../../hooks/useFetch";
import { listAirports } from "../../api/airports";
import { listFlights } from "../../api/flights";

/**
 * AirportMap con deck.gl (WebGL). Soporta dos modos:
 *  1) Modo "simulador real": el padre pasa airports/flights del snapshot del back
 *     + simulatedNowMs (reloj de la sim). Los aviones se muestran según los
 *     horarios reales (depTime/arrTime) y la hora simulada actual.
 *  2) Modo "decorativo": sin props de datos → useFetch interno + animación con
 *     progresos aleatorios. Útil para landing/mock.
 */

const occupancyStatus = (used, capacity) => {
  const pct = capacity > 0 ? (used / capacity) * 100 : 0;
  if (pct >= 85) return "red";
  if (pct >= 60) return "yellow";
  return "green";
};

const flightLoadStatusIndex = (used, capacity) => {
  const pct = capacity > 0 ? (used / capacity) * 100 : 0;
  if (pct >= 85) return 2;
  if (pct >= 60) return 1;
  return 0;
};

const parseUtcDateTime = (value) => {
  if (!value) return Number.NaN;
  const raw = String(value).trim();
  const normalized = /z$/i.test(raw) ? raw : `${raw}Z`;
  return Date.parse(normalized);
};

const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

/* ---------- Atlas de iconos para deck.gl IconLayer ---------- */
const PLANE_SVG_PATH =
  "M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.2-1.1.5l-1.3 1.5c-.3.4-.1 1 .4 1.2L9 12l-4 4-2.2-.6c-.4-.1-.8.1-1.1.4l-.8.8c-.3.4-.1 1 .4 1.2l4 1.5 1.5 4c.2.5.8.7 1.2.4l.8-.8c.3-.3.5-.7.4-1.1L8 19l4-4 2.6 6.2c.2.5.8.7 1.2.4l1.5-1.3c.3-.2.6-.6.5-1.1z";

const ICON_SIZE = 64;
const ICON_RENDER_SIZE = 24;

function buildIconAtlas() {
  const colors = [tokens.success, tokens.warning, tokens.danger];
  const atlas = document.createElement("canvas");
  atlas.width = ICON_SIZE * colors.length;
  atlas.height = ICON_SIZE;
  const ctx = atlas.getContext("2d");
  const path = new Path2D(PLANE_SVG_PATH);
  colors.forEach((color, i) => {
    ctx.save();
    ctx.translate(i * ICON_SIZE, 0);
    ctx.scale(ICON_SIZE / 24, ICON_SIZE / 24);
    ctx.fillStyle = color;
    ctx.strokeStyle = color;
    ctx.lineWidth = 1;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.fill(path);
    ctx.stroke(path);
    ctx.restore();
  });
  return atlas.toDataURL("image/png");
}

const ICON_ATLAS_URL = typeof document !== "undefined" ? buildIconAtlas() : null;

const ICON_MAPPING = {
  success: { x: 0,             width: ICON_SIZE, height: ICON_SIZE, anchorX: ICON_SIZE / 2, anchorY: ICON_SIZE / 2, mask: false },
  warning: { x: ICON_SIZE,     width: ICON_SIZE, height: ICON_SIZE, anchorX: ICON_SIZE / 2, anchorY: ICON_SIZE / 2, mask: false },
  danger:  { x: ICON_SIZE * 2, width: ICON_SIZE, height: ICON_SIZE, anchorX: ICON_SIZE / 2, anchorY: ICON_SIZE / 2, mask: false },
};

const STATUS_NAMES = ["success", "warning", "danger"];

const ROUTE_COLOR_RGBA = (() => {
  const hex = tokens.success.replace("#", "");
  return [
    parseInt(hex.slice(0, 2), 16),
    parseInt(hex.slice(2, 4), 16),
    parseInt(hex.slice(4, 6), 16),
    80,
  ];
})();

/* ---------- Iconos de aeropuerto (DOM, cacheados) ---------- */
const airportIconCache = new Map();
const getAirportIcon = (airport) => {
  const status = occupancyStatus(airport.used, airport.capacity);
  const key = `${airport.iata}|${status}`;
  const cached = airportIconCache.get(key);
  if (cached) return cached;
  const color = semaphoreColor(status);
  const icon = L.divIcon({
    html:
      `<div style="display:flex;flex-direction:column;justify-content:center;transform:translate(-50%,-50%);">` +
        `<div style="width:12px;height:12px;background-color:${color};border-radius:50%;box-shadow:0 0 10px ${color},0 0 20px ${color};margin:0 auto;"></div>` +
        `<div style="color:white;font-size:10px;font-weight:bold;font-family:sans-serif;background:rgba(0,0,0,0.5);padding:2px 4px;border-radius:4px;margin-top:4px;white-space:nowrap;">${airport.iata}</div>` +
      `</div>`,
    className: "",
    iconSize: [0, 0],
    iconAnchor: [0, 0],
  });
  airportIconCache.set(key, icon);
  return icon;
};

/* ---------- Filtros de vuelos ---------- */
const MAX_FLIGHTS_ON_MAP = 1000;

const filterValidFlights = (flights, airportsByIata) => {
  const out = [];
  for (const f of flights) {
    if (!airportsByIata.has(f.origin) || !airportsByIata.has(f.dest)) continue;
    out.push(f);
    if (out.length >= MAX_FLIGHTS_ON_MAP) break;
  }
  return out;
};

/* ---------- Overlay deck.gl ---------- */
function DeckGLOverlay({ planes, routes }) {
  const map = useMap();
  const layerRef = useRef(null);

  useEffect(() => {
    const deckLayer = new LeafletLayer({ layers: [] });
    deckLayer.addTo(map);
    layerRef.current = deckLayer;
    return () => {
      map.removeLayer(deckLayer);
      layerRef.current = null;
    };
  }, [map]);

  useEffect(() => {
    if (!layerRef.current) return;

    const pathLayer = new PathLayer({
      id: "routes",
      data: routes,
      getPath: (d) => d.path,
      getColor: ROUTE_COLOR_RGBA,
      getWidth: 1.5,
      widthUnits: "pixels",
      pickable: false,
    });

    const iconLayer = new IconLayer({
      id: "planes",
      data: planes,
      iconAtlas: ICON_ATLAS_URL,
      iconMapping: ICON_MAPPING,
      getIcon: (d) => STATUS_NAMES[d.statusIdx],
      getPosition: (d) => [d.lng, d.lat],
      getSize: ICON_RENDER_SIZE,
      sizeUnits: "pixels",
      getAngle: (d) => -d.angle,
      updateTriggers: {
        getPosition: planes,
        getAngle: planes,
        getIcon: planes,
      },
      pickable: false,
    });

    layerRef.current.setProps({ layers: [pathLayer, iconLayer] });
  }, [planes, routes]);

  return null;
}

function AirportMap({
  showFlights = true,
  showRouteLines = true,
  airports: airportsProp,
  flights: flightsProp,
  autoload = true,
  simulatedNowMs = null,
  animateFlights = false,
}) {
  const center = [20, -40];
  const zoom = 3;

  /* Fetch interno SOLO si el padre no nos pasa datos y autoload está activo */
  const { data: fetchedAirports = [] } = useFetch(
    () => (autoload && !airportsProp ? listAirports() : Promise.resolve([])),
    [autoload, !!airportsProp]
  );
  const { data: fetchedFlights = [] } = useFetch(
    () => (autoload && !flightsProp ? listFlights() : Promise.resolve([])),
    [autoload, !!flightsProp]
  );

  const airports = airportsProp ?? fetchedAirports ?? [];
  const flights = flightsProp ?? fetchedFlights ?? [];

  const airportsByIata = useMemo(() => {
    const map = new Map();
    (airports ?? []).forEach((a) => {
      if (a.iata && Number.isFinite(a.lat) && Number.isFinite(a.lng)) map.set(a.iata, a);
    });
    return map;
  }, [airports]);

  /* Geometría estática por vuelo: origen, destino, ángulo, deltas, fechas */
  const flightGeometry = useMemo(() => {
    const valid = filterValidFlights(flights ?? [], airportsByIata);
    return valid.map((route) => {
      const origin = airportsByIata.get(route.origin);
      const destination = airportsByIata.get(route.dest);
      const dLat = destination.lat - origin.lat;
      const dLng = destination.lng - origin.lng;
      const bearing = (Math.atan2(dLng, dLat) * 180) / Math.PI;
      const departureUtcMs = parseUtcDateTime(route.depTime);
      const arrivalUtcMs = parseUtcDateTime(route.arrTime);
      const hasValidSchedule =
        Number.isFinite(departureUtcMs) &&
        Number.isFinite(arrivalUtcMs) &&
        arrivalUtcMs > departureUtcMs;
      return {
        route,
        origin,
        destination,
        dLat,
        dLng,
        planeAngle: bearing - 45,
        statusIdx: flightLoadStatusIndex(route.used, route.capacity),
        departureUtcMs,
        arrivalUtcMs,
        hasValidSchedule,
      };
    });
  }, [flights, airportsByIata]);

  // Diagnóstico útil cuando algo no coincide
  useEffect(() => {
    const flightCount = Array.isArray(flights) ? flights.length : 0;
    // eslint-disable-next-line no-console
    console.log("[AirportMap] flights=" + flightCount + " airports=" + airportsByIata.size + " geometry=" + flightGeometry.length);
  }, [flights, airportsByIata, flightGeometry]);

  /* Decide si usamos el modo "horarios reales" (simulador con back)
   * o el modo "decorativo" (animación con progresos aleatorios). */
  const useRealSchedule = simulatedNowMs !== null && Number.isFinite(simulatedNowMs);

  /* === MODO DECORATIVO === progreso aleatorio + dt */
  const progressesRef = useRef([]);
  const [decorativePlanes, setDecorativePlanes] = useState([]);

  useEffect(() => {
    if (useRealSchedule) return;
    progressesRef.current = flightGeometry.map(() => Math.random());
  }, [flightGeometry.length, useRealSchedule]);

  useEffect(() => {
    if (useRealSchedule) return undefined;
    if (flightGeometry.length === 0) {
      setDecorativePlanes([]);
      return undefined;
    }

    let raf;
    let lastTime = performance.now();
    let acc = 0;
    const FRAME_BUDGET_MS = 33;

    const tick = (time) => {
      const dt = time - lastTime;
      lastTime = time;
      acc += dt;
      const arr = progressesRef.current;
      for (let i = 0; i < arr.length; i++) {
        arr[i] = (arr[i] + dt * 0.00005) % 1;
      }
      if (acc >= FRAME_BUDGET_MS) {
        acc = 0;
        const next = new Array(flightGeometry.length);
        for (let i = 0; i < flightGeometry.length; i++) {
          const g = flightGeometry[i];
          const p = arr[i];
          next[i] = {
            lat: g.origin.lat + g.dLat * p,
            lng: g.origin.lng + g.dLng * p,
            angle: g.planeAngle,
            statusIdx: g.statusIdx,
          };
        }
        setDecorativePlanes(next);
      }
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [flightGeometry, useRealSchedule]);

  /* === MODO HORARIOS REALES === posición según simulatedNowMs */
  const realPlanes = useMemo(() => {
    if (!useRealSchedule) return [];
    const out = [];
    for (const g of flightGeometry) {
      if (!g.hasValidSchedule) continue;
      const progress = clamp(
        (simulatedNowMs - g.departureUtcMs) / (g.arrivalUtcMs - g.departureUtcMs),
        0,
        1
      );
      if (progress <= 0 || progress >= 1) continue;
      out.push({
        lat: g.origin.lat + g.dLat * progress,
        lng: g.origin.lng + g.dLng * progress,
        angle: g.planeAngle,
        statusIdx: g.statusIdx,
      });
    }
    return out;
  }, [flightGeometry, simulatedNowMs, useRealSchedule]);

  const planes = useRealSchedule ? realPlanes : decorativePlanes;

  /* Rutas (PathLayer): solo las rutas de vuelos visibles */
  const routesForDeck = useMemo(() => {
    if (!showRouteLines) return [];
    if (useRealSchedule) {
      // Solo rutas de vuelos ya despegados (no terminados)
      const seen = new Set();
      const out = [];
      for (const g of flightGeometry) {
        if (!g.hasValidSchedule) continue;
        if (simulatedNowMs < g.departureUtcMs) continue;
        const key = `${g.route.origin}-${g.route.dest}`;
        if (seen.has(key)) continue;
        seen.add(key);
        out.push({ path: [[g.origin.lng, g.origin.lat], [g.destination.lng, g.destination.lat]] });
      }
      return out;
    }
    return flightGeometry.map((g) => ({
      path: [[g.origin.lng, g.origin.lat], [g.destination.lng, g.destination.lat]],
    }));
  }, [flightGeometry, showRouteLines, simulatedNowMs, useRealSchedule]);

  const airportList = useMemo(() => Array.from(airportsByIata.values()), [airportsByIata]);

  // Para que el efecto de animateFlights tenga sentido cuando el padre
  // no provee simulatedNowMs pero sí quiere ver aviones (caso decorativo)
  const showPlanesOnMap = showFlights && (useRealSchedule || animateFlights || (!simulatedNowMs && autoload));

  return (
    <div className="w-full h-full bg-canvas">
      <MapContainer
        center={center}
        zoom={zoom}
        style={{ height: "100%", width: "100%", background: "transparent" }}
        zoomControl={false}
        attributionControl={false}
      >
        <TileLayer url="https://{s}.basemaps.cartocdn.com/dark_nolabels/{z}/{x}/{y}{r}.png" />

        {showPlanesOnMap && (
          <DeckGLOverlay planes={planes} routes={routesForDeck} />
        )}

        {airportList.map((airport) => (
          <Marker
            key={airport.iata}
            position={[airport.lat, airport.lng]}
            icon={getAirportIcon(airport)}
          />
        ))}
      </MapContainer>
    </div>
  );
}

export default memo(AirportMap);
