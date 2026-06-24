import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Map as MapGL, useControl } from "react-map-gl/maplibre";
import { MapboxOverlay } from "@deck.gl/mapbox";
import { ScatterplotLayer, LineLayer, IconLayer, TextLayer, PathLayer } from "@deck.gl/layers";
import { PathStyleExtension } from "@deck.gl/extensions";
import "maplibre-gl/dist/maplibre-gl.css";
import { tokens } from "../../utils/tokens";
import { useFetch } from "../../hooks/useFetch";
import { listAirports } from "../../api/airports";
import { listFlights } from "../../api/flights";
import { useMapFocus } from "../../context/MapFocusContext";

/* Escalones discretos por % de ocupación.
 *   0%     → "white" (vacío)
 *   <60%   → "green"
 *   60-84% → "yellow"
 *   >=85%  → "red"
 */
const occupancyStatus = (used, capacity) => {
  const pct = capacity > 0 ? (used / capacity) * 100 : 0;
  if (pct <= 0) return "white";
  if (pct >= 85) return "red";
  if (pct >= 60) return "yellow";
  return "green";
};

const STATUS_HEX = {
  white: "#ffffff",
  green: tokens.success,
  yellow: tokens.warning,
  red: tokens.danger,
};

/* Alpha para los aviones según el escalón.
 * Vacío = blanco translúcido; cargado = opaco. */
const FLIGHT_ALPHA = {
  white: 70,
  green: 255,
  yellow: 255,
  red: 255,
};

const normalizeStatus = (status) =>
  String(status ?? "").trim().toUpperCase().replace(/\s+/g, "_");

/* Web Mercator: la latitud no es lineal en pantalla; sin esta correccion los
 * aviones se desvian de la linea pintada en rutas con mucho rango de latitud. */
const mercY = (lat) => Math.log(Math.tan(Math.PI / 4 + (lat * Math.PI) / 360));

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

/* Sprite blanco de un edificio de aeropuerto (terminal + torre de control).
 * Representa un aeropuerto sin confundirse con los aviones en vuelo. Es una
 * máscara que deck.gl colorea con getColor según la ocupación. Se dibuja con
 * primitivas de canvas en un espacio 24x24. */
const AIRPORT_BUILDING_SIZE_PX = 64;

let airportBuildingUrl = null;
function getAirportBuildingUrl() {
  if (airportBuildingUrl) return airportBuildingUrl;
  const size = AIRPORT_BUILDING_SIZE_PX;
  const canvas = document.createElement("canvas");
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext("2d");
  if (!ctx) return null;
  ctx.fillStyle = "#ffffff";
  ctx.scale(size / 24, size / 24);

  // Torre de control: antena + cabina (sala de control) + fuste.
  ctx.fillRect(5.1, 1.5, 0.9, 2.2); // antena
  ctx.beginPath();
  ctx.moveTo(2.8, 4);
  ctx.lineTo(8.2, 4);
  ctx.lineTo(7.4, 7);
  ctx.lineTo(3.6, 7);
  ctx.closePath();
  ctx.fill();
  ctx.fillRect(4.1, 7, 2.9, 13.5); // fuste

  // Terminal: edificio con techo inclinado que baja al alejarse de la torre.
  ctx.beginPath();
  ctx.moveTo(7, 20.5);
  ctx.lineTo(7, 11.5);
  ctx.lineTo(21, 14.5);
  ctx.lineTo(21, 20.5);
  ctx.closePath();
  ctx.fill();

  // Suelo / base.
  ctx.fillRect(2.5, 20.5, 19, 2);

  airportBuildingUrl = canvas.toDataURL("image/png");
  return airportBuildingUrl;
}

const AIRPORT_BUILDING_MAPPING = {
  airport: { x: 0, y: 0, width: AIRPORT_BUILDING_SIZE_PX, height: AIRPORT_BUILDING_SIZE_PX, mask: true },
};

