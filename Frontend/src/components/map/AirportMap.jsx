import { useEffect, useMemo, useRef, useState } from "react";
import { Map as MapGL, useControl } from "react-map-gl/maplibre";
import { MapboxOverlay } from "@deck.gl/mapbox";
import { ScatterplotLayer, LineLayer, IconLayer, TextLayer } from "@deck.gl/layers";
import "maplibre-gl/dist/maplibre-gl.css";
import { tokens, semaphoreColor } from "../../utils/tokens";
import { useFetch } from "../../hooks/useFetch";
import { listAirports } from "../../api/airports";
import { listFlights } from "../../api/flights";

const occupancyStatus = (used, capacity) => {
  const pct = capacity > 0 ? (used / capacity) * 100 : 0;
  if (pct >= 85) return "red";
  if (pct >= 60) return "yellow";
  return "green";
};

const flightLoadColor = (used, capacity) => {
  const pct = capacity > 0 ? (used / capacity) * 100 : 0;
  if (pct >= 85) return tokens.danger;
  if (pct >= 60) return tokens.warning;
  return tokens.success;
};

const normalizeStatus = (status) =>
  String(status ?? "").trim().toUpperCase().replace(/\s+/g, "_");

/* Hex (#RRGGBB) → [r, g, b, a] que es lo que deck.gl espera. */
function hexToRgba(hex, alpha = 255) {
  const clean = hex.replace("#", "");
  const r = parseInt(clean.substring(0, 2), 16);
  const g = parseInt(clean.substring(2, 4), 16);
  const b = parseInt(clean.substring(4, 6), 16);
  return [r, g, b, alpha];
}

/* Genera un sprite blanco de avión en un canvas y devuelve el data URL.
 * deck.gl multiplica este sprite por getColor → así pintamos cada avión
 * con su color sin tener N sprites distintos en memoria. */
const PLANE_SVG_PATH =
  "M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.2-1.1.5l-1.3 1.5c-.3.4-.1 1 .4 1.2L9 12l-4 4-2.2-.6c-.4-.1-.8.1-1.1.4l-.8.8c-.3.4-.1 1 .4 1.2l4 1.5 1.5 4c.2.5.8.7 1.2.4l.8-.8c.3-.3.5-.7.4-1.1L8 19l4-4 2.6 6.2c.2.5.8.7 1.2.4l1.5-1.3c.3-.2.6-.6.5-1.1z";

const PLANE_ICON_SIZE_PX = 64;

let planeIconUrl = null;
function getPlaneIconUrl() {
  if (planeIconUrl) return planeIconUrl;
  const size = PLANE_ICON_SIZE_PX;
  const canvas = document.createElement("canvas");
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext("2d");
  if (!ctx) return null;
  ctx.fillStyle = "#ffffff";
  ctx.scale(size / 24, size / 24);
  ctx.fill(new Path2D(PLANE_SVG_PATH));
  planeIconUrl = canvas.toDataURL("image/png");
  return planeIconUrl;
}

const PLANE_ICON_MAPPING = {
  plane: { x: 0, y: 0, width: PLANE_ICON_SIZE_PX, height: PLANE_ICON_SIZE_PX, mask: true },
};

const MAX_FLIGHTS_ON_MAP = 1000;

const pickFlightsToAnimate = (flights, airportsByIata, limit) => {
  const out = [];
  for (const f of flights) {
    if (normalizeStatus(f.status) === "CANCELADO") continue;
    if (!airportsByIata.has(f.origin) || !airportsByIata.has(f.dest)) continue;
    out.push(f);
  }
  return out;
};

const MAP_STYLE = import.meta.env.VITE_MAP_STYLE_URL
  ?? "https://basemaps.cartocdn.com/gl/dark-matter-nolabels-gl-style/style.json";

function DeckGLOverlay(props) {
  const overlay = useControl(() => new MapboxOverlay(props));
  overlay.setProps(props);
  return null;
}

