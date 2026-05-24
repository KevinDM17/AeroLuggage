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
 * Versión de prueba con deck.gl (WebGL) en lugar de canvas 2D.
 * - Aviones: IconLayer (GPU, miles de elementos sin problema)
 * - Rutas: PathLayer (GPU)
 * - Aeropuertos: siguen siendo markers DOM (son pocos)
 *
 * Misma API pública que la versión anterior: solo recibe `showFlights`.
 */

const occupancyStatus = (used, capacity) => {
  const pct = capacity > 0 ? (used / capacity) * 100 : 0;
  if (pct >= 85) return "red";
  if (pct >= 60) return "yellow";
  return "green";
};

const flightLoadStatus = (used, capacity) => {
  const pct = capacity > 0 ? (used / capacity) * 100 : 0;
  if (pct >= 85) return "danger";
  if (pct >= 60) return "warning";
  return "success";
};

/* ---------- Iconos pre-generados como data URL SVG ----------
 * deck.gl IconLayer puede usar URLs; las data-URLs evitan el
 * round-trip a red. Generamos 3 (uno por color) y los cacheamos. */
const PLANE_SVG_PATH =
  "M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.2-1.1.5l-1.3 1.5c-.3.4-.1 1 .4 1.2L9 12l-4 4-2.2-.6c-.4-.1-.8.1-1.1.4l-.8.8c-.3.4-.1 1 .4 1.2l4 1.5 1.5 4c.2.5.8.7 1.2.4l.8-.8c.3-.3.5-.7.4-1.1L8 19l4-4 2.6 6.2c.2.5.8.7 1.2.4l1.5-1.3c.3-.2.6-.6.5-1.1z";

const makePlaneIconUrl = (color) => {
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 24 24" ` +
    `fill="${color}" stroke="${color}" stroke-width="1" stroke-linecap="round" stroke-linejoin="round">` +
    `<path d="${PLANE_SVG_PATH}"/>` +
    `</svg>`;
  return `data:image/svg+xml;base64,${btoa(svg)}`;
};

const PLANE_ICONS = {
  success: { url: makePlaneIconUrl(tokens.success), width: 64, height: 64, anchorX: 32, anchorY: 32, mask: false },
  warning: { url: makePlaneIconUrl(tokens.warning), width: 64, height: 64, anchorX: 32, anchorY: 32, mask: false },
  danger:  { url: makePlaneIconUrl(tokens.danger),  width: 64, height: 64, anchorX: 32, anchorY: 32, mask: false },
};

/* Polyline color en formato deck.gl: [R, G, B, A] */
const ROUTE_COLOR_RGBA = (() => {
  const hex = tokens.success.replace("#", "");
  const r = parseInt(hex.slice(0, 2), 16);
  const g = parseInt(hex.slice(2, 4), 16);
  const b = parseInt(hex.slice(4, 6), 16);
  return [r, g, b, 80]; // alpha bajo (0.3 aprox)
})();

/* ---------- Cache de iconos de aeropuerto (siguen siendo DOM) ---------- */
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

const MAX_FLIGHTS_ON_MAP = 30;

const pickFlightsToAnimate = (flights, airportsByIata, limit) => {
  const seen = new Set();
  const out = [];
  for (const f of flights) {
    if (!airportsByIata.has(f.origin) || !airportsByIata.has(f.dest)) continue;
    const key = `${f.origin}-${f.dest}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(f);
    if (out.length >= limit) break;
  }
  return out;
};

/* ---------- Sub-componente que monta el LeafletLayer de deck.gl ---------- */
function DeckGLOverlay({ planes, routes }) {
  const map = useMap();
  const layerRef = useRef(null);
  const planesRef = useRef(planes);
  const routesRef = useRef(routes);

  // Mantenemos refs actualizadas para que el animation loop las lea.
  useEffect(() => { planesRef.current = planes; }, [planes]);
  useEffect(() => { routesRef.current = routes; }, [routes]);

  useEffect(() => {
    const deckLayer = new LeafletLayer({
      views: undefined,
      layers: [],
    });
    deckLayer.addTo(map);
    layerRef.current = deckLayer;

    return () => {
      map.removeLayer(deckLayer);
      layerRef.current = null;
    };
  }, [map]);

  // Re-publicar layers cuando cambien planes o rutas
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
      getIcon: (d) => PLANE_ICONS[d.status],
      getPosition: (d) => [d.lng, d.lat],
      getSize: 24,
      sizeUnits: "pixels",
      // deck.gl rota antihorario; el ángulo nuestro fue calculado para CSS (horario)
      getAngle: (d) => -d.angle,
      // Actualizaciones frecuentes: invalidamos solo lo que cambia
      updateTriggers: {
        getPosition: planes,
        getAngle: planes,
      },
      pickable: false,
    });

    layerRef.current.setProps({ layers: [pathLayer, iconLayer] });
  }, [planes, routes]);

  return null;
}

function AirportMap({ showFlights = true }) {
  const center = [20, -40];
  const zoom = 3;

  const { data: airports = [] } = useFetch(listAirports);
  const { data: flights = [] } = useFetch(listFlights);

  const airportsByIata = useMemo(() => {
    const map = new Map();
    (airports ?? []).forEach((a) => {
      if (a.iata && Number.isFinite(a.lat) && Number.isFinite(a.lng)) map.set(a.iata, a);
    });
    return map;
  }, [airports]);

  /* Geometría estática por ruta */
  const routesGeometry = useMemo(() => {
    const picked = pickFlightsToAnimate(flights ?? [], airportsByIata, MAX_FLIGHTS_ON_MAP);
    return picked.map((route) => {
      const origin = airportsByIata.get(route.origin);
      const destination = airportsByIata.get(route.dest);
      const dLat = destination.lat - origin.lat;
      const dLng = destination.lng - origin.lng;
      const bearing = (Math.atan2(dLng, dLat) * 180) / Math.PI;
      return {
        route,
        origin,
        destination,
        dLat,
        dLng,
        planeAngle: bearing - 45,
        status: flightLoadStatus(route.used, route.capacity),
      };
    });
  }, [flights, airportsByIata]);

  /* Datos para PathLayer (estáticos por ruta) */
  const routesForDeck = useMemo(
    () =>
      routesGeometry.map((g) => ({
        path: [
          [g.origin.lng, g.origin.lat],
          [g.destination.lng, g.destination.lat],
        ],
      })),
    [routesGeometry]
  );

  /* Animación: progresos aleatorios + dt. Publicamos al deck.gl layer
   * la lista de aviones con su posición actual cada frame. */
  const progressesRef = useRef([]);
  const [planes, setPlanes] = useState([]);

  useEffect(() => {
    progressesRef.current = routesGeometry.map(() => Math.random());
  }, [routesGeometry.length]);

  useEffect(() => {
    if (routesGeometry.length === 0) {
      setPlanes([]);
      return undefined;
    }

    let raf;
    let lastTime = performance.now();
    const FRAME_BUDGET_MS = 33; // ~30fps

    let acc = 0;
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
        const next = new Array(routesGeometry.length);
        for (let i = 0; i < routesGeometry.length; i++) {
          const geo = routesGeometry[i];
          const p = arr[i];
          next[i] = {
            lat: geo.origin.lat + geo.dLat * p,
            lng: geo.origin.lng + geo.dLng * p,
            angle: geo.planeAngle,
            status: geo.status,
          };
        }
        setPlanes(next);
      }

      raf = requestAnimationFrame(tick);
    };

    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [routesGeometry, setPlanes]);

  const airportList = useMemo(() => Array.from(airportsByIata.values()), [airportsByIata]);

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

        {showFlights && (
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