const MAX_FLIGHTS_ON_MAP = 1000;

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

  /* Vinculacion panel <-> mapa. */
  const { mapHighlight, selected, mapFocus, mapDim, setSelected, setPanelFocus, flightManifestLoader } = useMapFocus();
  const mapRef = useRef(null);

  // Click en el mapa -> seleccionar y pedir enfoque en el panel (req 6/8).
  const selectFromMap = useCallback((entity) => {
    setSelected(entity);
    setPanelFocus({ ...entity, ts: Date.now() });
  }, [setSelected, setPanelFocus]);

  /* Tarjeta con datos del vuelo: se muestra SOLO al pasar el mouse por encima de
   * un avion NO vacio (used > 0). El click solo selecciona (enlace con el panel).
   * - manifest: pedidos/maletas a bordo, pedidos al back mientras se sobrevuela. */
  const [clickedAirportId, setClickedAirportId] = useState(null);
  const [hoveredPlaneId, setHoveredPlaneId] = useState(null);
  const [hoveredAirportId, setHoveredAirportId] = useState(null);
  const [manifest, setManifest] = useState({ status: "idle", pedidos: 0, maletas: 0 });

  // Vuelo sobrevolado, solo si NO esta vacio (lleva al menos una maleta).
  const hoveredFlight = useMemo(() => {
    if (!hoveredPlaneId) return null;
    const f = (flights ?? []).find((fl) => (fl.id ?? fl.idVueloInstancia) === hoveredPlaneId);
    if (!f || Number(f.used ?? 0) <= 0) return null;
    return f;
  }, [hoveredPlaneId, flights]);

  const hoveredFlightId = hoveredFlight ? (hoveredFlight.id ?? hoveredFlight.idVueloInstancia) : null;

  const clickedAirport = useMemo(() => {
    if (!clickedAirportId) return null;
    return airportsByIata.get(clickedAirportId) ?? null;
  }, [clickedAirportId, airportsByIata]);

  useEffect(() => {
    if (!hoveredFlightId || !flightManifestLoader) {
      setManifest({ status: "idle", pedidos: 0, maletas: 0 });
      return undefined;
    }
    let cancelled = false;
    setManifest({ status: "loading", pedidos: 0, maletas: 0 });
    flightManifestLoader(hoveredFlightId)
      .then((data) => {
        if (cancelled) return;
        setManifest({
          status: "ready",
          pedidos: (data?.pedidos ?? []).length,
          maletas: (data?.maletas ?? []).length,
        });
      })
      .catch(() => {
        if (cancelled) return;
        setManifest({ status: "error", pedidos: 0, maletas: 0 });
      });
    return () => { cancelled = true; };
  }, [hoveredFlightId, flightManifestLoader]);

  const handleDeckClick = useCallback((info) => {
    if (!info?.object) {
      setClickedAirportId(null);
      setHoveredPlaneId(null);
      setHoveredAirportId(null);
      setSelected(null);
    }
  }, [setSelected]);

  // Enfoque de camara pedido por el panel (req 5/7/9).
  useEffect(() => {
    if (!mapFocus) return;
    const ref = mapRef.current;
    const map = ref?.getMap ? ref.getMap() : ref;
    const { lng, lat, zoom } = mapFocus;
    if (map?.flyTo && Number.isFinite(lng) && Number.isFinite(lat)) {
      try {
        map.flyTo({ center: [lng, lat], zoom: zoom ?? 4, duration: 1200 });
      } catch (e) {
        // ignore
      }
    }
  }, [mapFocus]);

  const selectedAirport = useMemo(() => {
    if (selected?.kind !== "airport") return null;
    return airportsByIata.get(selected.id) ?? null;
  }, [selected, airportsByIata]);

  const selectedFlightLine = useMemo(() => {
    if (selected?.kind !== "flight") return null;
    const f = (flights ?? []).find((fl) => (fl.id ?? fl.idVueloInstancia) === selected.id);
    if (!f) return null;
    const o = airportsByIata.get(f.origin);
    const d = airportsByIata.get(f.dest);
    if (!o || !d) return null;
    return { oLng: o.lng, oLat: o.lat, dLng: d.lng, dLat: d.lat };
  }, [selected, flights, airportsByIata]);

  const highlightGeometry = useMemo(() => {
    const legs = mapHighlight?.legs ?? [];
    return legs
      .map((leg, i) => {
        const o = airportsByIata.get(leg.origen);
        const d = airportsByIata.get(leg.destino);
        if (!o || !d) return null;
        return { id: `${leg.origen}-${leg.destino}-${i}`, oLng: o.lng, oLat: o.lat, dLng: d.lng, dLat: d.lat };
      })
      .filter(Boolean);
  }, [mapHighlight, airportsByIata]);

  const highlightAirports = useMemo(() => {
    const codes = new Set();
    for (const leg of mapHighlight?.legs ?? []) {
      if (leg.origen) codes.add(leg.origen);
      if (leg.destino) codes.add(leg.destino);
    }
    return [...codes].map((c) => airportsByIata.get(c)).filter(Boolean);
  }, [mapHighlight, airportsByIata]);

  /* Geometría estática de rutas — solo cambia con vuelos/aeropuertos, NO por frame. */
  const routesGeometry = useMemo(() => {
    return (flights ?? []).map((route, idx) => {
      const origin = airportsByIata.get(route.origin);
      const destination = airportsByIata.get(route.dest);
      if (!origin || !destination) return null;
      const dLat = destination.lat - origin.lat;
      const dLng = destination.lng - origin.lng;
      // deck.gl LineLayer dibuja recto en espacio Mercator proyectado, no en
      // lat/lng. Para que el avion siga la linea exactamente, interpolamos la
      // lat en mercator-y (la lng es lineal en Mercator). dLngRad/dMercY tambien
      // dan el angulo "real" de la trayectoria en pantalla.
      const oMercY = mercY(origin.lat);
      const dMercY = mercY(destination.lat) - oMercY;
      const dLngRad = dLng * Math.PI / 180;
      const salidaMs = Date.parse(`${route.depTime}Z`);
      const llegadaMs = Date.parse(`${route.arrTime}Z`);
      const occStatus = occupancyStatus(route.used, route.capacity);
      return {
        id: route.id ?? `${route.origin}-${route.dest}-${idx}`,
        origin,
        destination,
        oLng: origin.lng,
        oLat: origin.lat,
        dLng,
        dLat,
        oMercY,
        dMercY,
        angle: (Math.atan2(dLngRad, dMercY) * 180) / Math.PI,
        color: hexToRgba(STATUS_HEX[occStatus], FLIGHT_ALPHA[occStatus]),
        isEmpty: occStatus === "white",
        depMs: Number.isFinite(salidaMs) ? salidaMs : null,
        arrMs: Number.isFinite(llegadaMs) ? llegadaMs : null,
      };
    }).filter(Boolean);
  }, [flights, airportsByIata]);

  /* Filtro de almacenes activo en el panel -> en el mapa solo mostramos los
   * vuelos (y sus lineas) relacionados con esos aeropuertos: los que salen de o
   * se dirigen a alguno de ellos. El resto se ocultan por completo.
   * null = sin filtro de aeropuertos -> se muestran todos. */
  const relatedFlightIds = useMemo(() => {
    const set = mapDim.airports;
    if (!set) return null;
    const ids = new Set();
    for (const r of routesGeometry) {
      if (set.has(r.origin?.iata) || set.has(r.destination?.iata)) ids.add(r.id);
    }
    return ids;
  }, [mapDim.airports, routesGeometry]);

  const visibleRoutesGeometry = useMemo(
    () => (relatedFlightIds ? routesGeometry.filter((r) => relatedFlightIds.has(r.id)) : routesGeometry),
    [routesGeometry, relatedFlightIds]
  );

  /* Estado de las posiciones animadas — viene del worker. */
  const [planes, setPlanes] = useState([]);
  const workerRef = useRef(null);

  const visiblePlanes = useMemo(
    () => (relatedFlightIds ? planes.filter((p) => relatedFlightIds.has(p.id)) : planes),
    [planes, relatedFlightIds]
  );

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
        oMercY: g.oMercY,
        dMercY: g.dMercY,
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

    /* Polylines de rutas. Las vacias se pintan blancas discontinuas para
     * distinguirlas a simple vista del trafico real. */
    if (showFlights && showRouteLines && visibleRoutesGeometry.length > 0) {
      const loadedRoutes = visibleRoutesGeometry.filter((r) => !r.isEmpty);
      const emptyRoutes = visibleRoutesGeometry.filter((r) => r.isEmpty);

      if (loadedRoutes.length > 0) {
        ls.push(
          new LineLayer({
            id: "routes",
            data: loadedRoutes,
            getSourcePosition: (d) => [d.origin.lng, d.origin.lat],
            getTargetPosition: (d) => [d.destination.lng, d.destination.lat],
            getColor: hexToRgba(tokens.success, 255), // opaco; solo las vacias son translucidas
            getWidth: 1.2,
            widthUnits: "pixels",
          })
        );
      }

      if (emptyRoutes.length > 0) {
        ls.push(
          new PathLayer({
            id: "routes-empty",
            data: emptyRoutes,
            getPath: (d) => [[d.origin.lng, d.origin.lat], [d.destination.lng, d.destination.lat]],
            getColor: [255, 255, 255, 70], // blanco translucido
            getWidth: 1.2,
            widthUnits: "pixels",
            getDashArray: [4, 3],
            dashJustified: true,
            extensions: [new PathStyleExtension({ dash: true })],
          })
        );
      }
    }

    /* Halo glow de cada aeropuerto (un disco grande semi-transparente).
     * deck.gl no tiene box-shadow → simulamos con un scatterplot debajo. */
    if (airportList.length > 0) {
      const airportDimmed = (a) => mapDim.airports && !mapDim.airports.has(a.iata);
      const selectedAirportId = selected?.kind === "airport" ? selected.id : null;

      ls.push(
        new ScatterplotLayer({
          id: "airport-hitbox",
          data: airportList,
          getPosition: (a) => [a.lng, a.lat],
          getRadius: 18,
          radiusUnits: "pixels",
          getFillColor: [0, 0, 0, 0],
          pickable: true,
          onClick: (info) => {
            if (info?.object?.iata) {
              selectFromMap({ kind: "airport", id: info.object.iata });
              setClickedAirportId(info.object.iata);
              return true;
            }
            return false;
          },
        })
      );

      ls.push(
        new ScatterplotLayer({
          id: "airport-glow",
          data: airportList,
          getPosition: (a) => [a.lng, a.lat],
          getFillColor: (a) => {
            if (a.iata === selectedAirportId || a.iata === hoveredAirportId) return hexToRgba(tokens.info, 110);
            const s = occupancyStatus(a.used, a.capacity);
            return hexToRgba(STATUS_HEX[s], airportDimmed(a) ? 10 : 90);
          },
          getRadius: 14,
          radiusUnits: "pixels",
          stroked: false,
          updateTriggers: { getFillColor: [mapDim.airports, selectedAirportId, hoveredAirportId] },
        })
      );

      /* Ícono de edificio de aeropuerto (terminal + torre). Máscara coloreada
       * por ocupación; se distingue de los aviones en vuelo. Azul al
       * seleccionar/hover. */
      const airportBuildingUrl = getAirportBuildingUrl();
      if (airportBuildingUrl) {
        ls.push(
          new IconLayer({
            id: "airport-icons",
            data: airportList,
            iconAtlas: airportBuildingUrl,
            iconMapping: AIRPORT_BUILDING_MAPPING,
            getIcon: () => "airport",
            getPosition: (a) => [a.lng, a.lat],
            getColor: (a) => {
              if (a.iata === selectedAirportId || a.iata === hoveredAirportId) return hexToRgba(tokens.info, 255);
              const s = occupancyStatus(a.used, a.capacity);
              return hexToRgba(STATUS_HEX[s], airportDimmed(a) ? 45 : 255);
            },
            getSize: (a) => (a.iata === hoveredAirportId || a.iata === selectedAirportId) ? 28 : 23,
            sizeUnits: "pixels",
            updateTriggers: {
              getColor: [mapDim.airports, selectedAirportId, hoveredAirportId],
              getSize: [hoveredAirportId, selectedAirportId],
            },
          })
        );
      }

      /* Nombre descriptivo del aeropuerto (ciudad). */
      ls.push(
        new TextLayer({
          id: "airport-labels",
          data: airportList,
          getPosition: (a) => [a.lng, a.lat],
          getText: (a) => {
            const label = a.city || a.name || a.iata || "";
            return label.length > 22 ? `${label.slice(0, 21)}…` : label;
          },
          getSize: 11,
          getColor: [255, 255, 255, 255],
          getPixelOffset: [0, 22],
          fontFamily: "sans-serif",
          fontWeight: "bold",
          background: true,
          backgroundPadding: [3, 1],
          getBackgroundColor: [0, 0, 0, 128],
        })
      );
    }

    /* Aviones — hitbox invisible (captura clicks en radio amplio) + IconLayer visual. */
    if (showFlights && visiblePlanes.length > 0) {
      const iconUrl = getPlaneIconUrl();
      const flightDimmed = (d) => mapDim.flights && !mapDim.flights.has(d.id);
      const selectedFlightId = selected?.kind === "flight" ? selected.id : null;
      const selectedColor = hexToRgba(tokens.info, 255);

      ls.push(
        new ScatterplotLayer({
          id: "planes-hitbox",
          data: visiblePlanes,
          getPosition: (d) => [d.lng, d.lat],
          getRadius: 20,
          radiusUnits: "pixels",
          getFillColor: [0, 0, 0, 0],
          pickable: true,
          onClick: (info) => {
            if (info?.object?.id) {
              selectFromMap({ kind: "flight", id: info.object.id });
              return true;
            }
            return false;
          },
          updateTriggers: { getPosition: visiblePlanes },
        })
      );

      if (iconUrl) {
        ls.push(
          new IconLayer({
            id: "planes",
            data: visiblePlanes,
            iconAtlas: iconUrl,
            iconMapping: PLANE_ICON_MAPPING,
            getIcon: () => "plane",
            getPosition: (d) => [d.lng, d.lat],
            getAngle: (d) => 45 - d.angle,
            getColor: (d) => {
              if (d.id === selectedFlightId) return selectedColor;
              if (d.id === hoveredPlaneId) return selectedColor;
              if (flightDimmed(d)) return [d.color[0], d.color[1], d.color[2], 45];
              return d.color;
            },
            getSize: (d) => (d.id === hoveredPlaneId || d.id === selectedFlightId) ? 22 : 18,
            sizeUnits: "pixels",
            updateTriggers: {
              getPosition: visiblePlanes,
              getAngle: visiblePlanes,
              getColor: [visiblePlanes, mapDim.flights, selectedFlightId, hoveredPlaneId],
              getSize: [hoveredPlaneId, selectedFlightId],
            },
          })
        );
      }

    }

    /* Ruta resaltada (a demanda): lineas gruesas + nodos enfatizados, encima de todo. */
    if (highlightGeometry.length > 0) {
      ls.push(
        new LineLayer({
          id: "highlight-routes",
          data: highlightGeometry,
          getSourcePosition: (d) => [d.oLng, d.oLat],
          getTargetPosition: (d) => [d.dLng, d.dLat],
          getColor: hexToRgba(tokens.info, 255),
          getWidth: 3.5,
          widthUnits: "pixels",
        })
      );
    }
    if (highlightAirports.length > 0) {
      ls.push(
        new ScatterplotLayer({
          id: "highlight-airports",
          data: highlightAirports,
          getPosition: (a) => [a.lng, a.lat],
          getFillColor: hexToRgba(tokens.info, 235),
          getRadius: 8,
          radiusUnits: "pixels",
          stroked: true,
          getLineColor: [255, 255, 255, 255],
          lineWidthUnits: "pixels",
          getLineWidth: 1.5,
        })
      );
    }

    /* Entidad seleccionada (panel<->mapa): anillo/linea enfatizados. */
    if (selectedFlightLine) {
      ls.push(
        new LineLayer({
          id: "selected-flight-line",
          data: [selectedFlightLine],
          getSourcePosition: (d) => [d.oLng, d.oLat],
          getTargetPosition: (d) => [d.dLng, d.dLat],
          getColor: hexToRgba(tokens.info, 255),
          getWidth: 1.2,
          widthUnits: "pixels",
        })
      );
    }
    if (selectedAirport) {
      ls.push(
        new ScatterplotLayer({
          id: "selected-airport-ring",
          data: [selectedAirport],
          getPosition: (a) => [a.lng, a.lat],
          getFillColor: [0, 0, 0, 0],
          stroked: true,
          getLineColor: hexToRgba(tokens.info, 255),
          lineWidthUnits: "pixels",
          getLineWidth: 3,
          getRadius: 12,
          radiusUnits: "pixels",
        })
      );
    }

    return ls;
  }, [
    showFlights, showRouteLines, routesGeometry, visibleRoutesGeometry, airportList, planes, visiblePlanes,
    highlightGeometry, highlightAirports, mapDim, selected, selectedAirport, selectedFlightLine, selectFromMap,
    hoveredPlaneId, hoveredAirportId,
  ]);

  return (
    <div className="w-full h-full bg-canvas relative">
      <MapGL
        ref={mapRef}
        initialViewState={initialViewState}
        mapStyle={MAP_STYLE}
        attributionControl={false}
        renderWorldCopies={false}
        style={{ width: "100%", height: "100%", background: tokens.canvas }}
      >
        <DeckGLOverlay
          layers={layers}
          interleaved
          onClick={handleDeckClick}
          getCursor={({ isDragging, object }) => (object ? "pointer" : "grab")}
          onHover={(info) => {
            if (info?.object && info.layer?.id === "planes-hitbox") {
              setHoveredPlaneId(info.object.id);
              setHoveredAirportId(null);
            } else if (info?.object && info.layer?.id === "airport-hitbox") {
              setHoveredAirportId(info.object.iata);
              setHoveredPlaneId(null);
            } else {
              setHoveredPlaneId(null);
              setHoveredAirportId(null);
            }
          }}
        />
      </MapGL>
      {hoveredFlight && (
        <FlightInfoCard
          flight={hoveredFlight}
          manifest={manifest}
        />
      )}
      {clickedAirport && (
        <AirportInfoCard
          airport={clickedAirport}
          onClose={() => { setClickedAirportId(null); setSelected(null); }}
        />
      )}
    </div>
  );
}

