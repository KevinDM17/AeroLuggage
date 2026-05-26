import { useEffect, useMemo, useState } from "react";
import { MapContainer, TileLayer, Marker, Polyline } from "react-leaflet";
import L from "leaflet";
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

const parseUtcDateTime = (value) => {
  if (!value) return Number.NaN;
  const raw = String(value).trim();
  const normalized = /z$/i.test(raw) ? raw : `${raw}Z`;
  return Date.parse(normalized);
};

const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

const createAirportIcon = (airport) => {
  const color = semaphoreColor(occupancyStatus(airport.used, airport.capacity));
  return L.divIcon({
    html: `
      <div style="display: flex; flex-direction: column; justify-content: center; transform: translate(-50%, -50%);">
        <div style="width: 12px; height: 12px; background-color: ${color}; border-radius: 50%; box-shadow: 0 0 10px ${color}, 0 0 20px ${color}; margin: 0 auto;"></div>
        <div style="color: white; font-size: 10px; font-weight: bold; font-family: sans-serif; background: rgba(0,0,0,0.5); padding: 2px 4px; border-radius: 4px; margin-top: 4px; white-space: nowrap;">
          ${airport.iata}
        </div>
      </div>
    `,
    className: "",
    iconSize: [0, 0],
    iconAnchor: [0, 0],
  });
};

const createPlaneIcon = (angle, color) =>
  L.divIcon({
    html: `
      <div style="width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; transform: rotate(${angle}deg);">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="${color}" stroke="${color}" stroke-width="1" stroke-linecap="round" stroke-linejoin="round">
          <path d="M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.2-1.1.5l-1.3 1.5c-.3.4-.1 1 .4 1.2L9 12l-4 4-2.2-.6c-.4-.1-.8.1-1.1.4l-.8.8c-.3.4-.1 1 .4 1.2l4 1.5 1.5 4c.2.5.8.7 1.2.4l.8-.8c.3-.3.5-.7.4-1.1L8 19l4-4 2.6 6.2c.2.5.8.7 1.2.4l1.5-1.3c.3-.2.6-.6.5-1.1z"/>
        </svg>
      </div>
    `,
    className: "",
    iconSize: [24, 24],
    iconAnchor: [12, 12],
  });

const getFlightsOnMap = (flights, airportsByIata) => {
  return [...(flights ?? [])]
    .filter((flight) => airportsByIata.has(flight.origin) && airportsByIata.has(flight.dest))
    .sort((left, right) => {
      const leftTime = parseUtcDateTime(left.depTime);
      const rightTime = parseUtcDateTime(right.depTime);
      const leftValid = Number.isFinite(leftTime);
      const rightValid = Number.isFinite(rightTime);

      if (leftValid && rightValid) return leftTime - rightTime;
      if (leftValid) return -1;
      if (rightValid) return 1;
      return String(left.id ?? "").localeCompare(String(right.id ?? ""));
    });
};

export default function AirportMap({
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

  const routes = useMemo(() => getFlightsOnMap(flights ?? [], airportsByIata), [flights, airportsByIata]);

  const [frameNowMs, setFrameNowMs] = useState(() => Date.now());

  useEffect(() => {
    if (!showFlights || simulatedNowMs === null || !animateFlights) return undefined;

    let raf = 0;
    const animate = () => {
      setFrameNowMs(Date.now());
      raf = requestAnimationFrame(animate);
    };

    raf = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(raf);
  }, [showFlights, simulatedNowMs, animateFlights]);

  const animatedRoutes = useMemo(() => {
    const activeSimulatedNowMs = simulatedNowMs ?? frameNowMs;

    return routes.map((route) => {
      const origin = airportsByIata.get(route.origin);
      const destination = airportsByIata.get(route.dest);
      const departureUtcMs = parseUtcDateTime(route.depTime);
      const arrivalUtcMs = parseUtcDateTime(route.arrTime);
      const hasValidSchedule =
        Number.isFinite(departureUtcMs) &&
        Number.isFinite(arrivalUtcMs) &&
        arrivalUtcMs > departureUtcMs;

      const progress =
        hasValidSchedule && Number.isFinite(activeSimulatedNowMs)
          ? clamp((activeSimulatedNowMs - departureUtcMs) / (arrivalUtcMs - departureUtcMs), 0, 1)
          : null;

      return {
        route,
        origin,
        destination,
        hasDeparted:
          hasValidSchedule && Number.isFinite(activeSimulatedNowMs) && activeSimulatedNowMs >= departureUtcMs,
        progress,
        showPlane: hasValidSchedule && progress !== null && progress > 0 && progress < 1,
      };
    });
  }, [routes, airportsByIata, simulatedNowMs, frameNowMs]);

  const visibleRouteSegments = useMemo(() => {
    const seen = new Set();
    const segments = [];

    animatedRoutes.forEach(({ route, origin, destination, progress, showPlane }) => {
      if (!showPlane || progress === null || progress >= 1 || !origin || !destination) return;

      const routeKey = `${route.origin}-${route.dest}`;
      if (seen.has(routeKey)) return;
      seen.add(routeKey);

      segments.push({
        key: routeKey,
        origin,
        destination,
      });
    });

    return segments;
  }, [animatedRoutes]);

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

        {showFlights && showRouteLines &&
          visibleRouteSegments.map(({ key, origin, destination }) => (
            <Polyline
              key={key}
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

        {showFlights &&
          animatedRoutes.map(({ route, origin, destination, progress, showPlane }, i) => {
            if (!origin || !destination || !showPlane) return null;

            const bearing =
              (Math.atan2(destination.lng - origin.lng, destination.lat - origin.lat) * 180) /
              Math.PI;
            const planeAngle = bearing - 45;
            const planeColor = flightLoadColor(route.used, route.capacity);
            const lat = origin.lat + (destination.lat - origin.lat) * progress;
            const lng = origin.lng + (destination.lng - origin.lng) * progress;

            return (
              <Marker
                key={`${route.id ?? i}-${route.origin}-${route.dest}`}
                position={[lat, lng]}
                icon={createPlaneIcon(planeAngle, planeColor)}
              />
            );
          })}

        {Array.from(airportsByIata.values()).map((airport) => (
          <Marker
            key={airport.iata}
            position={[airport.lat, airport.lng]}
            icon={createAirportIcon(airport)}
          />
        ))}
      </MapContainer>
    </div>
  );
}
