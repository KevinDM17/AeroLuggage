import { useEffect, useMemo, useRef } from "react";
import { MapContainer, TileLayer, Marker, Polyline, useMap } from "react-leaflet";
import L from "leaflet";
import { tokens, semaphoreColor } from "../../utils/tokens";
import { useFetch } from "../../hooks/useFetch";
import { listAirports } from "../../api/airports";
import { listFlights } from "../../api/flights";
import { createPlanesCanvasLayer } from "./PlanesCanvasLayer";

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

/* Cache global de iconos de aeropuerto. Como casi nunca cambian de status
 * y son pocos, el cache hit-rate es ~100% y evita que Leaflet recree el DOM. */
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

/**
 * Selecciona hasta N vuelos para animar en el mapa.
 * Prioriza diversidad de rutas (origen-destino únicos).
 */
const pickFlightsToAnimate = (flights, airportsByIata, limit) => {
  const seen = new Set();
  const out = [];
  for (const f of flights) {
    if (normalizeStatus(f.status) === "CANCELADO") continue;
    if (!airportsByIata.has(f.origin) || !airportsByIata.has(f.dest)) continue;
    const key = `${f.origin}-${f.dest}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(f);
    if (out.length >= limit) break;
  }
  return out;
};

/* Sub-componente que monta y anima el canvas layer sin pasar por React state. */
function PlanesCanvasOverlay({ routesGeometry }) {
  const map = useMap();
  const layerRef = useRef(null);

  useEffect(() => {
    const layer = createPlanesCanvasLayer();
    layer.addTo(map);
    layerRef.current = layer;
    return () => {
      layerRef.current = null;
      try {
        if (map?.hasLayer?.(layer)) {
          map.removeLayer(layer);
        } else {
          layer.remove?.();
        }
      } catch (error) {
        console.warn("[AirportMap] No se pudo desmontar la capa de aviones:", error);
      }
    };
  }, [map]);

  useEffect(() => {
    const layer = layerRef.current;
    if (!layer) return undefined;

    layer.startAnimation(routesGeometry);

    return () => {
      layer.stopAnimation();
    };
  }, [routesGeometry]);

  return null;
}

function AirportMap({
  showFlights = true,
  showRouteLines = true,
  airports: airportsProp,
  flights: flightsProp,
  autoload = true,
}) {
  const center = [20, -40];
  const zoom = 3;

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

  /* Geometría estática de cada ruta animada: origen, destino, ángulo, color
   * y deltas pre-calculados. Solo cambia cuando cambian vuelos/aeropuertos,
   * NO en cada frame. Esto saca toda la trigonometría del hot path. */
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
        color: flightLoadColor(route.used, route.capacity),
      };
    });
  }, [flights, airportsByIata]);

  const airportList = useMemo(() => Array.from(airportsByIata.values()), [airportsByIata]);

  return (
    <div className="w-full h-full bg-canvas">
      <MapContainer
        center={center}
        zoom={zoom}
        style={{ height: "100%", width: "100%", background: "transparent" }}
        zoomControl={false}
        attributionControl={false}
        preferCanvas={true}
      >
        <TileLayer url="https://{s}.basemaps.cartocdn.com/dark_nolabels/{z}/{x}/{y}{r}.png" />

        {/* Polylines: solo cambian si cambia la lista de rutas, NO cada frame */}
        {showFlights && showRouteLines &&
          routesGeometry.map(({ route, origin, destination }) => (
            <Polyline
              key={`${route.id ?? `${route.origin}-${route.dest}`}`}
              positions={[
                [origin.lat, origin.lng],
                [destination.lat, destination.lng],
              ]}
              color={tokens.success}
              weight={1.5}
              opacity={0.3}
              dashArray="4, 6"
            />
          ))}

        {/* Aviones en UN solo canvas (en vez de N markers DOM) */}
        {showFlights && <PlanesCanvasOverlay routesGeometry={routesGeometry} />}

        {/* Aeropuertos siguen siendo markers DOM (son pocos y no se mueven) */}
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

export default AirportMap;