function FlightInfoCard({ flight, manifest, onClose }) {
  const used = Number(flight.used ?? 0);
  const cap = Number(flight.capacity ?? 0);
  const pct = cap > 0 ? Math.round((used / cap) * 100) : 0;
  const free = Math.max(cap - used, 0);
  const fmt = (iso) => {
    if (!iso) return "—";
    const d = new Date(`${iso}Z`);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toISOString().slice(11, 16) + " UTC";
  };
  return (
    <div className="absolute top-1/2 right-3 -translate-y-1/2 z-[3500] w-72 rounded-xl border border-info/40 bg-surface-1/95 backdrop-blur px-4 py-3 shadow-lg shadow-info/10 text-slate-200">
      <div className="flex items-start justify-between gap-2">
        <div>
          <div className="text-[10px] uppercase tracking-wider text-info font-semibold">Vuelo</div>
          <div className="font-bold text-base text-white">{flight.id ?? flight.idVueloInstancia}</div>
          <div className="text-xs text-slate-400 mt-0.5">{flight.origin} → {flight.dest}</div>
        </div>
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="rounded-md border border-slate-700 bg-surface-2/60 p-1 text-slate-300 hover:text-white hover:bg-surface-2"
          >
            <span className="block leading-none text-xs px-1">×</span>
          </button>
        )}
      </div>

      <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
        <Stat label="Maletas" value={`${used}/${cap}`} tone="info" />
        <Stat label="Ocupacion" value={`${pct}%`} tone={pct >= 85 ? "danger" : pct >= 60 ? "warning" : "success"} />
        <Stat
          label="Pedidos a bordo"
          value={
            manifest.status === "loading" ? "…" :
            manifest.status === "ready" ? String(manifest.pedidos) :
            "—"
          }
          tone="info"
        />
        <Stat label="Capacidad libre" value={String(free)} tone={free === 0 ? "danger" : "success"} />
      </div>

      <div className="mt-3 grid grid-cols-2 gap-2 text-[11px] text-slate-400 border-t border-slate-800 pt-2">
        <div>
          <div className="uppercase text-[9px] tracking-wider text-slate-500">Salida</div>
          <div className="text-slate-200 font-medium">{fmt(flight.depTime)}</div>
        </div>
        <div className="text-right">
          <div className="uppercase text-[9px] tracking-wider text-slate-500">Llegada</div>
          <div className="text-slate-200 font-medium">{fmt(flight.arrTime)}</div>
        </div>
      </div>
    </div>
  );
}