function AirportMap({
  showFlights = true,
  showRouteLines = true,
  airports: airportsProp,
  flights: flightsProp,
  autoload = true,
  simulatedNowMs,
  simulatedDayDurationMs,
}) {
  const initialViewState = {
    longitude: -40,
    latitude: 20,
    zoom: 2,
  };

  const { data: fetchedAirports = [] } = useFetch(
    () => (autoload ? listAirports() : Promise.resolve([])),
    [autoload]
  );
  const { data: fetchedFlights = [] } = useFetch(
    () => (autoload ? listFlights() : Promise.resolve([])),
    [autoload]
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

  const airportList = useMemo(() => Array.from(airportsByIata.values()), [airportsByIata]);

  /* Geometría estática de rutas — solo cambia con vuelos/aeropuertos, NO por frame. */
  const routesGeometry = useMemo(() => {
    const picked = pickFlightsToAnimate(flights ?? [], airportsByIata, MAX_FLIGHTS_ON_MAP);
    const nowMs = typeof simulatedNowMs === "number" && Number.isFinite(simulatedNowMs)
      ? simulatedNowMs : null;
    const enProgreso = picked.filter((route) => {
      if (nowMs == null) return true;
      const salidaMs = Date.parse(`${route.depTime}Z`);
      const llegadaMs = Date.parse(`${route.arrTime}Z`);
      if (!Number.isFinite(salidaMs) || !Number.isFinite(llegadaMs)) return false;
      return nowMs >= salidaMs && nowMs < llegadaMs;
    });
    return enProgreso.map((route, idx) => {
      const origin = airportsByIata.get(route.origin);
      const destination = airportsByIata.get(route.dest);
      const dLat = destination.lat - origin.lat;
      const dLng = destination.lng - origin.lng;
      const bearing = (Math.atan2(dLng, dLat) * 180) / Math.PI;
      const salidaMs = Date.parse(`${route.depTime}Z`);
      const llegadaMs = Date.parse(`${route.arrTime}Z`);
      return {
        id: route.id ?? `${route.origin}-${route.dest}-${idx}`,
        origin,
        destination,
        oLng: origin.lng,
        oLat: origin.lat,
        dLng,
        dLat,
        angle: bearing,
        color: hexToRgba(flightLoadColor(route.used, route.capacity)),
        depMs: Number.isFinite(salidaMs) ? salidaMs : null,
        arrMs: Number.isFinite(llegadaMs) ? llegadaMs : null,
      };
    });
  }, [flights, airportsByIata, simulatedNowMs]);

  /* Estado de las posiciones animadas — viene del worker. */
  const [planes, setPlanes] = useState([]);
  const workerRef = useRef(null);

  /* Levantar el worker una vez. */
  useEffect(() => {
    const worker = new Worker(
      new URL("./planesWorker.js", import.meta.url),
      { type: "module" }
    );
    worker.onmessage = (e) => {
      if (e.data.type === "positions") setPlanes(e.data.planes);
    };
    workerRef.current = worker;
    return () => {
      worker.postMessage({ type: "stop" });
      worker.terminate();
      workerRef.current = null;
    };
  }, []);

  /* Cada vez que cambia la geometría de rutas, le pasamos la nueva lista al worker. */
  useEffect(() => {
    const worker = workerRef.current;
    if (!worker) return;
    if (!showFlights) {
      worker.postMessage({ type: "stop" });
      setPlanes([]);
      return;
    }
    const simTimeMs = typeof simulatedNowMs === "number" && Number.isFinite(simulatedNowMs)
      ? simulatedNowMs : null;
    const dayDurMs = typeof simulatedDayDurationMs === "number" && simulatedDayDurationMs > 0
      ? simulatedDayDurationMs : 86400000;
    worker.postMessage({
      type: "init",
      simTime: simTimeMs,
      dayDurationMs: dayDurMs,
      routes: routesGeometry.map((g) => ({
        id: g.id,
        oLng: g.oLng,
        oLat: g.oLat,
        dLng: g.dLng,
        dLat: g.dLat,
        angle: g.angle,
        color: g.color,
        depMs: g.depMs,
        arrMs: g.arrMs,
      })),
    });
  }, [routesGeometry, showFlights, simulatedNowMs, simulatedDayDurationMs]);

  /* Capas de deck.gl. Se recalculan en cada render — son objetos baratos,
   * deck.gl hace el diff por id (`id` prop) y solo redibuja lo que cambió. */
  const layers = useMemo(() => {
    const ls = [];

    /* Polylines de rutas. */
    if (showFlights && showRouteLines && routesGeometry.length > 0) {
      ls.push(
        new LineLayer({
          id: "routes",
          data: routesGeometry,
          getSourcePosition: (d) => [d.origin.lng, d.origin.lat],
          getTargetPosition: (d) => [d.destination.lng, d.destination.lat],
          getColor: hexToRgba(tokens.success, 76), // ~0.3 alpha
          getWidth: 1.5,
          widthUnits: "pixels",
        })
      );
    }

    /* Halo glow de cada aeropuerto (un disco grande semi-transparente).
     * deck.gl no tiene box-shadow → simulamos con un scatterplot debajo. */
    if (airportList.length > 0) {
      ls.push(
        new ScatterplotLayer({
          id: "airport-glow",
          data: airportList,
          getPosition: (a) => [a.lng, a.lat],
          getFillColor: (a) => {
            const s = occupancyStatus(a.used, a.capacity);
            return hexToRgba(semaphoreColor(s), 90);
          },
          getRadius: 14,
          radiusUnits: "pixels",
          stroked: false,
        })
      );

      /* Punto central del aeropuerto. */
      ls.push(
        new ScatterplotLayer({
          id: "airport-dot",
          data: airportList,
          getPosition: (a) => [a.lng, a.lat],
          getFillColor: (a) => {
            const s = occupancyStatus(a.used, a.capacity);
            return hexToRgba(semaphoreColor(s));
          },
          getRadius: 6,
          radiusUnits: "pixels",
          stroked: false,
        })
      );

      /* Códigos IATA. */
      ls.push(
        new TextLayer({
          id: "airport-labels",
          data: airportList,
          getPosition: (a) => [a.lng, a.lat],
          getText: (a) => a.iata,
          getSize: 11,
          getColor: [255, 255, 255, 255],
          getPixelOffset: [0, 16],
          fontFamily: "sans-serif",
          fontWeight: "bold",
          background: true,
          backgroundPadding: [3, 1],
          getBackgroundColor: [0, 0, 0, 128],
        })
      );
    }

    /* Aviones — IconLayer pintado por la GPU. */
    if (showFlights && planes.length > 0) {
      const iconUrl = getPlaneIconUrl();
      if (iconUrl) {
        ls.push(
          new IconLayer({
            id: "planes",
            data: planes,
            iconAtlas: iconUrl,
            iconMapping: PLANE_ICON_MAPPING,
            getIcon: () => "plane",
            getPosition: (d) => [d.lng, d.lat],
            // El SVG del avión apunta al noreste (45° desde el norte).
            // Para que apunte al destino: rotar CW por (bearing - 45).
            // Pero deck.gl mide CCW → invertir signo → 45 - bearing.
            getAngle: (d) => 45 - d.angle,
            getColor: (d) => d.color,
            getSize: 22,
            sizeUnits: "pixels",
            updateTriggers: {
              getPosition: planes,
              getAngle: planes,
              getColor: planes,
            },
          })
        );
      }
    }

    return ls;
  }, [showFlights, showRouteLines, routesGeometry, airportList, planes]);

  return (
    <div className="w-full h-full bg-canvas">
      <MapGL
        initialViewState={initialViewState}
        mapStyle={MAP_STYLE}
        attributionControl={false}
        renderWorldCopies={false}
        style={{ width: "100%", height: "100%", background: tokens.canvas }}
      >
        <DeckGLOverlay layers={layers} interleaved />
      </MapGL>
    </div>
  );
}

export default AirportMap;
