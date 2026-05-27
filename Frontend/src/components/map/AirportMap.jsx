import { memo, useEffect, useMemo, useRef } from "react";
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

/* Polylines: dedup por par origen-destino para no superponer N lineas iguales. */
const dedupRoutesForLines = (flights, airportsByIata) => {
  const seen = new Set();
  const out = [];
  for (const f of flights) {
    if (!airportsByIata.has(f.origin) || !airportsByIata.has(f.dest)) continue;
    const key = `${f.origin}-${f.dest}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(f);
  }
  return out;
};

const parseUtcMs = (value) => {
  if (!value) return NaN;
  const withZ = /[zZ]|[+-]\d{2}:?\d{2}$/.test(value) ? value : `${value}Z`;
  return Date.parse(withZ);
};

/* Sub-componente que monta el canvas layer, le entrega refs estables
 * (routes + simClockRef) y arranca/para su animacion segun animateFlights.
 * El layer corre SU PROPIO requestAnimationFrame internamente — toda la
 * animacion vive fuera de React. */
function PlanesCanvasOverlay({ routesRef, simClockRef, animating }) {
  const map = useMap();
  const layerRef = useRef(null);

  // Montar y desmontar el layer junto al mapa.
  useEffect(() => {
    const layer = createPlanesCanvasLayer();
    layer.addTo(map);
    layerRef.current = layer;
    return () => {
      map.removeLayer(layer);
      layerRef.current = null;
    };
  }, [map]);

  // Reconectar refs si cambian (en la practica son estables).
  useEffect(() => {
    if (layerRef.current) {
      layerRef.current.setSources(routesRef, simClockRef);
    }
  }, [routesRef, simClockRef]);

  // Encender/apagar el rAF interno del layer.
  useEffect(() => {
    if (layerRef.current) {
      layerRef.current.setAnimating(animating);
    }
  }, [animating]);

  return null;
}

function AirportMap({
  showFlights = true,
  showRouteLines = true,
  airports: airportsProp,
  flights: flightsProp,
  autoload = true,
  simClockRef = null,
  animateFlights = false,
  initialZoom = 4,
  initialCenter = [20, -40],
}) {
  /* Zoom 4 (vs el viejo 3) ~duplica la cantidad de pixeles que recorre un
   * avion por segundo real → el movimiento se siente "fluido" en vez de
   * "sub-pixel". El usuario puede acercar/alejar normalmente con la rueda. */
  const center = initialCenter;
  const zoom = initialZoom;

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

  /* Geometría de cada vuelo: origen, destino, ángulo, color, timestamps de
   * salida/llegada y deltas pre-calculados. Solo cambia cuando cambia la lista
   * de vuelos o aeropuertos, NO en cada frame. La posición se interpola en el
   * raf abajo a partir del simClockRef. */
  const routesGeometry = useMemo(() => {
    const out = [];
    for (const route of flights ?? []) {
      const origin = airportsByIata.get(route.origin);
      const destination = airportsByIata.get(route.dest);
      if (!origin || !destination) continue;
      const depMs = parseUtcMs(route.depTime);
      const arrMs = parseUtcMs(route.arrTime);
      if (!Number.isFinite(depMs) || !Number.isFinite(arrMs) || arrMs <= depMs) continue;
      const dLat = destination.lat - origin.lat;
      const dLng = destination.lng - origin.lng;
      const bearing = (Math.atan2(dLng, dLat) * 180) / Math.PI;
      out.push({
        route,
        origin,
        destination,
        depMs,
        arrMs,
        dLat,
        dLng,
        planeAngle: bearing - 45,
        color: flightLoadColor(route.used, route.capacity),
      });
    }
    return out;
  }, [flights, airportsByIata]);

  /* Polylines: una sola por par origen-destino, sin importar cuantos vuelos
   * hagan esa ruta. Solo cambia cuando cambia la lista de vuelos. */
  const routeLines = useMemo(
    () => dedupRoutesForLines(flights ?? [], airportsByIata),
    [flights, airportsByIata],
  );

  /* Animacion fluida: el layer corre su propio rAF interno (ver
   * PlanesCanvasLayer). Aca solo le entregamos refs estables a las rutas y
   * al reloj simulado. Las posiciones se computan y se dibujan adentro del
   * layer, sin tocar React. */
  const routesRef = useRef(routesGeometry);
  useEffect(() => {
    routesRef.current = routesGeometry;
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
        preferCanvas={true}
      >
        <TileLayer url="https://{s}.basemaps.cartocdn.com/dark_nolabels/{z}/{x}/{y}{r}.png" />

        {/* Polylines: una sola por par origen-destino, no cambia cada frame. */}
        {showFlights && showRouteLines &&
          routeLines.map((route) => {
            const origin = airportsByIata.get(route.origin);
            const destination = airportsByIata.get(route.dest);
            return (
              <Polyline
                key={`${route.origin}-${route.dest}`}
                positions={[
                  [origin.lat, origin.lng],
                  [destination.lat, destination.lng],
                ]}
                color={tokens.success}
                weight={1.5}
                opacity={0.3}
                dashArray="4, 6"
              />
            );
          })}

        {/* Aviones en UN solo canvas (en vez de N markers DOM). El layer
            maneja su propio rAF: le entregamos refs y le decimos cuando
            animar. */}
        {showFlights && (
          <PlanesCanvasOverlay
            routesRef={routesRef}
            simClockRef={simClockRef}
            animating={animateFlights}
          />
        )}

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

export default memo(AirportMap);