function AirportInfoCard({ airport, onClose }) {
  const used = Number(airport.used ?? 0);
  const cap = Number(airport.capacity ?? 0);
  const pct = cap > 0 ? Math.round((used / cap) * 100) : 0;
  const free = Math.max(cap - used, 0);
  return (
    <div className="absolute top-1/2 left-3 -translate-y-1/2 z-[3500] w-64 rounded-xl border border-blue-400/40 bg-surface-1/95 backdrop-blur px-4 py-3 shadow-lg shadow-blue-400/10 text-slate-200">
      <div className="flex items-start justify-between gap-2">
        <div>
          <div className="text-[10px] uppercase tracking-wider text-blue-400 font-semibold">Aeropuerto</div>
          <div className="font-bold text-base text-white">{airport.iata}</div>
          <div className="text-xs text-slate-400 mt-0.5">{airport.name}</div>
          <div className="text-[10px] text-slate-500">{airport.city} · {airport.continent}</div>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Cerrar"
          className="rounded-md border border-slate-700 bg-surface-2/60 p-1 text-slate-300 hover:text-white hover:bg-surface-2"
        >
          <span className="block leading-none text-xs px-1">×</span>
        </button>
      </div>

      <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
        <Stat label="Maletas" value={`${used}/${cap}`} tone="info" />
        <Stat label="Ocupacion" value={`${pct}%`} tone={pct >= 85 ? "danger" : pct >= 60 ? "warning" : "success"} />
        <Stat label="Capacidad libre" value={String(free)} tone={free === 0 ? "danger" : "success"} />
        <Stat label="GMT" value={airport.gmt != null ? (airport.gmt > 0 ? `+${airport.gmt}` : String(airport.gmt)) : "—"} tone="info" />
      </div>
    </div>
  );
}

function Stat({ label, value, tone = "info" }) {
  const toneClass = tone === "danger" ? "text-danger" : tone === "warning" ? "text-warning" : tone === "success" ? "text-success" : "text-info";
  return (
    <div className="rounded-lg border border-slate-800 bg-surface-2/60 px-2 py-1.5">
      <div className="text-[9px] uppercase tracking-wider text-slate-500">{label}</div>
      <div className={`text-sm font-bold tabular-nums ${toneClass}`}>{value}</div>
    </div>
  );
}

export default AirportMap;
