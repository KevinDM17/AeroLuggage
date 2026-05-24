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
 * Versión con deck.gl (WebGL): IconLayer + PathLayer corren en GPU.
 * Soporta cientos/miles de aviones simultáneos.
 */

const occupancyStatus = (used, capacity) => {
  const pct = capacity > 0 ? (used / capacity) * 100 : 0;
  if (pct >= 85) return "red";
  if (pct >= 60) return "yellow";
  return "green";
};

/* Índice numérico para indexar el atlas (success=0, warning=1, danger=2). */
const flightLoadStatusIndex = (used, capacity) => {
  const pct = capacity > 0 ? (used / capacity) * 100 : 0;
  if (pct >= 85) return 2;
  if (pct >= 60) return 1;
  return 0;
};

/* ---------- Atlas de iconos para deck.gl ----------
 * Un solo canvas con los 3 iconos de avión (success, warning, danger)
 * rasterizados lado a lado. Esto es la forma RECOMENDADA por deck.gl
 * (más confiable que pasar dataURL diferentes por avión).
 */
const PLANE_SVG_PATH =
  "M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.2-1.1.5l-1.3 1.5c-.3.4-.1 1 .4 1.2L9 12l-4 4-2.2-.6c-.4-.1-.8.1-1.1.4l-.8.8c-.3.4-.1 1 .4 1.2l4 1.5 1.5 4c.2.5.8.7 1.2.4l.8-.8c.3-.3.5-.7.4-1.1L8 19l4-4 2.6 6.2c.2.5.8.7 1.2.4l1.5-1.3c.3-.2.6-.6.5-1.1z";

const ICON_SIZE = 64;          // pixeles por icono en el atlas
const ICON_RENDER_SIZE = 24;   // pixeles en pantalla

/** Construye un canvas con los 3 iconos lado a lado y devuelve un DataURL PNG. */
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
    ctx.scale(ICON_SIZE / 24, ICON_SIZE / 24); // SVG viewBox 0 0 24 24
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
  success: { x: 0,                width: ICON_SIZE, height: ICON_SIZE, anchorX: ICON_SIZE / 2, anchorY: ICON_SIZE / 2, mask: false },
  warning: { x: ICON_SIZE,        width: ICON_SIZE, height: ICON_SIZE, anchorX: ICON_SIZE / 2, anchorY: ICON_SIZE / 2, mask: false },
  danger:  { x: ICON_SIZE * 2,    width: ICON_SIZE, height: ICON_SIZE, anchorX: ICON_SIZE / 2, anchorY: ICON_SIZE / 2, mask: false },
};

const STATUS_NAMES = ["success", "warning", "danger"];

const ROUTE_COLOR_RGBA = (() => {
  const hex = tokens.success.replace("#", "");
  return [
    parseInt(hex.slice(0, 2), 16),
    parseInt(hex.slice(2, 4), 16),
    parseInt(hex.slice(4, 6), 16),
    80, // alpha ~0.3
  ];
})();

/* ---------- Iconos de aeropuertos (siguen DOM) ---------- */
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

/* ---------- Filtro de vuelos ----------
 * Ya NO deduplica por ruta (eso ocultaba aviones cuando el back devuelve
 * varios vuelos con el mismo origen-destino). Solo descarta vuelos con
 * origen/destino inválido. Tope alto por seguridad pero amplio.
 */
const MAX_FLIGHTS_ON_MAP = 1000;

const filterFlightsForMap = (flights, airportsByIata) => {
  const out = [];
  for (const f of flights) {
    if (!airportsByIata.has(f.origin) || !airportsByIata.has(f.dest)) continue;
    out.push(f);
    if (out.length >= MAX_FLIGHTS_ON_MAP) break;
  }
  return out;
};

/* ---------- DeckGL Overlay ---------- */
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
      // deck.gl rota antihorario; nuestro angle se calculó para CSS (horario)
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

  const routesGeometry = useMemo(() => {
    const picked = filterFlightsForMap(flights ?? [], airportsByIata);
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
        statusIdx: flightLoadStatusIndex(route.used, route.capacity),
      };
    });
  }, [flights, airportsByIata]);

  // Diagnóstico que se dispara SIEMPRE (incluso con flights vacío) para
  // poder identificar si el problema es de filtros o de carga del back.
  useEffect(() => {
    const flightCount = Array.isArray(flights) ? flights.length : 0;
    const airportCount = airportsByIata.size;
    const drawableCount = routesGeometry.length;
    const sampleFlight = flightCount > 0 ? flights[0] : null;
    const sampleAirport = airports && airports.length > 0 ? airports[0] : null;
    // eslint-disable-next-line no-console
    console.log("[AirportMap] diagnóstico", {
      flightsDelBack: flightCount,
      aeropuertosValidos: airportCount,
      avionesDibujables: drawableCount,
      sampleFlight,
      sampleAirport,
    });
    if (flightCount > 0 && drawableCount === 0) {
      // eslint-disable-next-line no-console
      console.warn(
        "[AirportMap] hay vuelos pero ninguno mapea a un aeropuerto conocido. " +
        "Posible mismatch entre flight.origin/dest y airport.iata. Sample flight:",
        sampleFlight,
        "Airports keys:",
        Array.from(airportsByIata.keys())
      );
    }
  }, [flights, airports, airportsByIata, routesGeometry]);

  /* PathLayer: paths estáticos derivados de routesGeometry */
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

  /* Animación decorativa: progresos aleatorios + dt */
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
    const FRAME_BUDGET_MS = 33;
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
            statusIdx: geo.statusIdx,
          };
        }
        setPlanes(next);
      }

      raf = requestAnimationFrame(tick);
    };

    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [routesGeometry]);

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
