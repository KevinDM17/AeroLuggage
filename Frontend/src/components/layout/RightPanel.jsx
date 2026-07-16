import { forwardRef, memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Virtuoso } from "react-virtuoso";
import { useLocation } from "react-router-dom";
import { Ban, Filter, PanelRightClose, Globe, Info, ChevronDown, Plane, RefreshCw, Package, Luggage, ArrowDownUp, ArrowUp, ArrowDown, Route, X, Crosshair, MapPin, Calendar, Clock } from "lucide-react";
import { useFetch } from "../../hooks/useFetch";
import { useStompPublish, useStompSubscribe } from "../../hooks/useStomp";
import { cancelFlight, listFlightPlans, listFlights } from "../../api/flights";
import { listOrders } from "../../api/orders";
import { listAirports } from "../../api/airports";
import { listMaletas } from "../../api/maletas";
import { listRutas } from "../../api/rutas";
import { cancelarVueloProgramadoOperacionesDiaADia, obtenerContenidoAlmacen, obtenerEnviosOperacionesDiaADia, obtenerEnviosPanel, obtenerMaletasOperacionesDiaADia, obtenerManifiestoVuelo, obtenerManifiestoVueloOperacionesDiaADia, obtenerRutaMaleta, obtenerRutaMaletaOperacionesDiaADia, obtenerRutasEnvio, obtenerRutasEnvioOperacionesDiaADia, obtenerRutasOperacionesDiaADia, obtenerVuelosOperacionesDiaADia, obtenerSnapshotSimulacionPeriodo } from "../../api/simulator";
import { adaptFlightInstance } from "../../api/flightInstances";
import { apiGet, USE_MOCK } from "../../api/client";
import { useMapFocus } from "../../context/MapFocusContext";
import Modal from "../ui/Modal";
import { LoadingState, EmptyState, ErrorState } from "../ui/States";
import { useToast } from "../ui/Toast";

const FLIGHT_STATUS_FILTERS = [
  { value: "DEFAULT", label: "Activos" },
  { value: "ALL", label: "Todos" },
  { value: "PROGRAMADO", label: "Programado" },
  { value: "CONFIRMADO", label: "Confirmado" },
  { value: "EN_PROGRESO", label: "En progreso" },
  { value: "FINALIZADO", label: "Finalizado" },
  { value: "CANCELADO", label: "Cancelado" },
];

const PANEL_TABS = [
  { id: "flights", label: "Vuelos" },
  { id: "orders", label: "Pedidos" },
  { id: "airports", label: "Aerop." },
  { id: "routes", label: "Rutas" },
  { id: "bags", label: "Maletas" },
];

const flightStatusColor = (s) => {
  switch ((s ?? "").toUpperCase().replace(/_/g, " ")) {
    case "PROGRAMADO": return "bg-slate-500 text-white";
    case "EN PROGRESO": return "bg-sky-500 text-slate-950";
    case "CONFIRMADO": return "bg-indigo-600 text-white";
    case "CANCELADO": return "bg-fuchsia-600 text-white";
    case "FINALIZADO": return "bg-violet-600 text-white";
    default: return "bg-slate-500 text-white";
  }
};

const occupancyColor = (pct) => pct >= 85 ? "bg-danger" : pct >= 50 ? "bg-warning" : "bg-success";
const occupancyBadgeColor = (pct) => (
  pct >= 85
    ? "border-danger/40 bg-danger/12 text-danger"
    : pct >= 50
      ? "border-warning/40 bg-warning/12 text-warning"
      : "border-success/40 bg-success/12 text-success"
);

const bagStatusColor = (s) => {
  switch (s) {
    case "EN_ALMACEN": return "bg-slate-300 text-slate-800";
    case "EN_TRANSITO": return "bg-sky-500 text-slate-950";
    case "EN_VUELO": return "bg-info text-slate-900";
    case "ENTREGADA": return "bg-success text-emerald-900";
    case "POR_RECOGER": return "bg-purple-500 text-purple-100";
    case "EXTRAVIADA": return "bg-danger text-white";
    default: return "bg-slate-500 text-white";
  }
};

const bagStatusLabel = (s) => (s ?? "").replace(/_/g, " ");

const routeStatusColor = (s) => {
  switch (s) {
    case "PLANIFICADA": return "border-info/40 text-info";
    case "EN_CURSO":
    case "ACTIVA":
    case "REPLANIFICADA": return "border-sky-500/40 text-sky-400";
    case "COMPLETADA": return "border-success/40 text-success";
    default: return "border-slate-700 text-slate-400";
  }
};

const ROUTE_STATUSES = ["PLANIFICADA", "ACTIVA", "COMPLETADA", "REPLANIFICADA"];

const EMPTY_MANIFEST = { bags: [], orders: [] };

const PanelScroller = forwardRef(function PanelScroller({ style, className, children, ...props }, ref) {
  return (
    <div {...props} ref={ref} style={style} className={className ? `${className} app-scrollbar` : "app-scrollbar"}>
      {children}
    </div>
  );
});

const FlightItem = memo(function FlightItem({ flight, cityByIata, continentByIata, onCancel, canceling, loadManifest, onFocus, onFocusAirport, onDeselect, isSelected, forceExpanded = false, onExpand, onCollapse }) {
  const [expanded, setExpanded] = useState(false);
  const pct = flight.capacity > 0 ? Math.round((flight.used / flight.capacity) * 100) : 0;
  const normalizedStatus = normalizeFlightStatus(flight.status);
  const canCancel = normalizedStatus === "PROGRAMADO";

  // El manifiesto (envios/maletas a bordo) se pide al back solo cuando la UT
  // esta expandida. La unica fuente que enlaza una UT con su carga son las
  // rutas globales de la sesion, por eso se resuelve en el back y no aqui.
  const flightKey = flight.idVueloInstancia ?? flight.id;
  const [manifest, setManifest] = useState(EMPTY_MANIFEST);
  const [manifestStatus, setManifestStatus] = useState("idle");

  useEffect(() => {
    if (forceExpanded) setExpanded(true);
  }, [forceExpanded]);

  useEffect(() => {
    if (!expanded || !loadManifest) return undefined;
    let cancelled = false;
    setManifestStatus("loading");
    loadManifest(flightKey)
      .then((data) => {
        if (cancelled) return;
        setManifest({ orders: data?.pedidos ?? [], bags: data?.maletas ?? [] });
        setManifestStatus("ready");
      })
      .catch(() => {
        if (cancelled) return;
        setManifest(EMPTY_MANIFEST);
        setManifestStatus("error");
      });
    return () => {
      cancelled = true;
    };
  }, [expanded, flightKey, loadManifest]);

  return (
    <div className={`mb-2 mr-3 flex h-full cursor-pointer flex-col rounded-xl border px-3 py-3 transition-all duration-200 ${isSelected ? "border-info/60 bg-info/5 ring-1 ring-info/70 shadow-[0_8px_24px_rgba(14,165,233,0.08)]" : "border-slate-800/70 bg-surface-1/65 hover:border-slate-700 hover:bg-surface-2/55"}`} onClick={() => {
      const next = !expanded;
      setExpanded(next);
      if (next) onExpand?.(flight.idVueloInstancia ?? flight.id);
      else onCollapse?.();
    }}>
      <div className="flex justify-between items-start mb-2">
        <div>
          <h4 className="font-bold text-lg text-slate-200 flex items-center gap-1.5"><Plane className="w-4 h-4 shrink-0" />{flight.id}</h4>
          <div className="flex justify-between gap-5 w-full">
            <div className={`mt-3 text-xs font-semibold px-2 py-0.5 rounded ${occupancyColor(pct)} inline-block text-slate-900`}>
            Capacidad: {flight.used}/{flight.capacity}
            </div>
          </div>
        </div>
        <div className="flex flex-col items-end gap-2">
          <div className="flex flex-col items-end gap-4">
            <div className="flex items-end gap-3">
              {isSelected && onDeselect && (
                <button
                  type="button"
                  onClick={(ev) => { ev.stopPropagation(); onDeselect(); }}
                  aria-label={`Quitar seleccion de ${flight.id}`}
                  title="Quitar seleccion"
                  className="rounded-md border border-slate-600/60 bg-slate-700/40 p-1 text-slate-200 transition-colors hover:bg-slate-700/70"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              )}
              {onFocus && (
                <button
                  type="button"
                  onClick={(ev) => { ev.stopPropagation(); onFocus(flight); }}
                  aria-label={`Enfocar ${flight.id} en el mapa`}
                  title="Enfocar en el mapa"
                  className="rounded-md border border-info/40 bg-info/10 p-1 text-info transition-colors hover:bg-info/20"
                >
                  <Crosshair className="h-3.5 w-3.5" />
                </button>
              )}
            </div>
            <span className={`text-[10px] font-bold px-2 py-1 rounded tracking-wider ${flightStatusColor(normalizedStatus)}`}>
              {normalizedStatus.replace(/_/g, " ")}
            </span>
          </div>
          {canCancel && (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                onCancel?.(flight);
              }}
              disabled={canceling}
              className="inline-flex items-center gap-1.5 rounded-md border border-danger/40 bg-danger/10 px-2 py-1 text-[10px] font-bold uppercase tracking-wide text-danger transition-colors hover:bg-danger/20 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <Ban className="h-3.5 w-3.5" />
              {canceling ? "Cancelando" : "Cancelar"}
            </button>
          )}
        </div>
      </div>
      {expanded && (
        <div onClick={(e) => e.stopPropagation()} className="cursor-default text-xs text-slate-400 mt-3 space-y-2 border-t border-slate-800 pt-3">
          <Collapsible title="Origen" defaultOpen>
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center gap-1.5">
                <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                <span className="text-slate-300">{cityByIata.get(flight.origin)}, {continentByIata.get(flight.origin)} ({flight.origin})</span>
                {onFocusAirport && (
                  <button
                    type="button"
                    onClick={(ev) => { ev.stopPropagation(); onFocusAirport(flight.origin); }}
                    title="Enfocar en el mapa"
                    className="rounded-md border border-info/40 bg-info/10 p-0.5 text-info transition-colors hover:bg-info/20 shrink-0"
                  >
                    <Crosshair className="h-3 w-3" />
                  </button>
                )}
              </div>
              <div className="flex items-center gap-3">
                <div className="flex items-center gap-1.5">
                  <Calendar className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  <span>{(formatUtcDateTime(flight.depTime) || "--").split(" ")[0]}</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  <span>{(formatUtcDateTime(flight.depTime) || "--").split(" ")[1]}</span>
                </div>
              </div>
            </div>
          </Collapsible>
          <Collapsible title="Destino" defaultOpen>
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center gap-1.5">
                <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                <span className="text-slate-300">{cityByIata.get(flight.dest)}, {continentByIata.get(flight.dest)} ({flight.dest})</span>
                {onFocusAirport && (
                  <button
                    type="button"
                    onClick={(ev) => { ev.stopPropagation(); onFocusAirport(flight.dest); }}
                    title="Enfocar en el mapa"
                    className="rounded-md border border-info/40 bg-info/10 p-0.5 text-info transition-colors hover:bg-info/20 shrink-0"
                  >
                    <Crosshair className="h-3 w-3" />
                  </button>
                )}
              </div>
              <div className="flex items-center gap-3">
                <div className="flex items-center gap-1.5">
                  <Calendar className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  <span>{(formatUtcDateTime(flight.arrTime) || "--").split(" ")[0]}</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  <span>{(formatUtcDateTime(flight.arrTime) || "--").split(" ")[1]}</span>
                </div>
              </div>
            </div>
          </Collapsible>

          {manifestStatus === "loading" ? (
            <div className="text-[11px] text-slate-500 px-3">Cargando manifiesto...</div>
          ) : manifestStatus === "error" ? (
            <div className="text-[11px] text-danger px-3">No se pudo cargar el manifiesto de esta UT.</div>
          ) : (
            <>
              <Collapsible title={`Envios a bordo (${manifest.orders.length})`} defaultOpen>
                {manifest.orders.length === 0 ? (
                  <div className="text-[11px] text-slate-500 pl-1">Sin envios asignados a esta UT.</div>
                ) : (
                  <ul className="space-y-1">
                    {manifest.orders.map((o) => (
                      <li key={o.id} className="flex items-center justify-between gap-2">
                        <span className="truncate font-medium text-slate-200">{o.id}</span>
                        <span className="shrink-0 text-[10px] text-slate-400">
                          {o.origin} {"->"} {o.dest} · {o.bags} mal.
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </Collapsible>

              <Collapsible title={`Maletas a bordo (${manifest.bags.length})`} defaultOpen>
                {manifest.bags.length === 0 ? (
                  <div className="text-[11px] text-slate-500 pl-1">Sin maletas asignadas a esta UT.</div>
                ) : (
                  <ul className="space-y-1 max-h-44 overflow-y-auto no-scrollbar pr-1">
                    {manifest.bags.map((b) => (
                      <li key={b.idMaleta} className="flex items-center justify-between gap-2">
                        <span className="truncate text-slate-200">{b.idMaleta}</span>
                        <span className={`shrink-0 rounded px-1.5 py-0.5 text-[9px] font-bold tracking-wider ${bagStatusColor(b.estado)}`}>
                          {bagStatusLabel(b.estado)}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </Collapsible>
            </>
          )}
        </div>
      )}
    </div>
  );
});

function ManifestSection({ icon: Icon, title, count, emptyLabel, scroll = false, children }) {
  return (
    <div>
      <div className="mb-1.5 flex items-center gap-1.5 font-semibold text-slate-300">
        <Icon className="h-3.5 w-3.5" />
        {title} ({count})
      </div>
      {count === 0 ? (
        <div className="pl-1 text-[11px] text-slate-500">{emptyLabel}</div>
      ) : (
        <ul className={`space-y-1 ${scroll ? "max-h-44 overflow-y-auto no-scrollbar pr-1" : ""}`}>
          {children}
        </ul>
      )}
    </div>
  );
}

const OrderItem = memo(function OrderItem({ order }) {
  const [expanded, setExpanded] = useState(false);
  const id = order.id ?? order.idPedido ?? "--";
  const clientId = order.clientId ?? order.idCliente ?? "--";
  const origin =
    order.origin ??
    order.aeropuertoOrigen?.idAeropuerto ??
    order.aeropuertoOrigen ??
    "--";
  const dest =
    order.dest ??
    order.aeropuertoDestino?.idAeropuerto ??
    order.aeropuertoDestino ??
    "--";
  const bags = order.bags ?? order.cantidadMaletas ?? 0;
  const status = order.status ?? order.estado ?? "--";
  const fechaRegistro = order.fechaRegistro ?? "";
  const [fallbackDate = "", fallbackTime = ""] = String(fechaRegistro).split("T");
  const date = order.date ?? fallbackDate;
  const time = order.time ?? fallbackTime.slice(0, 5);

  return (
    <div className="flex flex-col mr-3 border-b border-slate-800/50 h-full cursor-pointer hover:bg-slate-800 transition-colors duration-200" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-center mb-2">
        <h4 className="font-bold text-lg text-slate-200">{id}</h4>
        <span className="text-xs text-slate-400">{bags} maleta{bags !== 1 ? "s" : ""}</span>
      </div>
      {expanded && (
        <div onClick={(e) => e.stopPropagation()} className="cursor-default text-xs text-slate-400 space-y-1">
          <div>Cliente: <span className="text-slate-200">{clientId}</span></div>
          <div>Ruta: <span className="text-slate-200">{origin} {"->"} {dest}</span></div>
          <div>Registrado: <span className="text-slate-200">{date} {time}</span></div>
          <div>Estado: <span className="text-slate-200">{status}</span></div>
        </div>
      )}
    </div>
  );
});

const RouteItem = memo(function RouteItem({ route, onShowRoute, cityByIata, continentByIata }) {
  const [expanded, setExpanded] = useState(false);
  const vuelos = route.vuelos ?? [];
  const stops = useMemo(() => {
    if (vuelos.length === 0) return [];
    const out = [];
    out.push({ type: "node", icao: vuelos[0].aeropuertoOrigen });
    vuelos.forEach((v) => {
      out.push({
        type: "edge",
        codigo: `${v.codigo}`,
        idVueloInstancia: v.idVueloInstancia,
        fechaSalida: `${(v.fechaSalida ?? "").slice(11, 16)}`,
        fechaLlegada: `${(v.fechaLlegada ?? "").slice(11, 16)}`
      });
      out.push({ type: "node", icao: v.aeropuertoDestino });
    });
    return out;
  }, [vuelos]);

  return (
    <div className="mb-2 flex h-full cursor-pointer flex-col rounded-xl border border-slate-800/70 bg-surface-1/65 px-3 py-3 mr-3 transition-all duration-200 hover:border-slate-700 hover:bg-surface-2/55" onClick={() => setExpanded(!expanded)}>
      <div className="flex flex-col justify-between items-start mb-2 gap-4">
        <div>
          <h4 className="font-bold text-lg text-slate-200 flex items-center gap-1.5"><Route className="w-4 h-4 shrink-0" />{route.idRuta}</h4>
          <span className="text-xs text-slate-400">Maleta: {route.idMaleta}</span>
        </div>
        <div className="flex flex-row shrink-0 gap-1.5 items-center w-full justify-between">
          {onShowRoute && (
            <button
              type="button"
              onClick={(ev) => { ev.stopPropagation(); onShowRoute(route.idMaleta); }}
              className="inline-flex items-center gap-1 rounded-md border border-info/40 bg-info/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-info transition-colors hover:bg-info/20"
            >
              <Route className="h-3 w-3" /> Ver en mapa
            </button>
          )}
          <span className={`text-[10px] font-bold px-2 py-1 rounded tracking-wider border bg-transparent ${routeStatusColor(route.estado)}`}>
            {(route.estado ?? "").replace(/_/g, " ")}
          </span>
        </div>
      </div>
      {expanded && (
        <div className="flex flex-col mt-2 cursor-default gap-2 border-t border-t-slate-800/70 pt-3" onClick={(e) => e.stopPropagation()}>
          <div className="rounded-lg border border-slate-800 bg-surface-2/40 px-3 pb-3 space-y-2">
            <div className="flex flex-col mt-4 mb-2">
              {stops.map((stop, j) => {
                if (stop.type === "node") {
                  return (
                    <div key={j} className="flex items-center gap-3 relative z-10">
                      <div className="w-3 h-3 rounded-full bg-success" />
                      <span className="text-xs font-bold text-slate-200">{cityByIata?.get(stop.icao) ?? stop.icao}{continentByIata?.get(stop.icao) ? `, ${continentByIata.get(stop.icao)}` : ""} ({stop.icao})</span>
                    </div>
                  );
                }
                return (
                  <div key={j} className="flex items-center gap-6 my-2 py-2 relative">
                    <div className="w-[1px] h-15 bg-slate-700 absolute left-[5px] top-[-15px] z-0" />
                    <div className="flex gap-2 ml-6 text-[10px] text-slate-400 whitespace-nowrap overflow-hidden text-ellipsis">
                      <Plane className="w-3.5 h-3.5 shrink-0" />
                      {stop.idVueloInstancia ?? stop.codigo}
                      <div className="flex border-l border-l-slate-600 gap-1.5 pl-2">
                        <Clock className="w-3.5 h-3.5 shrink-0" />
                        {stop.fechaSalida}
                        <span>-</span>
                        <Clock className="w-3.5 h-3.5 shrink-0" />
                        {stop.fechaLlegada}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
          <div className="text-[10px] text-slate-400 pt-2">
            Plazo maximo: <span className="text-slate-200">{route.plazoMaximoDias}d</span> · Duracion: <span className="text-slate-200">{(() => { const totalMin = Math.round((route.duracion ?? 0) * 24 * 60); return `${Math.floor(totalMin / 60)}h ${totalMin % 60}m`; })()}</span>
          </div>
        </div>
      )}
    </div>
  );
});

const BagItem = memo(function BagItem({ bag, onShowRoute, cityByIata, continentByIata }) {
  const [expanded, setExpanded] = useState(false);
  const label = bagStatusLabel(bag.estado);
  return (
    <div className="mb-2 mr-3 flex h-full cursor-pointer flex-col rounded-xl border border-slate-800/70 bg-surface-1/65 px-3 py-3 transition-all duration-200 hover:border-slate-700 hover:bg-surface-2/55" onClick={() => setExpanded(!expanded)}>
      <div className="flex flex-col justify-between items-start mb-2 gap-4">
        <h4 className="font-bold text-sm text-slate-200 break-all pr-2 flex items-center gap-1.5"><Luggage className="w-3.5 h-3.5 shrink-0" />{bag.idMaleta}</h4>
        <div className="flex w-full justify-between gap-1.5">
          {onShowRoute && (
            <button
              type="button"
              onClick={(ev) => { ev.stopPropagation(); onShowRoute(bag.idMaleta); }}
              className="inline-flex items-center gap-1 rounded-md border border-info/40 bg-info/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-info transition-colors hover:bg-info/20"
            >
              <Route className="h-3 w-3" /> Ver ruta
            </button>
          )}
          <span className={`text-[10px] font-bold px-2 py-1 rounded tracking-wider whitespace-nowrap ${bagStatusColor(bag.estado)}`}>
            {label}
          </span>
        </div>
      </div>
      {expanded && (
        <div className="cursor-default mt-2" onClick={(e) => e.stopPropagation()}>
          <div className="text-xs border-t border-t-slate-800/70 text-slate-400 pt-3 flex flex-col gap-2.5">
            <div className="flex flex-col gap-1">
              <div className="font-medium text-sm">Pedido</div>
              <div className="flex items-center gap-1.5">
                <Package className="w-3.5 h-3.5 shrink-0" />
                <span className="text-slate-200">{bag.idPedido}</span>
              </div>
            </div>
            <div className="flex flex-col gap-1 border-b border-b-slate-800/70 pb-3">
              <div className="font-medium text-sm">Registro</div>
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center gap-1.5">
                  <Calendar className="w-3.5 h-3.5 shrink-0" />
                  <span className="text-slate-200">{(bag.fechaRegistro ?? "").replace("T", " ").slice(0, 10)}</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 shrink-0" />
                  <span className="text-slate-200">{(bag.fechaRegistro ?? "").replace("T", " ").slice(11, 16)}</span>
                </div>
              </div>
            </div>
            {bag.origen && (
              <div className="flex flex-col gap-1.5">
                <span className="font-medium text-sm">Origen</span>
                <div className="flex gap-1.5">
                  <MapPin className="w-3.5 h-3.5 shrink-0" />
                  <span className="text-slate-200">{cityByIata?.get(bag.origen) ?? bag.origen}{continentByIata?.get(bag.origen) ? `, ${continentByIata.get(bag.origen)}` : ""} ({bag.origen})</span>
                </div>
              </div>
            )}
            {bag.destino && (
              <div className="flex flex-col gap-1.5">
                <span className="font-medium text-sm">Destino</span>
                <div className="flex gap-1.5">
                  <MapPin className="w-3.5 h-3.5 shrink-0" />
                  <span className="text-slate-200">{cityByIata?.get(bag.destino) ?? bag.destino}{continentByIata?.get(bag.destino) ? `, ${continentByIata.get(bag.destino)}` : ""} ({bag.destino})</span>
                </div>
              </div>
            )}
            {(bag.ubicacionActual || bag.origen) && (
              <div className="flex flex-col gap-1.5 border-b border-b-slate-800/70 pb-3">
                <span className="font-medium text-sm">Ubicación</span>
                <div className="flex gap-1.5">
                  {bag.estado === "EN_ALMACEN" || bag.estado === "POR_RECOGER" ? (
                    <MapPin className="w-3.5 h-3.5 shrink-0" />
                  ) : (
                    <Plane className="w-3.5 h-3.5 shrink-0" />
                  )}
                  <span className="text-slate-200 font-semibold">{cityByIata?.get(bag.ubicacionActual || bag.origen) ?? (bag.ubicacionActual || bag.origen)}{continentByIata?.get(bag.ubicacionActual || bag.origen) ? `, ${continentByIata.get(bag.ubicacionActual || bag.origen)}` : ""} ({bag.ubicacionActual || bag.origen})</span>
                </div>
              </div>
            )}
            {bag.horaLlegadaEstimada && (
              <div className="flex flex-col gap-2">
                <div className="font-medium text-sm">Llegada estimada</div>
                <div className="flex flex-col gap-1.5">
                  <div className="flex items-center gap-1.5">
                    <Calendar className="w-3.5 h-3.5 shrink-0" />
                    <span className="text-slate-200">{bag.horaLlegadaEstimada.replace("T", " ").slice(0, 10)}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <Clock className="w-3.5 h-3.5 shrink-0" />
                    <span className="text-slate-200">{bag.horaLlegadaEstimada.replace("T", " ").slice(11, 16)}</span>
                  </div>
                </div>
              </div>
            )}
            {bag.fechaLlegada && (
              <div className="flex flex-col">
                <div className="font-medium text-sm">Entregada</div>
                <div className="flex items-center gap-3">
                  <div className="flex items-center gap-1.5">
                    <Calendar className="w-3.5 h-3.5 shrink-0" />
                    <span className="text-slate-200">{bag.fechaLlegada.replace("T", " ").slice(0, 10)}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <Clock className="w-3.5 h-3.5 shrink-0" />
                    <span className="text-slate-200">{bag.fechaLlegada.replace("T", " ").slice(11, 16)}</span>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
});

const EMPTY_CONTENIDO = {
  pedidosDestinoFinal: [],
  pedidosEnTransito: [],
  maletasDestinoFinal: [],
  maletasEnTransito: [],
  totalMaletasDestinoFinal: 0,
  totalMaletasEnTransito: 0,
  pedidosEntran: [],
  pedidosSalen: [],
  maletasEntran: [],
  maletasSalen: [],
  totalMaletasEntran: 0,
  totalMaletasSalen: 0,
};

// "yyyy-MM-ddTHH:mm:ss" -> "MM-dd HH:mm"
const formatHoraPlan = (h) => (h ? String(h).slice(5, 16).replace("T", " ") : "--");

// RutaVueloResponse -> escala normalizada para el mapa/panel.
const toEscala = (v) => ({
  codigo: v?.codigo,
  idVueloInstancia: v?.idVueloInstancia,
  origen: v?.aeropuertoOrigen,
  destino: v?.aeropuertoDestino,
  salida: v?.fechaSalida,
  llegada: v?.fechaLlegada,
});

/* El backend devuelve UNA ruta por maleta. Aqui agrupamos por itinerario
 * (secuencia de codigos de vuelo) para obtener, por cada ruta distinta:
 *   - escalas: tramos del itinerario
 *   - maletas: TODAS las maletas que viajan por esa ruta
 *   - cantidad: cuantas maletas la usan
 * El orden de los grupos es estable (orden de aparicion) para que coincida
 * entre "Ver rutas" (mapa) y el detalle de la fila. */
function agruparRutasEnvio(rutas) {
  const grupos = new Map();
  for (const r of rutas ?? []) {
    const escalas = (r?.vuelos ?? []).map(toEscala);
    const clave = escalas.map((e) => e.codigo).join(">");
    let g = grupos.get(clave);
    if (!g) {
      g = { clave, escalas, maletas: [] };
      grupos.set(clave, g);
    }
    if (r?.idMaleta) g.maletas.push(r.idMaleta);
  }
  return [...grupos.values()].map((g) => ({ ...g, cantidad: g.maletas.length }));
}

// Seccion plegable con render DIFERIDO: los hijos no se montan hasta abrirla,
// asi las listas pesadas no cargan el DOM mientras estan cerradas.
function Collapsible({ title, defaultOpen = false, children }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="rounded-lg mr-3 border border-slate-800 bg-surface-2/40">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="flex w-full items-center justify-between px-3 py-2 text-[11px] font-bold uppercase tracking-wide text-slate-300 hover:text-white"
      >
        <span>{title}</span>
        <ChevronDown className={`h-4 w-4 text-slate-500 transition-transform ${open ? "rotate-180" : ""}`} />
      </button>
      {open && <div className="space-y-3 px-3 pb-3">{children}</div>}
    </div>
  );
}

const EnvioItem = memo(function EnvioItem({ envio, showOrigin = true, onShowRoute, onLoadRutas, category, isSelected, onFocus, onDeselect, cityByIata, continentByIata, bags }) {
  const [expanded, setExpanded] = useState(false);
  // Rutas agrupadas (cada una con sus maletas), pedidas al back al expandir.
  // No mostramos UT ni escalas aqui: ese detalle vive en "Ver rutas".
  const [rutas, setRutas] = useState([]);
  const [rutasStatus, setRutasStatus] = useState("idle");

  useEffect(() => {
    if (!expanded || !onLoadRutas) return undefined;
    let cancelled = false;
    setRutasStatus("loading");
    Promise.resolve(onLoadRutas(envio.id))
      .then((data) => {
        if (cancelled) return;
        setRutas(Array.isArray(data) ? data : []);
        setRutasStatus("ready");
      })
      .catch(() => {
        if (cancelled) return;
        setRutas([]);
        setRutasStatus("error");
      });
    return () => { cancelled = true; };
  }, [expanded, envio.id, onLoadRutas]);

  return (
    <div className={`flex flex-col border-b border-slate-800/50 h-full cursor-pointer transition-colors duration-200 py-4`} onClick={() => setExpanded(!expanded)}>
      <div className="flex flex-col items-start justify-between gap-4">
        <div className="flex flex-row min-w-0 w-full justify-between">
          <h4 className="truncate text-sm font-bold text-slate-200 flex items-center gap-1.5"><Package className="w-3.5 h-3.5 shrink-0" />{envio.id}</h4>
          <div className="text-xs font-medium text-slate-300">{envio.bags} mal.</div>
        </div>
        <div className="flex shrink-0 flex-row w-full justify-between">
          {envio.horaEntrega && (
            <div className="text-[10px] text-sky-400">Entregado: {formatHoraPlan(envio.horaEntrega)}</div>
          )}
          <div className="flex flex-row w-full justify-between">
            {onShowRoute && (
              <button
                type="button"
                onClick={(ev) => { ev.stopPropagation(); onShowRoute(envio.id); }}
                className="inline-flex items-center gap-1 rounded-md border border-info/40 bg-info/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-info transition-colors hover:bg-info/20"
              >
                <Route className="h-3 w-3" /> Ver rutas
              </button>
            )}
            {isSelected && onDeselect && (
              <button
                type="button"
                onClick={(ev) => { ev.stopPropagation(); onDeselect(); }}
                aria-label={`Quitar seleccion de ${envio.id}`}
                title="Quitar seleccion"
                className="rounded-md border border-slate-600/60 bg-slate-700/40 p-1 text-slate-200 transition-colors hover:bg-slate-700/70"
              >
                <X className="h-3 w-3" />
              </button>
            )}
            {onFocus && (
              <button
                type="button"
                onClick={(ev) => { ev.stopPropagation(); onFocus(envio, category); }}
                aria-label={`Enfocar ${envio.id} en el mapa`}
                title={category === "flight" ? "Enfocar vuelo y ruta" : "Enfocar almacen"}
                className="rounded-md border border-info/40 bg-info/10 p-1 text-info transition-colors hover:bg-info/20"
              >
                <Crosshair className="h-3 w-3" />
              </button>
            )}
            
          </div>
        </div>
      </div>
      {expanded && (
        <div className="mt-2 space-y-2 border-t border-t-slate-800/70 pt-2" onClick={(e) => e.stopPropagation()}>
          <Collapsible title="Informacion del pedido" defaultOpen>
            <div className="flex flex-col gap-2 text-[11px] text-slate-400">
              {envio.fechaRegistro && (
                <div className="flex flex-col gap-1.5">
                  <span className="text-sm font-medium">Registro</span>
                  <div className="flex items-center gap-1.5">
                    <Calendar className="w-3.5 h-3.5 shrink-0" />
                    <span className="text-slate-200">{(envio.fechaRegistro ?? "").replace("T", " ").slice(0, 10)}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <Clock className="w-3.5 h-3.5 shrink-0" />
                    <span className="text-slate-200">{(envio.fechaRegistro ?? "").replace("T", " ").slice(11, 16)}</span>
                  </div>
                </div>
              )}
              {showOrigin && envio.origin && (
                <div className="flex flex-col gap-1.5">
                  <span className="text-sm font-medium">Origen</span>
                  <div className="flex gap-1.5">
                    <MapPin className="w-3.5 h-3.5 shrink-0" />
                    <span className="text-slate-200">{cityByIata?.get(envio.origin) ?? envio.origin}{continentByIata?.get(envio.origin) ? `, ${continentByIata.get(envio.origin)}` : ""} ({envio.origin})</span>
                  </div>
                </div>
              )}
              {envio.dest && (
                <div className="flex flex-col gap-1.5">
                  <span className="text-sm font-medium">Destino</span>
                  <div className="flex gap-1.5">
                    <MapPin className="w-3.5 h-3.5 shrink-0" />
                    <span className="text-slate-200">{cityByIata?.get(envio.dest) ?? envio.dest}{continentByIata?.get(envio.dest) ? `, ${continentByIata.get(envio.dest)}` : ""} ({envio.dest})</span>
                  </div>
                </div>
              )}
            </div>
          </Collapsible>
          {rutasStatus === "loading" && <div className="text-[11px] text-slate-500 px-3">Cargando rutas…</div>}
          {rutasStatus === "error" && <div className="text-[11px] text-danger px-3">No se pudieron cargar las rutas.</div>}
          {rutasStatus === "ready" && rutas.length === 0 && <div className="text-[11px] text-slate-500 px-3">Sin rutas asignadas.</div>}
          {rutasStatus === "ready" && rutas.map((r, ri) => (
            <Collapsible key={ri} title={`Ruta ${ri + 1} (${r.cantidad} maleta${r.cantidad !== 1 ? "s" : ""})`} defaultOpen>
              <div className="text-slate-200 flex flex-col gap-0.5">
                {r.maletas.length
                  ? r.maletas.map((idMaleta, mi) => {
                      const bag = bags?.get(idMaleta);
                      return (
                        <div key={mi} className="flex items-center justify-between gap-2">
                          <div className="flex items-center gap-1 min-w-0">
                            <Luggage className="w-3 h-3 shrink-0 text-slate-400" />
                            <span className="text-[11px] truncate">{idMaleta}</span>
                          </div>
                          {bag?.estado && (
                            <span className={`shrink-0 rounded px-1.5 py-0.5 text-[9px] font-bold tracking-wider ${bagStatusColor(bag.estado)}`}>
                              {bagStatusLabel(bag.estado)}
                            </span>
                          )}
                        </div>
                      );
                    })
                  : <span className="text-[11px] text-slate-500">—</span>}
              </div>
            </Collapsible>
          ))}
        </div>
      )}
    </div>
  );
});

function EnvioSeccion({ title, envios, showOrigin = true, defaultOpen = false, max = 200, onShowRoute, onLoadRutas, category, selectedEnvioId, onFocus, onDeselect, cityByIata, continentByIata, bags }) {
  const shown = envios.slice(0, max);
  return (
    <Collapsible title={`${title} (${envios.length})`} defaultOpen={defaultOpen}>
      {envios.length === 0 ? (
        <div className="pl-1 text-[11px] text-slate-500">Sin envios.</div>
      ) : (
        <>
          {shown.map((e, i) => <EnvioItem key={`${e.id}-${i}`} envio={e} showOrigin={showOrigin} onShowRoute={onShowRoute} onLoadRutas={onLoadRutas} category={category} isSelected={selectedEnvioId === e.id} onFocus={onFocus} onDeselect={onDeselect} cityByIata={cityByIata} continentByIata={continentByIata} bags={bags} />)}
          {envios.length > shown.length && (
            <div className="pt-1 text-[10px] text-slate-500">… y {envios.length - shown.length} mas (usa los filtros)</div>
          )}
        </>
      )}
    </Collapsible>
  );
}

const AirportItem = memo(function AirportItem({
  apt,
  loadContenido,
  onFocus,
  onDeselect,
  isSelected,
  forceExpanded = false,
  onShowFlightPlans,
  showFlightPlansAction = false,
  onExpand,
  onCollapse,
}) {
  const [expanded, setExpanded] = useState(false);
  const pct = apt.capacity > 0 ? Math.round((apt.used / apt.capacity) * 100) : 0;
  const code = apt.iata ?? apt.idAeropuerto ?? "";
  const displayName = apt.city || apt.name || code; // nombre descriptivo

  // Contenido del almacen (envios/maletas presentes) pedido al back al expandir.
  const airportKey = apt.iata ?? apt.idAeropuerto;
  const [contenido, setContenido] = useState(EMPTY_CONTENIDO);
  const [contenidoStatus, setContenidoStatus] = useState("idle");

  useEffect(() => {
    if (forceExpanded) setExpanded(true);
  }, [forceExpanded]);

  useEffect(() => {
    if (!expanded || !loadContenido) return undefined;
    let cancelled = false;
    setContenidoStatus("loading");
    loadContenido(airportKey)
      .then((data) => {
        if (cancelled) return;
        setContenido({
          pedidosDestinoFinal: data?.pedidosDestinoFinal ?? [],
          pedidosEnTransito: data?.pedidosEnTransito ?? [],
          maletasDestinoFinal: data?.maletasDestinoFinal ?? [],
          maletasEnTransito: data?.maletasEnTransito ?? [],
          totalMaletasDestinoFinal: data?.totalMaletasDestinoFinal ?? (data?.maletasDestinoFinal?.length ?? 0),
          totalMaletasEnTransito: data?.totalMaletasEnTransito ?? (data?.maletasEnTransito?.length ?? 0),
          pedidosEntran: data?.pedidosEntran ?? [],
          pedidosSalen: data?.pedidosSalen ?? [],
          maletasEntran: data?.maletasEntran ?? [],
          maletasSalen: data?.maletasSalen ?? [],
          totalMaletasEntran: data?.totalMaletasEntran ?? (data?.maletasEntran?.length ?? 0),
          totalMaletasSalen: data?.totalMaletasSalen ?? (data?.maletasSalen?.length ?? 0),
        });
        setContenidoStatus("ready");
      })
      .catch(() => {
        if (cancelled) return;
        setContenido(EMPTY_CONTENIDO);
        setContenidoStatus("error");
      });
    return () => {
      cancelled = true;
    };
  }, [expanded, airportKey, loadContenido]);

  return (
    <div className={`mb-2 flex h-full cursor-pointer flex-col rounded-xl border px-3 py-3 transition-all duration-200 ${isSelected ? "border-info/60 bg-info/5 ring-1 ring-info/70 shadow-[0_8px_24px_rgba(14,165,233,0.08)]" : "border-slate-800/70 bg-surface-1/65 hover:border-slate-700 hover:bg-surface-2/55"}`} onClick={() => {
      const next = !expanded;
      setExpanded(next);
      if (next) onExpand?.(apt.iata ?? apt.idAeropuerto);
      else onCollapse?.();
    }}>
      <div className="flex justify-between items-center gap-2">
        <div className="min-w-0">
          <h4 className="truncate font-bold text-lg text-slate-200 flex items-center gap-2.5"><MapPin className="w-3.5 h-3.5 shrink-0" />{displayName}</h4>
          {displayName !== code && (
            <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{code}</p>
          )}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <span className={`rounded-full border px-2 py-1 text-[11px] font-semibold ${occupancyBadgeColor(pct)}`}>
            {pct}% ocupado
          </span>
          {isSelected && onDeselect && (
            <button
              type="button"
              onClick={(ev) => { ev.stopPropagation(); onDeselect(); }}
              aria-label={`Quitar seleccion de ${apt.iata}`}
              title="Quitar seleccion"
              className="rounded-md border border-slate-600/60 bg-slate-700/40 p-1 text-slate-200 transition-colors hover:bg-slate-700/70"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
          {onFocus && (
            <button
              type="button"
              onClick={(ev) => { ev.stopPropagation(); onFocus(apt.iata ?? apt.idAeropuerto); }}
              aria-label={`Enfocar ${apt.iata} en el mapa`}
              title="Enfocar en el mapa"
              className="rounded-md border border-info/40 bg-info/10 p-1 text-info transition-colors hover:bg-info/20"
            >
              <Crosshair className="h-3.5 w-3.5" />
            </button>
          )}
        </div>
      </div>
      <div className="mt-2 flex justify-between items-start gap-3 pt-1">
        <div className="flex flex-col gap-0.5">
          <div className="flex items-center gap-1 text-[10px] text-slate-400"><Globe className="w-3 h-3 shrink-0" /> {apt.continent}</div>
        </div>
        <div className="flex flex-col items-end min-w-0">
          <div className="text-[10px] text-slate-400 mb-1">{apt.used} / {apt.capacity} maletas</div>
          <div className="w-24 h-1.5 bg-surface-2 rounded-full overflow-hidden border border-slate-800">
            <div className={`h-full ${occupancyColor(pct)}`} style={{ width: `${pct}%` }} />
          </div>
        </div>
      </div>
      {expanded && (
        <div className="cursor-default" onClick={(e) => e.stopPropagation()}>
          {showFlightPlansAction && onShowFlightPlans ? (
            <div className="mt-3">
              <button
                type="button"
                onClick={() => onShowFlightPlans(apt)}
                className="inline-flex items-center gap-2 rounded-lg border border-info/40 bg-info/10 px-3 py-2 text-xs font-semibold text-info transition-colors hover:bg-info/20"
              >
                <Plane className="h-3.5 w-3.5" />
                Ver vuelos programados
              </button>
            </div>
          ) : null}

          <div className="mt-3 space-y-3 border-t border-slate-800 pt-3 text-xs text-slate-400">
            {contenidoStatus === "loading" ? (
              <div className="text-[11px] text-slate-500">Cargando contenido del almacen...</div>
            ) : contenidoStatus === "error" ? (
              <div className="text-[11px] text-danger">No se pudo cargar el contenido del almacen.</div>
            ) : (
              <>
                <Collapsible title="En el almacen ahora" defaultOpen>
                  <ManifestSection
                    icon={Package}
                    title="Envios · destino final"
                    count={contenido.pedidosDestinoFinal.length}
                    emptyLabel="Sin envios con destino final en este almacen."
                  >
                    {contenido.pedidosDestinoFinal.map((o) => (
                      <li key={`pf-${o.id}`} className="flex items-center justify-between gap-2">
                        <span className="truncate font-medium text-slate-200">{o.id}</span>
                        <span className="shrink-0 text-[10px] text-slate-400">{o.origin} {"->"} {o.dest} · {o.bags} mal.</span>
                      </li>
                    ))}
                  </ManifestSection>

                  <ManifestSection
                    icon={Package}
                    title="Envios · en transito"
                    count={contenido.pedidosEnTransito.length}
                    emptyLabel="Sin envios en transito en este almacen."
                  >
                    {contenido.pedidosEnTransito.map((o) => (
                      <li key={`pt-${o.id}`} className="flex items-center justify-between gap-2">
                        <span className="truncate font-medium text-slate-200">{o.id}</span>
                        <span className="shrink-0 text-[10px] text-slate-400">{o.origin} {"->"} {o.dest} · {o.bags} mal.</span>
                      </li>
                    ))}
                  </ManifestSection>

                  <ManifestSection
                    icon={Luggage}
                    title="Maletas · destino final"
                    count={contenido.totalMaletasDestinoFinal}
                    emptyLabel="Sin maletas con destino final en este almacen."
                    scroll
                  >
                    {contenido.maletasDestinoFinal.map((b) => (
                      <li key={`mf-${b.idMaleta}`} className="flex items-center justify-between gap-2">
                        <span className="truncate text-slate-200">{b.idMaleta}</span>
                        <span className={`shrink-0 rounded px-1.5 py-0.5 text-[9px] font-bold tracking-wider ${bagStatusColor(b.estado)}`}>{bagStatusLabel(b.estado)}</span>
                      </li>
                    ))}
                    {contenido.totalMaletasDestinoFinal > contenido.maletasDestinoFinal.length && (
                      <li key="mf-more" className="pt-1 text-[10px] text-slate-500">… y {contenido.totalMaletasDestinoFinal - contenido.maletasDestinoFinal.length} mas</li>
                    )}
                  </ManifestSection>

                  <ManifestSection
                    icon={Luggage}
                    title="Maletas · en transito"
                    count={contenido.totalMaletasEnTransito}
                    emptyLabel="Sin maletas en transito en este almacen."
                    scroll
                  >
                    {contenido.maletasEnTransito.map((b) => (
                      <li key={`mt-${b.idMaleta}`} className="flex items-center justify-between gap-2">
                        <span className="truncate text-slate-200">{b.idMaleta}</span>
                        <span className={`shrink-0 rounded px-1.5 py-0.5 text-[9px] font-bold tracking-wider ${bagStatusColor(b.estado)}`}>{bagStatusLabel(b.estado)}</span>
                      </li>
                    ))}
                    {contenido.totalMaletasEnTransito > contenido.maletasEnTransito.length && (
                      <li key="mt-more" className="pt-1 text-[10px] text-slate-500">… y {contenido.totalMaletasEnTransito - contenido.maletasEnTransito.length} mas</li>
                    )}
                  </ManifestSection>
                </Collapsible>

                <Collapsible title="Planificado · entran / salen">
                  <ManifestSection
                    icon={Package}
                    title="Envios · entran"
                    count={contenido.pedidosEntran.length}
                    emptyLabel="Sin envios planificados que entren."
                  >
                    {contenido.pedidosEntran.map((o) => (
                      <li key={`pe-${o.id}`} className="flex items-center justify-between gap-2">
                        <span className="truncate font-medium text-slate-200">{o.id}</span>
                        <span className="shrink-0 text-[10px] text-slate-400">{o.origin} {"->"} {o.dest} · {o.bags} mal.{o.time ? ` · ${o.time}` : ""}</span>
                      </li>
                    ))}
                  </ManifestSection>

                  <ManifestSection
                    icon={Luggage}
                    title="Maletas · entran"
                    count={contenido.totalMaletasEntran}
                    emptyLabel="Sin maletas planificadas que entren."
                    scroll
                  >
                    {contenido.maletasEntran.map((b) => (
                      <li key={`me-${b.idMaleta}-${b.vuelo}`} className="flex items-center justify-between gap-2">
                        <span className="truncate text-slate-200">{b.idMaleta}</span>
                        <span className="shrink-0 text-[10px] text-slate-400">{b.vuelo} · {formatHoraPlan(b.hora)}</span>
                      </li>
                    ))}
                    {contenido.totalMaletasEntran > contenido.maletasEntran.length && (
                      <li key="me-more" className="pt-1 text-[10px] text-slate-500">… y {contenido.totalMaletasEntran - contenido.maletasEntran.length} mas</li>
                    )}
                  </ManifestSection>

                  <ManifestSection
                    icon={Package}
                    title="Envios · salen"
                    count={contenido.pedidosSalen.length}
                    emptyLabel="Sin envios planificados que salgan."
                  >
                    {contenido.pedidosSalen.map((o) => (
                      <li key={`psl-${o.id}`} className="flex items-center justify-between gap-2">
                        <span className="truncate font-medium text-slate-200">{o.id}</span>
                        <span className="shrink-0 text-[10px] text-slate-400">{o.origin} {"->"} {o.dest} · {o.bags} mal.{o.time ? ` · ${o.time}` : ""}</span>
                      </li>
                    ))}
                  </ManifestSection>

                  <ManifestSection
                    icon={Luggage}
                    title="Maletas · salen"
                    count={contenido.totalMaletasSalen}
                    emptyLabel="Sin maletas planificadas que salgan."
                    scroll
                  >
                    {contenido.maletasSalen.map((b) => (
                      <li key={`msl-${b.idMaleta}-${b.vuelo}`} className="flex items-center justify-between gap-2">
                        <span className="truncate text-slate-200">{b.idMaleta}</span>
                        <span className="shrink-0 text-[10px] text-slate-400">{b.vuelo} · {formatHoraPlan(b.hora)}</span>
                      </li>
                    ))}
                    {contenido.totalMaletasSalen > contenido.maletasSalen.length && (
                      <li key="msl-more" className="pt-1 text-[10px] text-slate-500">… y {contenido.totalMaletasSalen - contenido.maletasSalen.length} mas</li>
                    )}
                  </ManifestSection>
                </Collapsible>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
});

const ScheduledFlightPlanItem = memo(function ScheduledFlightPlanItem({
  flightPlan,
  simTime,
  onCancel,
  canceling,
}) {
  const gmtOrigin = Number(flightPlan?.gmtOrigin ?? 0);
  const depTimeLocal = String(flightPlan?.depTime ?? "").trim();
  const depTimeUtc = (() => {
    if (!depTimeLocal) return "--:--";
    const parts = depTimeLocal.split(":");
    if (parts.length < 2) return depTimeLocal;
    const localMin = parseInt(parts[0]) * 60 + parseInt(parts[1]);
    if (!Number.isFinite(localMin)) return depTimeLocal;
    const utcMin = ((localMin - gmtOrigin * 60) + 1440) % 1440;
    return `${String(Math.floor(utcMin / 60)).padStart(2, "0")}:${String(utcMin % 60).padStart(2, "0")}`;
  })();

  const timing = useMemo(
    () => buildScheduledFlightTiming(flightPlan, simTime),
    [flightPlan, simTime],
  );

  return (
    <div className="rounded-xl border border-slate-800 bg-surface-2 px-4 py-3">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="font-semibold text-slate-100">
            {flightPlan.origin} {"->"} {flightPlan.dest}
          </div>
          <div className="mt-1 text-sm text-slate-400">
            Sale a las {depTimeUtc} (UTC)
          </div>
          <div className="mt-2 inline-flex rounded-md border border-slate-700 bg-surface-1 px-2.5 py-1 text-[11px] font-semibold text-slate-300">
            {timing.statusLabel}
          </div>
        </div>
        <button
          type="button"
          onClick={() => onCancel?.(flightPlan, timing)}
          disabled={canceling || !timing.resolved}
          className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-xs font-bold uppercase tracking-wide text-danger transition-colors hover:bg-danger/20 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <Ban className="h-3.5 w-3.5" />
          {canceling ? "Cancelando" : "Cancelar"}
        </button>
      </div>
    </div>
  );
});

function ColorLegend() {
  return (
    <details className="group border-t border-slate-800 bg-surface-1/95 px-4 py-3">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-3 rounded-lg border border-slate-800 bg-surface-2 px-3 py-2 text-xs font-bold uppercase tracking-wider text-slate-300 transition-colors hover:border-slate-700 hover:text-white">
        <span className="flex items-center gap-2">
          <Info className="h-4 w-4 text-info" />
          Leyenda
        </span>
        <ChevronDown className="h-4 w-4 text-slate-500 transition-transform group-open:rotate-180" />
      </summary>

      <div className="mt-3 grid gap-3 text-xs text-slate-300">
        <div>
          <div className="mb-2 font-semibold text-slate-200">Aeropuertos</div>
          <LegendRow color="bg-white shadow-[0_0_10px_rgba(255,255,255,0.7)]" label="Blanco" value="vacio / 0% ocupado" />
          <LegendRow color="bg-success shadow-[0_0_10px_rgba(0,255,136,0.7)]" label="Verde" value="menos de 60% ocupado" />
          <LegendRow color="bg-warning shadow-[0_0_10px_rgba(255,221,0,0.7)]" label="Amarillo" value="50% a 84% ocupado" />
          <LegendRow color="bg-danger shadow-[0_0_10px_rgba(255,59,48,0.7)]" label="Rojo" value="85% o mas ocupado" />
        </div>
        <div>
          <div className="mb-2 font-semibold text-slate-200">Carga de vuelos</div>
          <FlightLegendRow color="text-white/60" label="Blanco translucido" value="vuelo vacio / 0% ocupado" />
          <FlightLegendRow color="text-success" label="Verde" value="menos de 60% ocupado" />
          <FlightLegendRow color="text-warning" label="Amarillo" value="50% a 84% ocupado" />
          <FlightLegendRow color="text-danger" label="Rojo" value="85% o mas ocupado" />
        </div>
      </div>
    </details>
  );
}

function LegendRow({ color, label, value }) {
  return (
    <div className="mb-1.5 flex items-center gap-2 last:mb-0">
      <span className={`h-3 w-3 shrink-0 rounded-full ${color}`} />
      <span className="font-semibold text-slate-200">{label}</span>
      <span className="text-slate-400">{value}</span>
    </div>
  );
}

function FlightLegendRow({ color, label, value }) {
  return (
    <div className="mb-1.5 flex items-center gap-2 last:mb-0">
      <Plane className={`h-4 w-4 shrink-0 ${color}`} />
      <span className="font-semibold text-slate-200">{label}</span>
      <span className="text-slate-400">{value}</span>
    </div>
  );
}

function createStaticSource(data = []) {
  return { data, loading: false, error: null, refetch: () => Promise.resolve() };
}

function parseUtcDateTime(value) {
  if (!value) return Number.NaN;
  const raw = String(value).trim();
  const normalized = /z$/i.test(raw) ? raw : `${raw}Z`;
  return Date.parse(normalized);
}

function padDatePart(value) {
  return String(value).padStart(2, "0");
}

function formatUtcDateTime(value) {
  const utcMs = parseUtcDateTime(value);
  if (!Number.isFinite(utcMs)) return value || "--";

  const date = new Date(utcMs);
  return `${date.getUTCFullYear()}-${padDatePart(date.getUTCMonth() + 1)}-${padDatePart(date.getUTCDate())} ${padDatePart(date.getUTCHours())}:${padDatePart(date.getUTCMinutes())} UTC`;
}

function flightOccupancyRatio(flight) {
  const cap = Number(flight?.capacity) || 0;
  const used = Number(flight?.used) || 0;
  return cap > 0 ? used / cap : 0;
}

function compareFlightsByTime(left, right, field) {
  const leftTime = parseUtcDateTime(left?.[field]);
  const rightTime = parseUtcDateTime(right?.[field]);
  const leftValid = Number.isFinite(leftTime);
  const rightValid = Number.isFinite(rightTime);
  if (leftValid && rightValid) return leftTime - rightTime;
  if (leftValid) return -1;   // los invalidos van al final
  if (rightValid) return 1;
  return 0;
}

// Ordena las UT por el criterio elegido (req 11-15) con direccion asc/desc.
// El desempate por codigo mantiene el orden estable entre iguales.
function sortFlights(rows = [], key = "departure", dir = "asc") {
  const factor = dir === "desc" ? -1 : 1;
  return [...rows].sort((left, right) => {
    let cmp;
    switch (key) {
      case "occupancy":
        cmp = flightOccupancyRatio(left) - flightOccupancyRatio(right);
        break;
      case "arrival":
        cmp = compareFlightsByTime(left, right, "arrTime");
        break;
      case "origin":
        cmp = String(left?.origin ?? "").localeCompare(String(right?.origin ?? ""));
        break;
      case "dest":
        cmp = String(left?.dest ?? "").localeCompare(String(right?.dest ?? ""));
        break;
      case "departure":
      default:
        cmp = compareFlightsByTime(left, right, "depTime");
        break;
    }
    if (cmp === 0) cmp = String(left?.id ?? "").localeCompare(String(right?.id ?? ""));
    return factor * cmp;
  });
}

function sortBags(rows = [], key = "code", dir = "asc") {
  const factor = dir === "desc" ? -1 : 1;
  return [...rows].sort((left, right) => {
    let cmp;
    switch (key) {
      case "registration":
        cmp = String(left?.fechaRegistro ?? "").localeCompare(String(right?.fechaRegistro ?? ""));
        break;
      case "origin":
        cmp = String(left?.origen ?? "").localeCompare(String(right?.origen ?? ""));
        break;
      case "dest":
        cmp = String(left?.destino ?? "").localeCompare(String(right?.destino ?? ""));
        break;
      case "arrival":
        cmp = String(left?.horaLlegadaEstimada ?? "").localeCompare(String(right?.horaLlegadaEstimada ?? ""));
        break;
      case "code":
      default:
        cmp = String(left?.idMaleta ?? "").localeCompare(String(right?.idMaleta ?? ""));
        break;
    }
    return factor * cmp;
  });
}

function sortOrders(rows = [], key = "code", dir = "asc") {
  const factor = dir === "desc" ? -1 : 1;
  return [...rows].sort((left, right) => {
    let cmp;
    switch (key) {
      case "registration":
        cmp = String(left?.fechaRegistro ?? "").localeCompare(String(right?.fechaRegistro ?? ""));
        break;
      case "origin":
        cmp = String(left?.origin ?? "").localeCompare(String(right?.origin ?? ""));
        break;
      case "dest":
        cmp = String(left?.dest ?? "").localeCompare(String(right?.dest ?? ""));
        break;
      case "routes":
        cmp = (left?.rutas?.length ?? 0) - (right?.rutas?.length ?? 0);
        break;
      case "bags":
        cmp = (left?.bags ?? 0) - (right?.bags ?? 0);
        break;
      case "code":
      default:
        cmp = String(left?.id ?? "").localeCompare(String(right?.id ?? ""));
        break;
    }
    return factor * cmp;
  });
}

function sortRoutes(rows = [], key = "code", dir = "asc") {
  const factor = dir === "desc" ? -1 : 1;
  return [...rows].sort((left, right) => {
    let cmp;
    const leftVuelos = left?.vuelos ?? [];
    const rightVuelos = right?.vuelos ?? [];
    switch (key) {
      case "departure":
        cmp = String(leftVuelos[0]?.fechaSalida ?? "").localeCompare(String(rightVuelos[0]?.fechaSalida ?? ""));
        break;
      case "arrival":
        cmp = String(leftVuelos[leftVuelos.length - 1]?.fechaLlegada ?? "")
              .localeCompare(String(rightVuelos[rightVuelos.length - 1]?.fechaLlegada ?? ""));
        break;
      case "duration":
        cmp = (left?.duracion ?? 0) - (right?.duracion ?? 0);
        break;
      case "flights":
        cmp = leftVuelos.length - rightVuelos.length;
        break;
      case "slack":
        cmp = (left?.plazoMaximoDias ?? 0) - (left?.duracion ?? 0)
            - (right?.plazoMaximoDias ?? 0) + (right?.duracion ?? 0);
        break;
      case "deadline":
        cmp = (left?.plazoMaximoDias ?? 0) - (right?.plazoMaximoDias ?? 0);
        break;
      case "code":
      default:
        cmp = String(left?.idRuta ?? "").localeCompare(String(right?.idRuta ?? ""));
        break;
    }
    return factor * cmp;
  });
}

function normalizeFlightStatus(status) {
  return String(status ?? "")
    .trim()
    .toUpperCase()
    .replace(/\s+/g, "_");
}

function formatLocalDateLabel(utcLikeMs) {
  const date = new Date(utcLikeMs);
  return `${padDatePart(date.getUTCDate())}/${padDatePart(date.getUTCMonth() + 1)}/${date.getUTCFullYear()}`;
}

function buildScheduledFlightTiming(flightPlan, simTime) {
  const utcMs = parseUtcDateTime(simTime);
  const gmtOrigin = Number(flightPlan?.gmtOrigin ?? 0);
  const depRaw = String(flightPlan?.depTime ?? "").trim();
  const departureLabel = depRaw ? depRaw.slice(0, 5) : "--:--";
  const [depHourRaw, depMinuteRaw] = depRaw.split(":");
  const depHour = Number(depHourRaw);
  const depMinute = Number(depMinuteRaw);

  if (!Number.isFinite(utcMs) || !Number.isFinite(depHour) || !Number.isFinite(depMinute)) {
    return {
      resolved: false,
      departureLabel,
      statusLabel: "Esperando hora simulada",
      effectiveDateIso: null,
      effectiveDateLabel: "--",
      appliesToday: null,
      cutoffLabel: "--:--",
      confirmMessage: "",
    };
  }

  const depLocalMin = depHour * 60 + depMinute;
  const depUtcMin = ((depLocalMin - gmtOrigin * 60) + 1440) % 1440;
  const depUtcHour = Math.floor(depUtcMin / 60);
  const depUtcMinute = depUtcMin % 60;

  const simDate = new Date(utcMs);
  const departureTodayUtcMs = Date.UTC(
    simDate.getUTCFullYear(),
    simDate.getUTCMonth(),
    simDate.getUTCDate(),
    depUtcHour,
    depUtcMinute,
    0,
    0,
  );
  const cutoffUtcMs = departureTodayUtcMs - 60 * 60 * 1000;
  const appliesToday = utcMs <= cutoffUtcMs;
  const effectiveUtcMs = departureTodayUtcMs + (appliesToday ? 0 : 24 * 60 * 60 * 1000);
  const effectiveDate = new Date(effectiveUtcMs);
  const effectiveDateIso = `${effectiveDate.getUTCFullYear()}-${padDatePart(effectiveDate.getUTCMonth() + 1)}-${padDatePart(effectiveDate.getUTCDate())}`;
  const effectiveDateLabel = formatLocalDateLabel(effectiveUtcMs);
  const cutoffUtcMin = ((depUtcMin - 60) + 1440) % 1440;
  const cutoffLabel = `${padDatePart(Math.floor(cutoffUtcMin / 60))}:${padDatePart(cutoffUtcMin % 60)}`;
  const statusLabel = appliesToday
    ? `Aplica hoy · ${effectiveDateLabel}`
    : `Aplica manana · ${effectiveDateLabel}`;

  return {
    resolved: true,
    departureLabel,
    statusLabel,
    effectiveDateIso,
    effectiveDateLabel,
    appliesToday,
    cutoffLabel,
    confirmMessage: `Se cancelara la salida ${flightPlan.origin} -> ${flightPlan.dest} de las ${depRaw} para el ${effectiveDateLabel}. Regla aplicada: hasta ${cutoffLabel} UTC afecta el mismo dia; despues de esa hora afecta el dia siguiente.`,
  };
}

function airportOccupancyRatio(apt) {
  const cap = Number(apt?.capacity) || 0;
  const used = Number(apt?.used) || 0;
  return cap > 0 ? used / cap : 0;
}

// Estado de semaforo incluyendo "vacio" (sin stock) -> req 10/11.
function semaforoState(used, capacity) {
  const u = Number(used) || 0;
  if (u <= 0) return "VACIO";
  const c = Number(capacity) || 0;
  const pct = c > 0 ? (u / c) * 100 : 0;
  if (pct >= 85) return "ROJO";
  if (pct >= 50) return "AMBAR";
  return "VERDE";
}

const SEMAFORO_OPCIONES = [
  { value: "ALL", label: "Semaforo: todos" },
  { value: "VACIO", label: "Semaforo: vacio" },
  { value: "VERDE", label: "Semaforo: verde" },
  { value: "AMBAR", label: "Semaforo: ambar" },
  { value: "ROJO", label: "Semaforo: rojo" },
];

// Ordena almacenes por ocupacion (req 26), proxima salida (req 27) o proxima
// llegada (req 28) de UT. nextTimes mapea iata -> {dep, arr} en ms (Infinity si
// no hay UT proxima); los Infinity quedan al final.
function sortAirports(rows = [], key = "occupancy", dir = "desc", nextTimes = new Map()) {
  const factor = dir === "desc" ? -1 : 1;
  return [...rows].sort((left, right) => {
    let cmp;
    if (key === "name") {
      cmp = String(left?.iata ?? "").localeCompare(String(right?.iata ?? ""));
    } else if (key === "nextDep" || key === "nextArr") {
      const field = key === "nextDep" ? "dep" : "arr";
      const lv = nextTimes.get(left?.iata)?.[field] ?? Infinity;
      const rv = nextTimes.get(right?.iata)?.[field] ?? Infinity;
      cmp = lv - rv;
      if (Number.isNaN(cmp)) cmp = 0; // Infinity - Infinity
    } else {
      cmp = airportOccupancyRatio(left) - airportOccupancyRatio(right);
    }
    if (cmp === 0) cmp = String(left?.iata ?? "").localeCompare(String(right?.iata ?? ""));
    return factor * cmp;
  });
}

export default function RightPanel({
  onClose,
  simulationPanelData,
  setSimulationPanelData,
  setCancelledFlightIds,
}) {
  const toast = useToast();
  const [activeTab, setActiveTab] = useState("flights");
  // Refs a las listas virtualizadas para poder desplazar hasta un item concreto
  // cuando se selecciona un almacen/UT en el mapa (req 6/8). scrollTarget guarda
  // la peticion pendiente hasta que la pestaña correcta y sus filas esten listas.
  const flightsVirtuosoRef = useRef(null);
  const airportsVirtuosoRef = useRef(null);
  const routesVirtuosoRef = useRef(null);
  const bagsVirtuosoRef = useRef(null);
  const savedRouteScrollIndexRef = useRef(0);
  const savedBagScrollIndexRef = useRef(0);
  const [scrollTarget, setScrollTarget] = useState(null);
  const [query, setQuery] = useState("");
  const [flightStatusFilter, setFlightStatusFilter] = useState("DEFAULT");
  const [flightOriginFilter, setFlightOriginFilter] = useState("ALL");
  const [flightDestFilter, setFlightDestFilter] = useState("ALL");
  const [flightCodePattern, setFlightCodePattern] = useState("");
  const [flightSortKey, setFlightSortKey] = useState("departure");
  const [flightSortDir, setFlightSortDir] = useState("asc");
  const [airportRegionFilter, setAirportRegionFilter] = useState("ALL");
  const [airportCodePattern, setAirportCodePattern] = useState("");
  const [airportSortKey, setAirportSortKey] = useState("name");
  const [airportSortDir, setAirportSortDir] = useState("asc");
  const [envioOriginFilter, setEnvioOriginFilter] = useState("ALL");
  const [envioDestFilter, setEnvioDestFilter] = useState("ALL");
  const [enviosData, setEnviosData] = useState({ planificados: [], enVuelos: [], entregados: [] });
  const [enviosStatus, setEnviosStatus] = useState("idle");
  const [selectedEnvioId, setSelectedEnvioId] = useState(null);
  const [routeOriginFilter, setRouteOriginFilter] = useState("ALL");
  const [routeDestFilter, setRouteDestFilter] = useState("ALL");
  const [routeStatusFilter, setRouteStatusFilter] = useState("ALL");
  const [routeSortKey, setRouteSortKey] = useState("code");
  const [routeSortDir, setRouteSortDir] = useState("asc");
  const [routePlazoFilter, setRoutePlazoFilter] = useState("ALL");
  const [bagStatusFilter, setBagStatusFilter] = useState("ALL");
  const [bagOriginFilter, setBagOriginFilter] = useState("ALL");
  const [bagDestFilter, setBagDestFilter] = useState("ALL");
  const [orderSortKey, setOrderSortKey] = useState("code");
  const [orderSortDir, setOrderSortDir] = useState("asc");
  const [bagSortKey, setBagSortKey] = useState("code");
  const [bagSortDir, setBagSortDir] = useState("asc");
  const [airportSemaforo, setAirportSemaforo] = useState("ALL");
  const [flightSemaforo, setFlightSemaforo] = useState("ALL");
  const [cancelingFlightId, setCancelingFlightId] = useState(null);
  const [flightPlansModalAirport, setFlightPlansModalAirport] = useState(null);
  const [airportFlightPlans, setAirportFlightPlans] = useState([]);
  const [airportFlightPlansStatus, setAirportFlightPlansStatus] = useState("idle");
  const [cancelingFlightPlanId, setCancelingFlightPlanId] = useState(null);
  const [pendingFlightPlanCancellation, setPendingFlightPlanCancellation] = useState(null);
  const [flightPlanConfirmation, setFlightPlanConfirmation] = useState(null);
  const publish = useStompPublish();
  const sessionId = simulationPanelData?.sessionId ?? null;
  const { mapHighlight, setMapHighlight, selected, setSelected, setMapFocus, panelFocus, setMapDim, setCancellationNotice, setFlightManifestLoader } = useMapFocus();
  const location = useLocation();
  const isOperacionesDiaADia = location.pathname === "/operaciones";
  const isPeriodo = location.pathname === "/simulator/period";
  const isColapso = location.pathname === "/simulator/collapse";
  const isSimulacionPeriodo = isPeriodo || isColapso;
  const isSimulator = location.pathname === "/operaciones" || location.pathname.startsWith("/simulator");
  const statusTopic = !USE_MOCK && isSimulacionPeriodo && sessionId ? `/topic/simulacion/${sessionId}/estado` : null;
  const { data: periodStatusMessage } = useStompSubscribe(statusTopic, { enabled: Boolean(statusTopic) });
  const simulationLoaded = !isSimulator || simulationPanelData?.loaded === true;
  const activeTabLabel = PANEL_TABS.find((tab) => tab.id === activeTab)?.label ?? "";

  const clearFlightFilters = () => {
    setFlightOriginFilter("ALL");
    setFlightDestFilter("ALL");
    setFlightCodePattern("");
    setFlightSemaforo("ALL");
  };

  const resetFlightSort = () => {
    setFlightSortKey("departure");
    setFlightSortDir("asc");
  };

  const clearAirportFilters = () => {
    setAirportRegionFilter("ALL");
    setAirportCodePattern("");
    setAirportSemaforo("ALL");
  };

  const resetAirportSort = () => {
    setAirportSortKey("name");
    setAirportSortDir("asc");
  };

  const clearEnvioFilters = () => {
    setEnvioOriginFilter("ALL");
    setEnvioDestFilter("ALL");
  };

  const clearRouteFilters = () => {
    setRouteOriginFilter("ALL");
    setRouteDestFilter("ALL");
    setRouteStatusFilter("ALL");
    setRoutePlazoFilter("ALL");
  };

  const clearBagFilters = () => {
    setBagStatusFilter("ALL");
    setBagOriginFilter("ALL");
    setBagDestFilter("ALL");
  };

  useEffect(() => {
    if (!isSimulator || simulationLoaded) return;
    setActiveTab("flights");
    setQuery("");
    setFlightStatusFilter("DEFAULT");
    clearFlightFilters();
    resetFlightSort();
    clearAirportFilters();
    resetAirportSort();
    clearEnvioFilters();
    setCancelingFlightId(null);
    setFlightPlansModalAirport(null);
    setAirportFlightPlans([]);
    setAirportFlightPlansStatus("idle");
    setCancelingFlightPlanId(null);
    setPendingFlightPlanCancellation(null);
    setFlightPlanConfirmation(null);
  }, [isSimulator, simulationLoaded]);

  const onTabChange = (tab) => {
    setActiveTab(tab);
    setQuery("");
    clearFlightFilters();
    resetFlightSort();
    clearAirportFilters();
    resetAirportSort();
    clearEnvioFilters();
    if (tab !== "flights") {
      setFlightStatusFilter("ALL");
    } else {
      setFlightStatusFilter("DEFAULT");
    }
  };

  const closeFlightPlansModal = useCallback(() => {
    setFlightPlansModalAirport(null);
    setAirportFlightPlans([]);
    setAirportFlightPlansStatus("idle");
    setCancelingFlightPlanId(null);
    setPendingFlightPlanCancellation(null);
    setFlightPlanConfirmation(null);
  }, []);

  const openFlightPlansModal = useCallback((airport) => {
    setFlightPlansModalAirport(airport ?? null);
    setCancelingFlightPlanId(null);
    setPendingFlightPlanCancellation(null);
    setFlightPlanConfirmation(null);
  }, []);

  const loadFlightPlansForAirport = useCallback((airport) => {
    if (!airport || (!isSimulacionPeriodo && !isOperacionesDiaADia)) return () => {};
    let cancelled = false;

    setAirportFlightPlansStatus("loading");
    listFlightPlans(airport.iata ?? airport.idAeropuerto)
      .then((data) => {
        if (cancelled) return;
        setAirportFlightPlans((Array.isArray(data) ? data : []).sort((a, b) => {
          const toUtcMin = (fp) => {
            const dt = String(fp?.depTime ?? "");
            const parts = dt.split(":");
            if (parts.length < 2) return 0;
            const localMin = parseInt(parts[0]) * 60 + parseInt(parts[1]);
            const gmt = Number(fp?.gmtOrigin ?? 0);
            return ((localMin - gmt * 60) + 1440) % 1440;
          };
          return toUtcMin(a) - toUtcMin(b);
        }));
        setAirportFlightPlansStatus("ready");
      })
      .catch(() => {
        if (cancelled) return;
        setAirportFlightPlans([]);
        setAirportFlightPlansStatus("error");
      });

    return () => {
      cancelled = true;
    };
  }, [isOperacionesDiaADia, isSimulacionPeriodo]);

  useEffect(() => {
    if (!flightPlansModalAirport || (!isSimulacionPeriodo && !isOperacionesDiaADia)) return;
    return loadFlightPlansForAirport(flightPlansModalAirport);
  }, [flightPlansModalAirport, isOperacionesDiaADia, isSimulacionPeriodo, loadFlightPlansForAirport]);

  useEffect(() => {
    if (!pendingFlightPlanCancellation || !periodStatusMessage?.estado) return;

    if (periodStatusMessage.estado === "VUELO_PROGRAMADO_CANCELADO") {
      toast.push({
        type: "warning",
        title: "Vuelo cancelado",
        message: pendingFlightPlanCancellation.toastMessage,
      });
      if (pendingFlightPlanCancellation.origin) {
        setCancellationNotice({
          airportCode: pendingFlightPlanCancellation.origin,
          flightId: pendingFlightPlanCancellation.idVueloProgramado,
          message: pendingFlightPlanCancellation.toastMessage,
          ts: Date.now(),
        });
      }
      // Re-fetch del snapshot para traer el vuelo recién cancelado a la lista.
      // El stream del tick no basta: la instancia cancelada puede tener un id
      // que el frontend no cargó en metadata. El snapshot trae hot+cold con
      // estado, así que mezclamos los CANCELADO (no se dibujan en el mapa, que
      // solo muestra EN_PROGRESO).
      if (sessionId && !USE_MOCK) {
        obtenerSnapshotSimulacionPeriodo(sessionId)
          .then((snap) => {
            const cancelados = (snap?.vuelosInstancia ?? [])
              .map(adaptFlightInstance)
              .filter((f) => normalizeFlightStatus(f.status) === "CANCELADO");
            if (cancelados.length === 0) return;
            setSimulationPanelData?.((prev) => {
              const flights = new Map(prev?.flights ?? new Map());
              for (const f of cancelados) {
                const id = f.idVueloInstancia ?? f.id;
                flights.set(id, { ...flights.get(id), ...f, ticksAusente: 0 });
              }
              return { ...prev, flights };
            });
          })
          .catch(() => {});
      }
      closeFlightPlansModal();
      return;
    }

    if (periodStatusMessage.estado === "ERROR_CANCELACION_VUELO_PROGRAMADO") {
      toast.push({
        type: "error",
        title: "No se pudo cancelar el vuelo",
        message: periodStatusMessage.mensaje ?? "La cancelacion fue rechazada por el backend.",
      });
      setPendingFlightPlanCancellation(null);
      setCancelingFlightPlanId(null);
      setFlightPlanConfirmation(null);
    }
  }, [closeFlightPlansModal, pendingFlightPlanCancellation, periodStatusMessage, toast, sessionId, setCancellationNotice, setSimulationPanelData]);

  const flightsFetch = useFetch(() => (isSimulator ? Promise.resolve([]) : listFlights()), [isSimulator]);
  const ordersFetch = useFetch(() => (isSimulator ? Promise.resolve([]) : listOrders()), [isSimulator]);
  const airportsFetch = useFetch(() => (isSimulator ? Promise.resolve([]) : listAirports()), [isSimulator]);
  const maletasFetch = useFetch(() => (isSimulator ? Promise.resolve([]) : listMaletas()), [isSimulator]);
  const rutasFetch = useFetch(() => (isSimulator ? Promise.resolve([]) : listRutas()), [isSimulator]);

  const flightsData = useMemo(
    () => [...(simulationLoaded ? simulationPanelData?.flights ?? new Map() : new Map()).values()],
    [simulationPanelData?.flights, simulationLoaded]
  );
  const bagsData = useMemo(
    () => [...(simulationLoaded ? simulationPanelData?.bags ?? new Map() : new Map()).values()],
    [simulationPanelData?.bags, simulationLoaded]
  );
  const routesData = useMemo(
    () => [...(simulationLoaded ? simulationPanelData?.routes ?? new Map() : new Map()).values()],
    [simulationPanelData?.routes, simulationLoaded]
  );
  const ordersData = useMemo(
    () => [...(simulationLoaded ? simulationPanelData?.orders ?? new Map() : new Map()).values()],
    [simulationPanelData?.orders, simulationLoaded]
  );
  const airportsData = useMemo(
    () => simulationLoaded ? (simulationPanelData?.airports ?? []) : [],
    [simulationPanelData?.airports, simulationLoaded]
  );

  const flights = isSimulator ? createStaticSource(flightsData) : flightsFetch;
  const airports = isSimulator ? createStaticSource(airportsData) : airportsFetch;
  const orders = isSimulator ? createStaticSource(ordersData) : ordersFetch;
  const maletas = isSimulator ? createStaticSource(bagsData) : maletasFetch;
  const rutas = isSimulator ? createStaticSource(routesData) : rutasFetch;

  const activeSource = {
    flights,
    orders,
    routes: rutas,
    bags: maletas,
    airports,
  }[activeTab];

  const filterByText = (rows, fields) => {
    const q = query.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((r) => fields.some((f) => String(r[f] ?? "").toLowerCase().includes(q)));
  };

  const FILTER_STATUSES = new Set(["PROGRAMADO", "CONFIRMADO", "EN_PROGRESO", "CANCELADO"]);

  // IATA -> ciudad, para poder buscar/filtrar UT por "almacen, ciudad o aeropuerto".
  const cityByIata = useMemo(() => {
    const map = new Map();
    for (const a of airports.data ?? []) {
      const code = a?.iata ?? a?.idAeropuerto;
      if (code) map.set(code, a.city ?? a.ciudad ?? "");
    }
    return map;
  }, [airports.data]);

  const continentByIata = useMemo(() => {
    const map = new Map();
    for (const a of airports.data ?? []) {
      const code = a?.iata ?? a?.idAeropuerto;
      if (code) map.set(code, a.continent ?? a.continente ?? "");
    }
    return map;
  }, [airports.data]);

  const airportCoords = useMemo(() => {
    const map = new Map();
    for (const a of airports.data ?? []) {
      const code = a?.iata ?? a?.idAeropuerto;
      if (code && Number.isFinite(a.lat) && Number.isFinite(a.lng)) map.set(code, { lat: a.lat, lng: a.lng });
    }
    return map;
  }, [airports.data]);

  const centroideDe = useCallback((codes) => {
    let sx = 0, sy = 0, n = 0;
    for (const c of codes) {
      const p = airportCoords.get(c);
      if (p) { sx += p.lng; sy += p.lat; n++; }
    }
    return n > 0 ? { lng: sx / n, lat: sy / n } : null;
  }, [airportCoords]);

  const flightOriginOptions = useMemo(() => {
    const set = new Set();
    for (const f of flights.data ?? []) if (f?.origin) set.add(f.origin);
    return [...set].sort();
  }, [flights.data]);

  const flightDestOptions = useMemo(() => {
    const set = new Set();
    for (const f of flights.data ?? []) if (f?.dest) set.add(f.dest);
    return [...set].sort();
  }, [flights.data]);

  const visibleFlights = useMemo(() => {
    const sourceFlights = Array.isArray(flights.data) ? flights.data : [];
    const q = query.trim().toLowerCase();
    const pattern = flightCodePattern.trim().toLowerCase();

    // Alcance por estado.
    let filtered;
    if (flightStatusFilter === "ALL") {
      filtered = sourceFlights;
    } else if (flightStatusFilter === "DEFAULT") {
      filtered = sourceFlights.filter((f) => FILTER_STATUSES.has(normalizeFlightStatus(f?.status)));
    } else {
      filtered = sourceFlights.filter((f) => normalizeFlightStatus(f?.status) === flightStatusFilter);
    }

    // Filtrar por origen / destino (req 9 / 10).
    if (flightOriginFilter !== "ALL") filtered = filtered.filter((f) => f.origin === flightOriginFilter);
    if (flightDestFilter !== "ALL") filtered = filtered.filter((f) => f.dest === flightDestFilter);

    // Filtrar por semaforo de UT (req 11).
    if (flightSemaforo !== "ALL") filtered = filtered.filter((f) => semaforoState(f.used, f.capacity) === flightSemaforo);

    // Filtrar por patron de codigo de UT (req 8).
    if (pattern) filtered = filtered.filter((f) => String(f.id ?? "").toLowerCase().includes(pattern));

    // Buscar por codigo / tramo / origen / destino, por IATA y por ciudad (req 5 / 6 / 7).
    if (q) {
      filtered = filtered.filter((f) => {
        const oCity = cityByIata.get(f.origin) ?? "";
        const dCity = cityByIata.get(f.dest) ?? "";
        const haystack = [
          f.id, f.origin, f.dest, oCity, dCity,
          `${f.origin}-${f.dest}`, `${f.origin} ${f.dest}`,
        ];
        return haystack.some((v) => String(v ?? "").toLowerCase().includes(q));
      });
    }

    return sortFlights(filtered, flightSortKey, flightSortDir);
  }, [
    flights.data, flightStatusFilter, flightOriginFilter, flightDestFilter,
    flightCodePattern, flightSemaforo, query, cityByIata, flightSortKey, flightSortDir,
  ]);

  const markFlightAsCanceled = (flightId) => {
    setCancelledFlightIds?.((previous) => {
      const next = new Set(previous);
      next.add(flightId);
      return next;
    });
    setSimulationPanelData?.((previous) => {
      const updated = new Map(previous?.flights ?? new Map());
      const flight = updated.get(flightId);
      if (flight) {
        updated.set(flightId, { ...flight, status: "CANCELADO", used: 0 });
      }
      return { ...previous, flights: updated };
    });
  };

  const handleCancelFlight = async (flight) => {
    const flightId = flight?.idVueloInstancia ?? flight?.id;
    if (!flightId) return;

    setCancelingFlightId(flightId);
    try {
      if (isSimulator) {
        markFlightAsCanceled(flightId);
        if (sessionId) {
          publish("/app/simulacion/periodo/cancelar-vuelo", {
            sessionId,
            idVueloInstancia: flightId,
          });
        }
      } else {
        await cancelFlight(flightId);
        await flightsFetch.refetch();
      }
      toast.push({
        type: "warning",
        title: "Vuelo cancelado",
        message: flightId,
      });
      if (flight?.origin) {
        setCancellationNotice({
          airportCode: flight.origin,
          flightId,
          message: `${flightId} cancelado`,
          ts: Date.now(),
        });
      }
    } catch (err) {
      toast.push({
        type: "error",
        title: "No se pudo cancelar el vuelo",
        message: err.message,
      });
    } finally {
      setCancelingFlightId(null);
    }
  };

  const executeFlightPlanCancellation = async (flightPlan, timing) => {
    const flightPlanId = flightPlan?.idVueloProgramado ?? flightPlan?.id;
    if (!flightPlanId) return;
    setCancelingFlightPlanId(flightPlanId);

    try {
      if (isOperacionesDiaADia) {
        await cancelarVueloProgramadoOperacionesDiaADia(flightPlanId);
        const [rutasData, maletasData, vuelosData] = await Promise.all([
          obtenerRutasOperacionesDiaADia().catch(() => []),
          obtenerMaletasOperacionesDiaADia().catch(() => []),
          obtenerVuelosOperacionesDiaADia().catch(() => []),
        ]);
        setSimulationPanelData?.((previous) => {
          const bagOrigen = new Map();
          const bagDestino = new Map();
          for (const r of rutasData) {
            const first = r?.vuelos?.[0];
            const last = r?.vuelos?.[r.vuelos.length - 1];
            if (r.idMaleta) {
              if (first?.aeropuertoOrigen) bagOrigen.set(r.idMaleta, first.aeropuertoOrigen);
              if (last?.aeropuertoDestino) bagDestino.set(r.idMaleta, last.aeropuertoDestino);
            }
          }
          const routes = new Map();
          for (const r of rutasData) routes.set(r.idRuta, { ...r, ticksAusente: 0 });
          const bags = new Map(previous?.bags ?? new Map());
          for (const m of maletasData) {
            const existing = bags.get(m.idMaleta);
            bags.set(m.idMaleta, {
              ...m,
              origen: bagOrigen.get(m.idMaleta) ?? existing?.origen ?? null,
              destino: bagDestino.get(m.idMaleta) ?? existing?.destino ?? null,
              ticksAusente: 0,
            });
          }
          // Mezclar la lista de vuelos re-obtenida (ya incluye CANCELADO) para que
          // el vuelo cancelado aparezca de inmediato sin esperar un tick ni recargar.
          const flights = new Map(previous?.flights ?? new Map());
          for (const raw of (vuelosData ?? [])) {
            const f = adaptFlightInstance(raw);
            const id = f.idVueloInstancia ?? f.id;
            flights.set(id, { ...flights.get(id), ...f, ticksAusente: 0 });
          }
          return { ...previous, routes, bags, flights };
        });
        toast.push({
          type: "warning",
          title: "Vuelo cancelado",
          message: `${flightPlan.origin} -> ${flightPlan.dest} · ${timing.effectiveDateLabel}`,
        });
        if (flightPlan.origin) {
          setCancellationNotice({
            airportCode: flightPlan.origin,
            flightId: flightPlanId,
            message: `${flightPlan.origin} -> ${flightPlan.dest} - ${timing.effectiveDateLabel}`,
            ts: Date.now(),
          });
        }
        closeFlightPlansModal();
        return;
      }

      if (USE_MOCK || !sessionId) {
        toast.push({
          type: "warning",
          title: "Vuelo cancelado",
          message: `${flightPlan.origin} -> ${flightPlan.dest} · ${timing.effectiveDateLabel}`,
        });
        if (flightPlan.origin) {
          setCancellationNotice({
            airportCode: flightPlan.origin,
            flightId: flightPlanId,
            message: `${flightPlan.origin} -> ${flightPlan.dest} - ${timing.effectiveDateLabel}`,
            ts: Date.now(),
          });
        }
        closeFlightPlansModal();
        return;
      }

      setPendingFlightPlanCancellation({
        idVueloProgramado: flightPlanId,
        origin: flightPlan.origin,
        dest: flightPlan.dest,
        toastMessage: `${flightPlan.origin} -> ${flightPlan.dest} · ${timing.effectiveDateLabel}`,
      });
      await publish("/app/simulacion/periodo/cancelar-vuelo-programado", {
        sessionId,
        idVueloProgramado: flightPlanId,
      });
    } catch (err) {
      setPendingFlightPlanCancellation(null);
      setCancelingFlightPlanId(null);
      toast.push({
        type: "error",
        title: "No se pudo cancelar el vuelo",
        message: err.message,
      });
    }
  };

  const handleCancelFlightPlan = (flightPlan, timing) => {
    const flightPlanId = flightPlan?.idVueloProgramado ?? flightPlan?.id;
    if (!flightPlanId) return;
    if (!timing?.resolved) {
      toast.push({
        type: "warning",
        title: "Hora simulada no disponible",
        message: "Espera a que la simulacion tenga una hora simulada para resolver si la cancelacion aplica hoy o manana.",
      });
      return;
    }

    setFlightPlanConfirmation({ flightPlan, timing });
  };

  const confirmFlightPlanCancellation = async () => {
    if (!flightPlanConfirmation) return;
    const { flightPlan, timing } = flightPlanConfirmation;
    setFlightPlanConfirmation(null);
    await executeFlightPlanCancellation(flightPlan, timing);
  };

  const simTime = simulationPanelData?.simTime;
  const flightPlanTimingRef = isOperacionesDiaADia ? new Date().toISOString() : simTime;

  const maletasVisibles = useMemo(() => {
    const source = maletas.data ?? [];
    if (!simTime) return source;
    return source.filter(b => !b.fechaRegistro || b.fechaRegistro <= simTime);
  }, [maletas.data, simTime]);

  const idMaletasVisibles = useMemo(() => {
    const source = maletas.data ?? [];
    const visibles = simTime
      ? source.filter(b => !b.fechaRegistro || b.fechaRegistro <= simTime)
      : source;
    return new Set(visibles.map(b => b.idMaleta));
  }, [maletas.data, simTime]);

  const pedidosActivos = useMemo(() => {
    const source = maletas.data ?? [];
    const visibles = simTime
      ? source.filter(b => !b.fechaRegistro || b.fechaRegistro <= simTime)
      : source;
    return new Set(visibles.map(b => b.idPedido));
  }, [maletas.data, simTime]);

  const ordersFiltered = useMemo(() => {
    const source = orders.data ?? [];
    const filtrados = simTime ? source.filter(o => pedidosActivos.has(o.id ?? o.idPedido)) : source;
    return filterByText(filtrados, ["id", "idPedido", "clientId", "idCliente", "origin", "dest", "status", "estado"]);
  }, [orders.data, pedidosActivos, query, simTime]);

  // Por almacen: hora de la UT mas proxima en SALIR (despegue desde el) y en
  // LLEGAR (aterrizaje en el), considerando solo vuelos futuros y no cancelados.
  const nextFlightTimesByAirport = useMemo(() => {
    const nowMs = simTime ? parseUtcDateTime(simTime) : Number.NEGATIVE_INFINITY;
    const map = new Map();
    const ensure = (code) => {
      let entry = map.get(code);
      if (!entry) { entry = { dep: Infinity, arr: Infinity }; map.set(code, entry); }
      return entry;
    };
    for (const f of flights.data ?? []) {
      if (normalizeFlightStatus(f?.status) === "CANCELADO") continue;
      const depMs = parseUtcDateTime(f?.depTime);
      const arrMs = parseUtcDateTime(f?.arrTime);
      if (f?.origin && Number.isFinite(depMs) && depMs >= nowMs) {
        const e = ensure(f.origin);
        if (depMs < e.dep) e.dep = depMs;
      }
      if (f?.dest && Number.isFinite(arrMs) && arrMs >= nowMs) {
        const e = ensure(f.dest);
        if (arrMs < e.arr) e.arr = arrMs;
      }
    }
    return map;
  }, [flights.data, simTime]);

  const airportRegionOptions = useMemo(() => {
    const set = new Set();
    for (const a of airports.data ?? []) {
      const region = a?.continent ?? a?.continente;
      if (region) set.add(region);
    }
    return [...set].sort();
  }, [airports.data]);

  const airportsFiltered = useMemo(() => {
    let rows = airports.data ?? [];

    // Filtrar por region/continental (req 25).
    if (airportRegionFilter !== "ALL") {
      rows = rows.filter((a) => (a?.continent ?? a?.continente) === airportRegionFilter);
    }
    // Filtrar por semaforo de almacen (req 10).
    if (airportSemaforo !== "ALL") {
      rows = rows.filter((a) => semaforoState(a?.used, a?.capacity) === airportSemaforo);
    }
    // Filtrar por patron de codigo de almacen (req 24).
    const pattern = airportCodePattern.trim().toLowerCase();
    if (pattern) {
      rows = rows.filter((a) => String(a?.iata ?? a?.idAeropuerto ?? "").toLowerCase().includes(pattern));
    }
    // Busqueda libre (codigo, ciudad, continente).
    rows = filterByText(rows, ["iata", "city", "continent"]);

    // Ordenar (req 26/27/28).
    return sortAirports(rows, airportSortKey, airportSortDir, nextFlightTimesByAirport);
  }, [
    airports.data, query, airportRegionFilter, airportCodePattern, airportSemaforo,
    airportSortKey, airportSortDir, nextFlightTimesByAirport,
  ]);

  // Envios del panel (planificados / en vuelos / entregados 4h). Se pide al
  // back al entrar a la pestana Pedidos y con el boton refrescar; NO en cada
  // tick, para no recargar (la data se mantiene hasta que el usuario refresca).
  const fetchEnvios = useCallback(() => {
    if (USE_MOCK || !sessionId) {
      setEnviosData({ planificados: [], enVuelos: [], entregados: [] });
      setEnviosStatus("ready");
      return Promise.resolve();
    }
    setEnviosStatus("loading");

    if (isOperacionesDiaADia) {
      return obtenerEnviosOperacionesDiaADia()
        .then((data) => {
          setEnviosData({
            planificados: data?.planificados ?? [],
            enVuelos: data?.enVuelos ?? [],
            entregados: data?.entregadosUltimas4h ?? [],
          });
          setEnviosStatus("ready");
        })
        .catch(() => {
          setEnviosData({ planificados: [], enVuelos: [], entregados: [] });
          setEnviosStatus("error");
        });
    }

    return obtenerEnviosPanel(sessionId)
      .then((data) => {
        setEnviosData({
          planificados: data?.planificados ?? [],
          enVuelos: data?.enVuelos ?? [],
          entregados: data?.entregadosUltimas4h ?? [],
        });
        setEnviosStatus("ready");
      })
      .catch(() => {
        setEnviosData({ planificados: [], enVuelos: [], entregados: [] });
        setEnviosStatus("error");
      });
  }, [sessionId, isOperacionesDiaADia, isSimulacionPeriodo]);

  useEffect(() => {
    if ((isSimulacionPeriodo || isOperacionesDiaADia) && activeTab === "orders") fetchEnvios();
  }, [isSimulacionPeriodo, isOperacionesDiaADia, activeTab, fetchEnvios]);

  const bagAirportOptions = useMemo(() => {
    const origins = new Set();
    const dests = new Set();
    for (const b of maletas.data ?? []) {
      if (b.origen) origins.add(b.origen);
      if (b.destino) dests.add(b.destino);
    }
    return { origins: [...origins].sort(), dests: [...dests].sort() };
  }, [maletas.data]);

  const routeAirportOptions = useMemo(() => {
    const origins = new Set();
    const dests = new Set();
    for (const r of rutas.data ?? []) {
      const vuelos = r.vuelos ?? [];
      if (vuelos[0]?.aeropuertoOrigen) origins.add(vuelos[0].aeropuertoOrigen);
      if (vuelos[vuelos.length - 1]?.aeropuertoDestino) dests.add(vuelos[vuelos.length - 1].aeropuertoDestino);
    }
    return { origins: [...origins].sort(), dests: [...dests].sort() };
  }, [rutas.data]);

  const enviosAirportOptions = useMemo(() => {
    const origins = new Set();
    const dests = new Set();
    for (const e of [...enviosData.planificados, ...enviosData.enVuelos, ...(enviosData.entregados ?? [])]) {
      for (const a of e.origenesRuta ?? []) origins.add(a);
      for (const a of e.destinosRuta ?? []) dests.add(a);
    }
    return { origins: [...origins].sort(), dests: [...dests].sort() };
  }, [enviosData]);

  // Filtra por origen/destino considerando TODA la ruta (tramos), no solo los
  // extremos del pedido (req 32/33), mas la busqueda libre por IATA/ciudad/UT.
  const filterEnvios = useCallback((list) => {
    let rows = list ?? [];
    if (envioOriginFilter !== "ALL") rows = rows.filter((e) => (e.origenesRuta ?? []).includes(envioOriginFilter));
    if (envioDestFilter !== "ALL") rows = rows.filter((e) => (e.destinosRuta ?? []).includes(envioDestFilter));
    const q = query.trim().toLowerCase();
    if (q) {
      rows = rows.filter((e) => {
        const airports = [...(e.origenesRuta ?? []), ...(e.destinosRuta ?? [])];
        const cities = airports.map((c) => cityByIata.get(c) ?? "");
        const hay = [e.id, e.origin, e.dest, ...(e.uts ?? []), ...airports, ...cities];
        return hay.some((v) => String(v ?? "").toLowerCase().includes(q));
      });
    }
    return sortOrders(rows, orderSortKey, orderSortDir);
  }, [envioOriginFilter, envioDestFilter, query, cityByIata, orderSortKey, orderSortDir]);

  const enviosFilteredForMap = useMemo(() => ([
    ...filterEnvios(enviosData.planificados),
    ...filterEnvios(enviosData.enVuelos),
    ...filterEnvios(enviosData.entregados ?? []),
  ]), [enviosData, filterEnvios]);

  const maletasFiltered = useMemo(() => {
    let source = maletasVisibles;
    if (bagOriginFilter !== "ALL") {
      source = source.filter(b => b.origen === bagOriginFilter);
    }
    if (bagDestFilter !== "ALL") {
      source = source.filter(b => b.destino === bagDestFilter);
    }
    if (bagStatusFilter !== "ALL") {
      source = source.filter(b => b.estado === bagStatusFilter);
    }
    let result = filterByText(source, ["idMaleta", "idPedido", "estado", "ubicacionActual"]);
    result = sortBags(result, bagSortKey, bagSortDir);
    return result;
  }, [maletasVisibles, query, bagOriginFilter, bagDestFilter, bagStatusFilter, bagSortKey, bagSortDir]);

  const rutasFiltered = useMemo(() => {
    let source = rutas.data ?? [];
    if (simTime) {
      source = source.filter(r => idMaletasVisibles.has(r.idMaleta));
    }
    if (routeOriginFilter !== "ALL") {
      source = source.filter(r => (r.vuelos ?? [])[0]?.aeropuertoOrigen === routeOriginFilter);
    }
    if (routeDestFilter !== "ALL") {
      source = source.filter(r => {
        const vuelos = r.vuelos ?? [];
        return vuelos[vuelos.length - 1]?.aeropuertoDestino === routeDestFilter;
      });
    }
    if (routeStatusFilter !== "ALL") {
      source = source.filter(r => r.estado === routeStatusFilter);
    }
    if (routePlazoFilter !== "ALL") {
      source = source.filter(r => r.plazoMaximoDias === Number(routePlazoFilter));
    }
    source = sortRoutes(filterByText(source, ["idRuta", "idMaleta", "estado"]), routeSortKey, routeSortDir);
    return source;
  }, [rutas.data, idMaletasVisibles, query, simTime,
      routeOriginFilter, routeDestFilter, routeStatusFilter, routePlazoFilter, routeSortKey, routeSortDir]);

  // Pide el manifiesto de una UT al back (envios/maletas que traslada). Se
  // resuelve alli porque el enlace UT->carga solo existe en las rutas globales
  // de la sesion, no en el subconjunto por-ventana que se carga en el panel.
  const loadFlightManifest = useCallback(
    (flightKey) => {
      if (USE_MOCK || !sessionId || !flightKey) {
        return Promise.resolve({ maletas: [], pedidos: [] });
      }
      return isOperacionesDiaADia
        ? obtenerManifiestoVueloOperacionesDiaADia(flightKey)
        : obtenerManifiestoVuelo(sessionId, flightKey);
    },
    [sessionId, isOperacionesDiaADia]
  );

  // Publicamos el loader al contexto para que el mapa pueda usarlo al hacer
  // click en un avion (popup con pedidos/maletas).
  useEffect(() => {
    // useState con setter funcional: envolver para evitar que React lo trate como updater.
    setFlightManifestLoader(() => loadFlightManifest);
    return () => setFlightManifestLoader(() => null);
  }, [loadFlightManifest, setFlightManifestLoader]);

  // Contenido de un almacen (envios/maletas presentes), pedido al back al
  // expandir el aeropuerto. El enlace almacen->contenido vive en el estado
  // global de la sesion, no en el subconjunto por-ventana del panel.
  const loadAirportContenido = useCallback(
    (idAeropuerto) => {
      if (USE_MOCK || !sessionId || !idAeropuerto) {
        return Promise.resolve({
          pedidosDestinoFinal: [], pedidosEnTransito: [],
          maletasDestinoFinal: [], maletasEnTransito: [],
          totalMaletasDestinoFinal: 0, totalMaletasEnTransito: 0,
          pedidosEntran: [], pedidosSalen: [],
          maletasEntran: [], maletasSalen: [],
          totalMaletasEntran: 0, totalMaletasSalen: 0,
        });
      }
      if (isOperacionesDiaADia) {
        return apiGet(`/operations/${sessionId}/almacen/${encodeURIComponent(idAeropuerto)}/contenido`)
          .catch(() => ({
            pedidosDestinoFinal: [], pedidosEnTransito: [],
            maletasDestinoFinal: [], maletasEnTransito: [],
            totalMaletasDestinoFinal: 0, totalMaletasEnTransito: 0,
            pedidosEntran: [], pedidosSalen: [],
            maletasEntran: [], maletasSalen: [],
            totalMaletasEntran: 0, totalMaletasSalen: 0,
          }));
      }
      return obtenerContenidoAlmacen(sessionId, idAeropuerto);
    },
    [sessionId]
  );

  // Resalta en el mapa la ruta de una maleta por su ID (req 1/2).
  const showMaletaRoute = useCallback(
    async (idMaleta) => {
      if (!idMaleta || !sessionId || USE_MOCK) return;
      try {
        const ruta = isOperacionesDiaADia
          ? await obtenerRutaMaletaOperacionesDiaADia(idMaleta)
          : await obtenerRutaMaleta(sessionId, idMaleta);
        const escalas = (ruta?.vuelos ?? []).map(toEscala);
        if (escalas.length === 0) {
          toast.push({ type: "warning", title: "Sin ruta", message: `La maleta ${idMaleta} no tiene ruta asignada.` });
          return;
        }
        setMapHighlight({
          kind: "maleta",
          label: `Maleta ${idMaleta}`,
          rutas: [{ idMaleta, escalas }],
          legs: escalas.map((e) => ({ origen: e.origen, destino: e.destino, codigo: e.codigo })),
        });
        const codes = new Set();
        escalas.forEach((e) => { if (e.origen) codes.add(e.origen); if (e.destino) codes.add(e.destino); });
        const c = centroideDe(codes);
        if (c) setMapFocus({ ...c, zoom: 3.5, ts: Date.now() });
      } catch (err) {
        if (err?.status === 404) {
          toast.push({ type: "warning", title: "Sin ruta", message: `La maleta ${idMaleta} no tiene ruta asignada.` });
          return;
        }
        toast.push({ type: "error", title: "No se pudo cargar la ruta", message: err.message });
      }
    },
    [sessionId, isOperacionesDiaADia, setMapHighlight, setMapFocus, centroideDe, toast]
  );

  // Resalta en el mapa las rutas de un envio por su ID (req 3/4).
  const showEnvioRoutes = useCallback(
    async (idPedido) => {
      if (!idPedido || !sessionId || USE_MOCK) return;
      try {
        const rutas = isOperacionesDiaADia
          ? await obtenerRutasEnvioOperacionesDiaADia(idPedido)
          : await obtenerRutasEnvio(sessionId, idPedido);
        const grupos = agruparRutasEnvio(rutas);
        const legSeen = new Set();
        const legs = [];
        for (const g of grupos) {
          for (const e of g.escalas) {
            const lk = `${e.origen}-${e.destino}`;
            if (!legSeen.has(lk)) {
              legSeen.add(lk);
              legs.push({ origen: e.origen, destino: e.destino, codigo: e.codigo });
            }
          }
        }
        if (legs.length === 0) {
          toast.push({ type: "warning", title: "Sin rutas", message: `El envio ${idPedido} no tiene rutas asignadas.` });
          return;
        }
        setMapHighlight({
          kind: "envio",
          label: `Envio ${idPedido} (${grupos.length} ruta${grupos.length !== 1 ? "s" : ""})`,
          rutas: grupos,
          legs,
        });
        const codes = new Set();
        legs.forEach((l) => { if (l.origen) codes.add(l.origen); if (l.destino) codes.add(l.destino); });
        const c = centroideDe(codes);
        if (c) setMapFocus({ ...c, zoom: 3, ts: Date.now() });
      } catch (err) {
        toast.push({ type: "error", title: "No se pudieron cargar las rutas", message: err.message });
      }
    },
    [sessionId, isOperacionesDiaADia, setMapHighlight, setMapFocus, centroideDe, toast]
  );

  // Rutas agrupadas (con sus maletas) de un envio, para el detalle de la fila al
  // expandirla. Reusa el mismo endpoint que "Ver rutas".
  const loadEnvioRutas = useCallback(
    async (idPedido) => {
      if (!idPedido || !sessionId || USE_MOCK) return [];
      const rutas = isOperacionesDiaADia
        ? await obtenerRutasEnvioOperacionesDiaADia(idPedido)
        : await obtenerRutasEnvio(sessionId, idPedido);
      return agruparRutasEnvio(rutas);
    },
    [sessionId, isOperacionesDiaADia]
  );

  // Enfocar un almacen (req 5) o una UT (req 7) en el mapa desde el panel.
  const focusAirportOnMap = useCallback(
    (iata) => {
      if (!iata) return;
      if (selected?.kind === "airport" && selected.id === iata) {
        setSelected(null);
        setMapFocus(null);
        return;
      }
      setSelected({ kind: "airport", id: iata });
      const c = airportCoords.get(iata);
      if (c) setMapFocus({ lng: c.lng, lat: c.lat, zoom: 5, ts: Date.now() });
    },
    [airportCoords, selected, setSelected, setMapFocus]
  );

  const focusFlightOnMap = useCallback(
    (flight) => {
      if (!flight) return;
      const id = flight.idVueloInstancia ?? flight.id;
      setSelected({ kind: "flight", id });
      const o = airportCoords.get(flight.origin);
      const d = airportCoords.get(flight.dest);
      if (o && d) setMapFocus({ lng: (o.lng + d.lng) / 2, lat: (o.lat + d.lat) / 2, zoom: 3.5, ts: Date.now() });
      else if (o) setMapFocus({ lng: o.lng, lat: o.lat, zoom: 4, ts: Date.now() });
    },
    [airportCoords, setSelected, setMapFocus]
  );

  // Seleccionar un pedido y enfocarlo en el mapa:
  //  - "flight" (en vuelos): resalta su(s) ruta(s) y enfoca la cámara (avión + ruta).
  //  - "warehouse" (planificado): enfoca el almacén de origen donde esperan las maletas.
  const focusEnvio = useCallback(
    (envio, category) => {
      if (!envio?.id) return;
      setSelectedEnvioId(envio.id);
      if (category === "flight") {
        showEnvioRoutes(envio.id);                 // avión + ruta
      } else if (category === "delivered" && envio.dest) {
        focusAirportOnMap(envio.dest);             // ya entregado -> almacén destino
      } else if (envio.origin) {
        focusAirportOnMap(envio.origin);           // planificado -> almacén de origen
      } else {
        showEnvioRoutes(envio.id);
      }
    },
    [showEnvioRoutes, focusAirportOnMap]
  );

  const deselectEnvio = useCallback(() => {
    setSelectedEnvioId(null);
    setMapHighlight(null);
    setSelected(null);
  }, [setMapHighlight, setSelected]);

  const normalizedQuery = query.trim().toLowerCase();
  const normalizedFlightCodePattern = flightCodePattern.trim().toLowerCase();
  const normalizedAirportCodePattern = airportCodePattern.trim().toLowerCase();

  // Panel -> mapa: refleja el filtro de almacenes (semaforo y otros) (req 10/12).
  useEffect(() => {
    if (activeTab !== "airports") return;
    const active = airportSemaforo !== "ALL" || airportRegionFilter !== "ALL"
      || airportCodePattern.trim() !== "" || query.trim() !== "";
    if (active) {
      const set = new Set(airportsFiltered.map((a) => a.iata ?? a.idAeropuerto));
      const fitKey = `airports:${airportSemaforo}|${airportRegionFilter}|${normalizedAirportCodePattern}|${normalizedQuery}`;
      setMapDim((prev) => ({ ...prev, airports: set, flights: null, fitKey }));
    } else {
      setMapDim((prev) => (prev.airports || prev.flights || prev.fitKey ? { ...prev, airports: null, flights: null, fitKey: null } : prev));
    }
  }, [activeTab, airportsFiltered, airportSemaforo, airportRegionFilter, airportCodePattern, normalizedAirportCodePattern, normalizedQuery, query, setMapDim]);

  // Panel -> mapa: refleja el filtro de UT (semaforo y otros) (req 11/13).
  useEffect(() => {
    if (activeTab !== "flights") return;
    const active = flightSemaforo !== "ALL" || flightOriginFilter !== "ALL" || flightDestFilter !== "ALL"
      || flightCodePattern.trim() !== "" || query.trim() !== "" || flightStatusFilter !== "DEFAULT";
    if (active) {
      const set = new Set(visibleFlights.map((f) => f.idVueloInstancia ?? f.id));
      const fitKey = `flights:${flightStatusFilter}|${flightSemaforo}|${flightOriginFilter}|${flightDestFilter}|${normalizedFlightCodePattern}|${normalizedQuery}`;
      setMapDim((prev) => ({ ...prev, airports: null, flights: set, fitKey }));
    } else {
      setMapDim((prev) => (prev.airports || prev.flights || prev.fitKey ? { ...prev, airports: null, flights: null, fitKey: null } : prev));
    }
  }, [activeTab, visibleFlights, flightSemaforo, flightOriginFilter, flightDestFilter, flightCodePattern, normalizedFlightCodePattern, normalizedQuery, query, flightStatusFilter, setMapDim]);

  // Panel -> mapa: filtros de envios/pedidos. Se limita por aeropuertos de ruta
  // porque las UT del panel son codigos de vuelo, no siempre ids de instancia.
  useEffect(() => {
    if (activeTab !== "orders") return;
    const active = envioOriginFilter !== "ALL" || envioDestFilter !== "ALL" || query.trim() !== "";
    if (active) {
      const codes = new Set();
      for (const e of enviosFilteredForMap) {
        if (e.origin) codes.add(e.origin);
        if (e.dest) codes.add(e.dest);
        for (const c of e.origenesRuta ?? []) codes.add(c);
        for (const c of e.destinosRuta ?? []) codes.add(c);
      }
      const fitKey = `orders:${envioOriginFilter}|${envioDestFilter}|${normalizedQuery}`;
      setMapDim((prev) => ({ ...prev, airports: codes, flights: null, fitKey }));
    } else {
      setMapDim((prev) => (prev.airports || prev.flights || prev.fitKey ? { ...prev, airports: null, flights: null, fitKey: null } : prev));
    }
  }, [activeTab, enviosFilteredForMap, envioOriginFilter, envioDestFilter, normalizedQuery, query, setMapDim]);

  useEffect(() => {
    if (activeTab === "airports" || activeTab === "flights" || activeTab === "orders") return;
    setMapDim((prev) => (prev.airports || prev.flights || prev.fitKey ? { ...prev, airports: null, flights: null, fitKey: null } : prev));
  }, [activeTab, setMapDim]);

  // Mapa -> panel (req 6/8): al hacer click en un almacen (aeropuerto) o en una
  // unidad de transporte (vuelo) en el mapa, saltar a su pestaña y dejar pedido
  // el desplazamiento hasta el item correspondiente.
  useEffect(() => {
    if (!panelFocus) return;
    const targetTab =
      panelFocus.kind === "airport" ? "airports" :
      panelFocus.kind === "flight" ? "flights" : null;
    if (!targetTab) return;
    if (activeTab !== targetTab) onTabChange(targetTab);
    setScrollTarget({ kind: panelFocus.kind, id: panelFocus.id, ts: panelFocus.ts });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [panelFocus]);

  // Ejecuta el desplazamiento una vez que la pestaña correcta esta activa y sus
  // filas estan disponibles. Reintenta por frames porque la lista virtualizada
  // (Virtuoso) puede montarse despues del cambio de pestaña.
  useEffect(() => {
    if (!scrollTarget) return undefined;
    const isAirport = scrollTarget.kind === "airport";
    const targetTab = isAirport ? "airports" : "flights";
    if (activeTab !== targetTab) return undefined;
    const rows = isAirport ? airportsFiltered : visibleFlights;
    const index = rows.findIndex((row) =>
      isAirport
        ? (row.iata ?? row.idAeropuerto) === scrollTarget.id
        : (row.idVueloInstancia ?? row.id) === scrollTarget.id
    );
    if (index < 0) return undefined;
    const virtuosoRef = isAirport ? airportsVirtuosoRef : flightsVirtuosoRef;
    let cancelled = false;
    let frame = 0;
    let tries = 0;
    const tryScroll = () => {
      if (cancelled) return;
      const inst = virtuosoRef.current;
      if (inst?.scrollToIndex) {
        inst.scrollToIndex({ index, align: "center", behavior: "smooth" });
        setScrollTarget(null);
        return;
      }
      if (tries++ < 30) frame = requestAnimationFrame(tryScroll);
    };
    frame = requestAnimationFrame(tryScroll);
    return () => { cancelled = true; cancelAnimationFrame(frame); };
  }, [scrollTarget, activeTab, visibleFlights, airportsFiltered]);

  const prevRoutesSizeRef = useRef(0);
  const prevBagsSizeRef = useRef(0);

  useEffect(() => {
    const routesSize = rutasFiltered.length;
    const bagsSize = maletasFiltered.length;
    if (routesSize < prevRoutesSizeRef.current && routesVirtuosoRef.current?.scrollToIndex) {
      const savedIndex = savedRouteScrollIndexRef.current;
      if (savedIndex < routesSize) {
        routesVirtuosoRef.current.scrollToIndex({ index: savedIndex, align: "start" });
      }
    }
    if (bagsSize < prevBagsSizeRef.current && bagsVirtuosoRef.current?.scrollToIndex) {
      const savedIndex = savedBagScrollIndexRef.current;
      if (savedIndex < bagsSize) {
        bagsVirtuosoRef.current.scrollToIndex({ index: savedIndex, align: "start" });
      }
    }
    prevRoutesSizeRef.current = routesSize;
    prevBagsSizeRef.current = bagsSize;
  }, [rutasFiltered, maletasFiltered]);

  const handleItemExpand = useCallback((entity) => {
    setSelected(entity);
  }, [setSelected]);

  const handleItemCollapse = useCallback(() => {
    setSelected(null);
  }, [setSelected]);

  const tabContent = {
    flights: (
      <TabBody
        {...flights}
        virtuosoRef={flightsVirtuosoRef}
        empty="Ejecuta la simulacion para cargar los vuelos del periodo."
        rows={visibleFlights}
        keyForRow={(f, index) => f.idVueloInstancia ?? f.id ?? index}
        renderItem={(f, index) => (
          <FlightItem
            key={f.idVueloInstancia ?? f.id ?? index}
            flight={f}
            onCancel={handleCancelFlight}
            canceling={cancelingFlightId === f.id}
            loadManifest={loadFlightManifest}
            onFocus={focusFlightOnMap}
            onFocusAirport={focusAirportOnMap}
            onDeselect={() => setSelected(null)}
            onExpand={(id) => handleItemExpand({ kind: "flight", id })}
            onCollapse={handleItemCollapse}
            isSelected={selected?.kind === "flight" && selected.id === (f.idVueloInstancia ?? f.id)}
            forceExpanded={selected?.kind === "flight" && selected.id === (f.idVueloInstancia ?? f.id)}
            cityByIata={cityByIata}
            continentByIata={continentByIata}
          />
        )}
      />
    ),
    orders: (
      enviosStatus === "loading" ? (
        <LoadingState label="Cargando envios..." />
      ) : enviosStatus === "error" ? (
        <ErrorState error={{ message: "No se pudieron cargar los envios." }} onRetry={fetchEnvios} />
      ) : (
        <div className="space-y-2">
          <EnvioSeccion
            title="Planificados (por transportar)"
            envios={filterEnvios(enviosData.planificados)}
            onShowRoute={showEnvioRoutes}
            onLoadRutas={loadEnvioRutas}
            category="warehouse"
            selectedEnvioId={selectedEnvioId}
            onFocus={focusEnvio}
            onDeselect={deselectEnvio}
            cityByIata={cityByIata}
            bags={simulationPanelData?.bags}
            defaultOpen
          />
          <EnvioSeccion
            title="En vuelos"
            envios={filterEnvios(enviosData.enVuelos)}
            onShowRoute={showEnvioRoutes}
            onLoadRutas={loadEnvioRutas}
            category="flight"
            selectedEnvioId={selectedEnvioId}
            onFocus={focusEnvio}
            onDeselect={deselectEnvio}
            cityByIata={cityByIata}
            bags={simulationPanelData?.bags}
            defaultOpen
          />
          <EnvioSeccion
            title="Entregados (ultimas 4h)"
            envios={filterEnvios(enviosData.entregados ?? [])}
            onShowRoute={showEnvioRoutes}
            onLoadRutas={loadEnvioRutas}
            category="delivered"
            selectedEnvioId={selectedEnvioId}
            onFocus={focusEnvio}
            onDeselect={deselectEnvio}
            cityByIata={cityByIata}
            bags={simulationPanelData?.bags}
          />
        </div>
      )
    ),
    routes: (
      <TabBody
        {...rutas}
        virtuosoRef={routesVirtuosoRef}
        onFirstItemIndexChange={(idx) => { savedRouteScrollIndexRef.current = idx; }}
        empty="Ejecuta la simulacion para cargar las rutas de la ventana activa."
        rows={rutasFiltered}
        keyForRow={(r, index) => r.idRuta ?? r.id ?? index}
        renderItem={(r, index) => <RouteItem key={r.idRuta ?? r.id ?? index} route={r} onShowRoute={showMaletaRoute} cityByIata={cityByIata} continentByIata={continentByIata} />}
      />
    ),
    bags: (
      <TabBody
        {...maletas}
        virtuosoRef={bagsVirtuosoRef}
        onFirstItemIndexChange={(idx) => { savedBagScrollIndexRef.current = idx; }}
        empty="Ejecuta la simulacion para cargar las maletas de la ventana activa."
        rows={maletasFiltered}
        keyForRow={(b, index) => b.idMaleta ?? b.id ?? index}
        renderItem={(b, index) => <BagItem key={b.idMaleta ?? b.id ?? index} bag={b} onShowRoute={showMaletaRoute} cityByIata={cityByIata} continentByIata={continentByIata} />}
      />
    ),
    airports: (
      <TabBody
        {...airports}
        virtuosoRef={airportsVirtuosoRef}
        empty="Ejecuta la simulacion para cargar los aeropuertos del periodo."
        rows={airportsFiltered}
        keyForRow={(a, index) => a.iata ?? a.idAeropuerto ?? index}
        renderItem={(a, index) => (
          <AirportItem
            key={a.iata ?? a.idAeropuerto ?? index}
            apt={a}
            loadContenido={loadAirportContenido}
            onFocus={focusAirportOnMap}
            onDeselect={() => setSelected(null)}
            onExpand={(id) => handleItemExpand({ kind: "airport", id })}
            onCollapse={handleItemCollapse}
            isSelected={selected?.kind === "airport" && selected.id === (a.iata ?? a.idAeropuerto)}
            forceExpanded={selected?.kind === "airport" && selected.id === (a.iata ?? a.idAeropuerto)}
            onShowFlightPlans={openFlightPlansModal}
            showFlightPlansAction={isSimulacionPeriodo || isOperacionesDiaADia}
          />
        )}
      />
    ),
  }[activeTab];

  const flightPlansModal = (
    <Modal
      open={Boolean(flightPlansModalAirport)}
      onClose={closeFlightPlansModal}
      title={`Vuelos programados de ${flightPlansModalAirport?.iata ?? flightPlansModalAirport?.idAeropuerto ?? ""}`}
      maxWidth="max-w-4xl"
    >
      <div className="flex max-h-[80vh] flex-col">
        <div className="border-b border-slate-800 px-6 py-5 pr-16">
          <h3 className="text-lg font-semibold text-slate-100">
            Vuelos programados de {flightPlansModalAirport?.iata ?? flightPlansModalAirport?.idAeropuerto ?? "--"}
          </h3>
          <p className="mt-2 text-sm leading-relaxed text-slate-400">
            Selecciona un vuelo programado para registrar su cancelacion. La interfaz te indicara si la cancelacion aplica al vuelo de hoy o al del dia siguiente segun la hora simulada actual.
          </p>
          <p className="mt-1 text-sm font-medium text-slate-300">
            Hora simulada: <span className="text-info">{(simTime ?? "").replace("T", " ").slice(0, 19)} UTC</span>
          </p>
        </div>

        <div className="overflow-y-auto px-6 py-5">
          {airportFlightPlansStatus === "loading" ? (
            <LoadingState label="Cargando vuelos programados..." />
          ) : airportFlightPlansStatus === "error" ? (
            <ErrorState
              error={{ message: "No se pudieron cargar los vuelos programados de este aeropuerto." }}
              onRetry={() => loadFlightPlansForAirport(flightPlansModalAirport)}
            />
          ) : airportFlightPlans.length === 0 ? (
            <EmptyState title="Sin vuelos programados" message="Este aeropuerto no tiene vuelos programados registrados." />
          ) : (
            <div className="space-y-3">
              {airportFlightPlans.map((flightPlan, index) => {
                const key = flightPlan.idVueloProgramado ?? flightPlan.id ?? `${flightPlan.origin}-${flightPlan.dest}-${index}`;
                return (
                  <ScheduledFlightPlanItem
                    key={key}
                    flightPlan={flightPlan}
                    simTime={flightPlanTimingRef}
                    onCancel={handleCancelFlightPlan}
                    canceling={cancelingFlightPlanId === (flightPlan.idVueloProgramado ?? flightPlan.id)}
                  />
                );
              })}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );

  const flightPlanConfirmationModal = (
    <Modal
      open={Boolean(flightPlanConfirmation)}
      onClose={() => {
        if (cancelingFlightPlanId) return;
        setFlightPlanConfirmation(null);
      }}
      title="Confirmar cancelacion de vuelo programado"
      maxWidth="max-w-2xl"
    >
      <div className="flex max-h-[80vh] flex-col">
        <div className="border-b border-slate-800 px-6 py-5 pr-16">
          <p className="text-xs font-semibold uppercase tracking-[0.24em] text-warning">
            Confirmacion
          </p>
          <h3 className="mt-2 text-2xl font-semibold text-slate-100">
            Cancelar vuelo programado
          </h3>
          <p className="mt-3 text-sm leading-relaxed text-slate-400">
            La cancelacion se registrara sobre la siguiente ocurrencia aplicable del vuelo segun la hora simulada actual.
          </p>
        </div>

        <div className="space-y-5 overflow-y-auto px-6 py-5">
          <div className="rounded-2xl border border-slate-700/80 bg-slate-900/40 p-5">
            <div className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
              Vuelo seleccionado
            </div>
            <div className="mt-3 flex flex-wrap items-center gap-3 text-lg font-semibold text-white">
              <span>{flightPlanConfirmation?.flightPlan?.origin ?? "--"}</span>
              <span className="text-slate-500">{"->"}</span>
              <span>{flightPlanConfirmation?.flightPlan?.dest ?? "--"}</span>
            </div>

            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <div className="rounded-xl border border-slate-700/70 bg-surface-2/70 p-4">
                <div className="text-xs uppercase tracking-wide text-slate-400">Hora de salida</div>
                <div className="mt-1 text-lg font-semibold text-white">
                  {flightPlanConfirmation?.timing?.departureLabel ?? "--:--"} UTC
                </div>
              </div>

              <div className="rounded-xl border border-slate-700/70 bg-surface-2/70 p-4">
                <div className="text-xs uppercase tracking-wide text-slate-400">Fecha afectada</div>
                <div className="mt-1 text-lg font-semibold text-white">
                  {flightPlanConfirmation?.timing?.effectiveDateLabel ?? "--"}
                </div>
                <div className="mt-1 text-sm text-info">
                  {flightPlanConfirmation?.timing?.appliesToday ? "Afecta el vuelo de hoy" : "Afecta el vuelo del dia siguiente"}
                </div>
              </div>
            </div>
          </div>

          <div className="rounded-2xl border border-warning/30 bg-warning/10 p-5">
            <div className="text-sm font-semibold text-warning">Regla aplicada</div>
            <p className="mt-2 text-sm leading-relaxed text-slate-200">
              Si registras la cancelacion hasta las{" "}
              <span className="font-semibold text-white">
                {flightPlanConfirmation?.timing?.cutoffLabel ?? "--:--"} UTC
              </span>
              , afecta el mismo dia. Despues de esa hora, afecta el dia siguiente.
            </p>
          </div>
        </div>

        <div className="flex flex-col-reverse gap-3 border-t border-slate-800 px-6 py-5 sm:flex-row sm:justify-end">
          <button
            type="button"
            onClick={() => setFlightPlanConfirmation(null)}
            disabled={Boolean(cancelingFlightPlanId)}
            className="inline-flex items-center justify-center rounded-lg border border-slate-700 bg-surface-2 px-4 py-2.5 text-sm font-semibold text-slate-200 transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
          >
            Volver
          </button>
          <button
            type="button"
            onClick={confirmFlightPlanCancellation}
            disabled={Boolean(cancelingFlightPlanId)}
            className="inline-flex items-center justify-center rounded-lg border border-danger/50 bg-danger/15 px-4 py-2.5 text-sm font-semibold text-danger transition-colors hover:bg-danger/20 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {cancelingFlightPlanId ? "Cancelando..." : "Confirmar cancelacion"}
          </button>
        </div>
      </div>
    </Modal>
  );

  return (
    <div className="w-[min(360px,90vw)] lg:w-100 shrink-0 bg-surface-1 border-slate-800 h-screen flex flex-col relative z-[9999]">
      <div className="flex items-center border-b border-slate-800">
        <button
          type="button"
          onClick={onClose}
          aria-label="Cerrar panel de detalle"
          className="p-4 hover:bg-slate-800 text-slate-400 hover:text-white transition-colors shrink-0"
        >
          <PanelRightClose className="w-5 h-5" />
        </button>
        <div className="flex flex-1 overflow-x-auto no-scrollbar">
          {PANEL_TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => onTabChange(tab.id)}
              aria-pressed={activeTab === tab.id}
              className={`px-3 py-4 text-xs font-semibold whitespace-nowrap ${activeTab === tab.id ? "border-b-2 border-slate-300 text-slate-200" : "text-slate-400 hover:text-slate-200"}`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {mapHighlight && (
        <div className="border-b border-slate-800 bg-info/5 px-4 py-3">
          <div className="mb-2 flex items-center justify-between gap-2">
            <div className="flex min-w-0 items-center gap-2 text-xs font-bold text-info">
              <Route className="h-4 w-4 shrink-0" />
              <span className="truncate">{mapHighlight.label}</span>
            </div>
            <button
              type="button"
              onClick={() => setMapHighlight(null)}
              aria-label="Quitar ruta del mapa"
              className="inline-flex shrink-0 items-center gap-1 rounded-md border border-slate-700 px-2 py-1 text-[10px] font-semibold text-slate-300 hover:border-slate-500 hover:text-white"
            >
              <X className="h-3 w-3" /> Quitar
            </button>
          </div>
          <div className="max-h-48 space-y-3 overflow-y-auto no-scrollbar">
            {mapHighlight.rutas.map((r, ri) => (
              <div key={ri}>
                {mapHighlight.kind === "envio" && (
                  <div className="mb-1 flex items-center justify-between gap-2">
                    <span className="text-[10px] font-semibold uppercase tracking-wide text-slate-300">Ruta {ri + 1}</span>
                    <span className="shrink-0 text-[10px] text-slate-400">{r.cantidad} maleta{r.cantidad !== 1 ? "s" : ""}</span>
                  </div>
                )}
                {r.escalas.length === 0 ? (
                  <div className="text-[11px] text-slate-500">Sin escalas.</div>
                ) : (
                  <ol className="space-y-1">
                    {r.escalas.map((e, ei) => (
                      <li key={ei} className="flex items-center justify-between gap-2 text-[11px]">
                        <span className="font-medium text-slate-200">{e.origen} {"->"} {e.destino}</span>
                        <span className="shrink-0 text-slate-400">{e.codigo} · {formatHoraPlan(e.salida)} {"->"} {formatHoraPlan(e.llegada)}</span>
                      </li>
                    ))}
                  </ol>
                )}
                {mapHighlight.kind === "envio" && r.maletas?.length > 0 && (
                  <div className="mt-1 text-[10px] leading-relaxed text-slate-400">
                    <span className="text-slate-500">Maletas: </span>
                    {r.maletas.join(", ")}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="p-4 border-b border-slate-800 flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={activeTab === "flights"
              ? "Buscar por codigo, tramo, origen o destino..."
              : `Buscar ${activeTabLabel.toLowerCase()}...`}
            aria-label={activeTab === "flights"
              ? "Buscar unidades de transporte por codigo, tramo, origen o destino"
              : `Buscar ${activeTabLabel.toLowerCase()}`}
            className="w-full bg-surface-2 border border-slate-800 rounded-lg py-1.5 pl-3 pr-3 text-sm text-slate-200 placeholder:text-slate-400 focus:outline-none focus:border-slate-600"
          />
          <button
            type="button"
            onClick={() => (activeTab === "orders" ? fetchEnvios() : activeSource?.refetch?.())}
            disabled={activeTab === "orders" ? enviosStatus === "loading" : activeSource?.loading}
            aria-label="Refrescar"
            title="Refrescar"
            className="shrink-0 p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-surface-2 transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${(activeTab === "orders" ? enviosStatus === "loading" : activeSource?.loading) ? "animate-spin" : ""}`} />
          </button>
        </div>

        {activeTab === "flights" && (
          <div className="flex flex-col gap-2">
            <div className="relative">
              <Filter className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <select
                value={flightStatusFilter}
                onChange={(e) => setFlightStatusFilter(e.target.value)}
                aria-label="Filtrar UT por estado"
                className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-2 py-1.5 pl-9 pr-8 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                {FLIGHT_STATUS_FILTERS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex items-center gap-2">
              <div className="relative flex-1 min-w-0">
                <ArrowDownUp className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <select
                  value={flightSortKey}
                  onChange={(e) => setFlightSortKey(e.target.value)}
                  aria-label="Ordenar UT por"
                  className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-2 py-1.5 pl-9 pr-8 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
                >
                  <option value="occupancy">Ordenar: ocupacion</option>
                  <option value="departure">Ordenar: hora de salida</option>
                  <option value="arrival">Ordenar: hora de llegada</option>
                  <option value="origin">Ordenar: origen</option>
                  <option value="dest">Ordenar: destino</option>
                </select>
              </div>
              <button
                type="button"
                onClick={() => setFlightSortDir((d) => (d === "asc" ? "desc" : "asc"))}
                aria-label={flightSortDir === "asc"
                  ? "Orden ascendente; cambiar a descendente"
                  : "Orden descendente; cambiar a ascendente"}
                title={flightSortDir === "asc" ? "Ascendente" : "Descendente"}
                className="shrink-0 inline-flex items-center gap-1 rounded-lg border border-slate-800 bg-surface-2 px-2.5 py-1.5 text-xs font-semibold text-slate-300 hover:text-white hover:border-slate-600 transition-colors"
              >
                {flightSortDir === "asc" ? <ArrowUp className="h-4 w-4" /> : <ArrowDown className="h-4 w-4" />}
                {flightSortDir === "asc" ? "Asc" : "Desc"}
              </button>
            </div>

            <details className="group rounded-lg border border-slate-800 bg-surface-2">
              <summary className="flex cursor-pointer list-none items-center justify-between px-3 py-1.5 text-xs font-semibold text-slate-300">
                <span className="flex items-center gap-1.5">
                  <Filter className="h-3.5 w-3.5 text-slate-400" /> Filtros
                </span>
                <ChevronDown className="h-4 w-4 text-slate-500 transition-transform group-open:rotate-180" />
              </summary>
              <div className="flex flex-col gap-2 px-3 pb-3 pt-1">
                <select
                  value={flightSemaforo}
                  onChange={(e) => setFlightSemaforo(e.target.value)}
                  aria-label="Filtrar UT por semaforo"
                  className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
                >
                  {SEMAFORO_OPCIONES.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
                <input
                  type="text"
                  value={flightCodePattern}
                  onChange={(e) => setFlightCodePattern(e.target.value)}
                  placeholder="Codigo (patron)..."
                  aria-label="Filtrar UT por patron de codigo"
                  className="w-full rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 placeholder:text-slate-400 focus:outline-none focus:border-slate-600"
                />
                <select
                  value={flightOriginFilter}
                  onChange={(e) => setFlightOriginFilter(e.target.value)}
                  aria-label="Filtrar UT por origen"
                  className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
                >
                  <option value="ALL">Origen: todos</option>
                  {flightOriginOptions.map((code) => {
                    const city = cityByIata.get(code);
                    return (
                      <option key={code} value={code}>{city ? `${code} — ${city}` : code}</option>
                    );
                  })}
                </select>
                <select
                  value={flightDestFilter}
                  onChange={(e) => setFlightDestFilter(e.target.value)}
                  aria-label="Filtrar UT por destino"
                  className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
                >
                  <option value="ALL">Destino: todos</option>
                  {flightDestOptions.map((code) => {
                    const city = cityByIata.get(code);
                    return (
                      <option key={code} value={code}>{city ? `${code} — ${city}` : code}</option>
                    );
                  })}
                </select>
                {(flightCodePattern || flightOriginFilter !== "ALL" || flightDestFilter !== "ALL" || flightSemaforo !== "ALL") && (
                  <button
                    type="button"
                    onClick={clearFlightFilters}
                    className="self-start text-[11px] font-semibold text-info hover:underline"
                  >
                    Limpiar filtros
                  </button>
                )}
              </div>
            </details>
          </div>
        )}

        {activeTab === "airports" && (
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <div className="relative flex-1 min-w-0">
                <ArrowDownUp className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <select
                  value={airportSortKey}
                  onChange={(e) => setAirportSortKey(e.target.value)}
                  aria-label="Ordenar almacenes por"
                  className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-2 py-1.5 pl-9 pr-8 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
                >
                  <option value="name">Ordenar: alfabetico</option>
                  <option value="occupancy">Ordenar: ocupacion</option>
                  <option value="nextDep">Ordenar: proxima salida (UT)</option>
                  <option value="nextArr">Ordenar: proxima llegada (UT)</option>
                </select>
              </div>
              <button
                type="button"
                onClick={() => setAirportSortDir((d) => (d === "asc" ? "desc" : "asc"))}
                aria-label={airportSortDir === "asc"
                  ? "Orden ascendente; cambiar a descendente"
                  : "Orden descendente; cambiar a ascendente"}
                title={airportSortDir === "asc" ? "Ascendente" : "Descendente"}
                className="shrink-0 inline-flex items-center gap-1 rounded-lg border border-slate-800 bg-surface-2 px-2.5 py-1.5 text-xs font-semibold text-slate-300 hover:text-white hover:border-slate-600 transition-colors"
              >
                {airportSortDir === "asc" ? <ArrowUp className="h-4 w-4" /> : <ArrowDown className="h-4 w-4" />}
                {airportSortDir === "asc" ? "Asc" : "Desc"}
              </button>
            </div>

            <details className="group rounded-lg border border-slate-800 bg-surface-2">
              <summary className="flex cursor-pointer list-none items-center justify-between px-3 py-1.5 text-xs font-semibold text-slate-300">
                <span className="flex items-center gap-1.5">
                  <Filter className="h-3.5 w-3.5 text-slate-400" /> Filtros
                </span>
                <ChevronDown className="h-4 w-4 text-slate-500 transition-transform group-open:rotate-180" />
              </summary>
              <div className="flex flex-col gap-2 px-3 pb-3 pt-1">
                <select
                  value={airportSemaforo}
                  onChange={(e) => setAirportSemaforo(e.target.value)}
                  aria-label="Filtrar almacenes por semaforo"
                  className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
                >
                  {SEMAFORO_OPCIONES.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
                <input
                  type="text"
                  value={airportCodePattern}
                  onChange={(e) => setAirportCodePattern(e.target.value)}
                  placeholder="Codigo (patron)..."
                  aria-label="Filtrar almacenes por patron de codigo"
                  className="w-full rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 placeholder:text-slate-400 focus:outline-none focus:border-slate-600"
                />
                <select
                  value={airportRegionFilter}
                  onChange={(e) => setAirportRegionFilter(e.target.value)}
                  aria-label="Filtrar almacenes por region continental"
                  className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
                >
                  <option value="ALL">Region: todas</option>
                  {airportRegionOptions.map((region) => (
                    <option key={region} value={region}>{region}</option>
                  ))}
                </select>
                {(airportCodePattern || airportRegionFilter !== "ALL" || airportSemaforo !== "ALL") && (
                  <button
                    type="button"
                    onClick={clearAirportFilters}
                    className="self-start text-[11px] font-semibold text-info hover:underline"
                  >
                    Limpiar filtros
                  </button>
                )}
              </div>
            </details>
          </div>
        )}

        {activeTab === "orders" && (
          <>
            <div className="flex items-center gap-2">
              <div className="relative flex-1 min-w-0">
                <ArrowDownUp className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <select
                  value={orderSortKey}
                  onChange={(e) => setOrderSortKey(e.target.value)}
                  aria-label="Ordenar pedidos por"
                  className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-2 py-1.5 pl-9 pr-8 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
                >
                  <option value="code">Ordenar: código</option>
                  <option value="registration">Ordenar: hora de registro</option>
                  <option value="origin">Ordenar: origen</option>
                  <option value="dest">Ordenar: destino</option>
                  <option value="bags">Ordenar: cant. de maletas</option>
                  <option value="routes">Ordenar: cant. de rutas</option>
                </select>
              </div>
              <button
                type="button"
                onClick={() => setOrderSortDir((d) => (d === "asc" ? "desc" : "asc"))}
                aria-label={orderSortDir === "asc" ? "Orden ascendente; cambiar a descendente" : "Orden descendente; cambiar a ascendente"}
                title={orderSortDir === "asc" ? "Ascendente" : "Descendente"}
                className="shrink-0 inline-flex items-center gap-1 rounded-lg border border-slate-800 bg-surface-2 px-2.5 py-1.5 text-xs font-semibold text-slate-300 hover:text-white hover:border-slate-600 transition-colors"
              >
                {orderSortDir === "asc" ? <ArrowUp className="h-4 w-4" /> : <ArrowDown className="h-4 w-4" />}
                {orderSortDir === "asc" ? "Asc" : "Desc"}
              </button>
            </div>
            <details className="group rounded-lg border border-slate-800 bg-surface-2">
            <summary className="flex cursor-pointer list-none items-center justify-between px-3 py-1.5 text-xs font-semibold text-slate-300">
              <span className="flex items-center gap-1.5">
                <Filter className="h-3.5 w-3.5 text-slate-400" /> Filtros (por tramo / ruta)
              </span>
              <ChevronDown className="h-4 w-4 text-slate-500 transition-transform group-open:rotate-180" />
            </summary>
            <div className="flex flex-col gap-2 px-3 pb-3 pt-1">
              <select
                value={envioOriginFilter}
                onChange={(e) => setEnvioOriginFilter(e.target.value)}
                aria-label="Filtrar envios por origen (en el tramo o la ruta)"
                className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                <option value="ALL">Origen (ruta): todos</option>
                {enviosAirportOptions.origins.map((code) => {
                  const city = cityByIata.get(code);
                  return <option key={code} value={code}>{city ? `${code} — ${city}` : code}</option>;
                })}
              </select>
              <select
                value={envioDestFilter}
                onChange={(e) => setEnvioDestFilter(e.target.value)}
                aria-label="Filtrar envios por destino (en el tramo o la ruta)"
                className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                <option value="ALL">Destino (ruta): todos</option>
                {enviosAirportOptions.dests.map((code) => {
                  const city = cityByIata.get(code);
                  return <option key={code} value={code}>{city ? `${code} — ${city}` : code}</option>;
                })}
              </select>
              {(envioOriginFilter !== "ALL" || envioDestFilter !== "ALL") && (
                <button
                  type="button"
                  onClick={clearEnvioFilters}
                  className="self-start text-[11px] font-semibold text-info hover:underline"
                >
                  Limpiar filtros
                </button>
              )}
            </div>
          </details>
          </>
        )}

        {activeTab === "routes" && (
          <>
            <div className="flex items-center gap-2">
              <div className="relative flex-1 min-w-0">
                <ArrowDownUp className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <select
                  value={routeSortKey}
                  onChange={(e) => setRouteSortKey(e.target.value)}
                  aria-label="Ordenar rutas por"
                  className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-2 py-1.5 pl-9 pr-8 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
                >
                  <option value="code">Ordenar: código</option>
                  <option value="departure">Ordenar: hora salida (1er vuelo)</option>
                  <option value="arrival">Ordenar: hora llegada (ultimo vuelo)</option>
                  <option value="duration">Ordenar: duracion</option>
                  <option value="flights">Ordenar: cant. de vuelos</option>
                  <option value="slack">Ordenar: holgura</option>
                  <option value="deadline">Ordenar: plazo maximo</option>
                </select>
              </div>
              <button
                type="button"
                onClick={() => setRouteSortDir((d) => (d === "asc" ? "desc" : "asc"))}
                aria-label={routeSortDir === "asc" ? "Orden ascendente; cambiar a descendente" : "Orden descendente; cambiar a ascendente"}
                title={routeSortDir === "asc" ? "Ascendente" : "Descendente"}
                className="shrink-0 inline-flex items-center gap-1 rounded-lg border border-slate-800 bg-surface-2 px-2.5 py-1.5 text-xs font-semibold text-slate-300 hover:text-white hover:border-slate-600 transition-colors"
              >
                {routeSortDir === "asc" ? <ArrowUp className="h-4 w-4" /> : <ArrowDown className="h-4 w-4" />}
                {routeSortDir === "asc" ? "Asc" : "Desc"}
              </button>
            </div>
            <details className="group rounded-lg border border-slate-800 bg-surface-2">
            <summary className="flex cursor-pointer list-none items-center justify-between px-3 py-1.5 text-xs font-semibold text-slate-300">
              <span className="flex items-center gap-1.5">
                <Filter className="h-3.5 w-3.5 text-slate-400" /> Filtros
              </span>
              <ChevronDown className="h-4 w-4 text-slate-500 transition-transform group-open:rotate-180" />
            </summary>
            <div className="flex flex-col gap-2 px-3 pb-3 pt-1">
              <select
                value={routeOriginFilter}
                onChange={(e) => setRouteOriginFilter(e.target.value)}
                aria-label="Filtrar rutas por origen"
                className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                <option value="ALL">Origen: todos</option>
                {routeAirportOptions.origins.map((code) => {
                  const city = cityByIata.get(code);
                  return <option key={code} value={code}>{city ? `${code} — ${city}` : code}</option>;
                })}
              </select>
              <select
                value={routeDestFilter}
                onChange={(e) => setRouteDestFilter(e.target.value)}
                aria-label="Filtrar rutas por destino"
                className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                <option value="ALL">Destino: todos</option>
                {routeAirportOptions.dests.map((code) => {
                  const city = cityByIata.get(code);
                  return <option key={code} value={code}>{city ? `${code} — ${city}` : code}</option>;
                })}
              </select>
              <select
                value={routeStatusFilter}
                onChange={(e) => setRouteStatusFilter(e.target.value)}
                aria-label="Filtrar rutas por estado"
                className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                <option value="ALL">Estado: todos</option>
                {ROUTE_STATUSES.map((est) => (
                  <option key={est} value={est}>{est.replace(/_/g, " ")}</option>
                ))}
              </select>
              <select
                value={routePlazoFilter}
                onChange={(e) => setRoutePlazoFilter(e.target.value)}
                aria-label="Filtrar rutas por plazo"
                className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                <option value="ALL">Plazo: todos</option>
                <option value="1">Plazo: 1 día</option>
                <option value="2">Plazo: 2 días</option>
              </select>
              {(routeOriginFilter !== "ALL" || routeDestFilter !== "ALL" || routeStatusFilter !== "ALL" || routePlazoFilter !== "ALL") && (
                <button
                  type="button"
                  onClick={clearRouteFilters}
                  className="self-start text-[11px] font-semibold text-info hover:underline"
                >
                  Limpiar filtros
                </button>
              )}
            </div>
          </details>
          </>
        )}

        {activeTab === "bags" && (
          <>
            <div className="flex items-center gap-2">
              <div className="relative flex-1 min-w-0">
                <ArrowDownUp className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <select
                  value={bagSortKey}
                  onChange={(e) => setBagSortKey(e.target.value)}
                  aria-label="Ordenar maletas por"
                  className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-2 py-1.5 pl-9 pr-8 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
                >
                  <option value="code">Ordenar: código</option>
                  <option value="registration">Ordenar: hora de registro</option>
                  <option value="origin">Ordenar: origen</option>
                  <option value="dest">Ordenar: destino</option>
                  <option value="arrival">Ordenar: llegada estimada</option>
                </select>
              </div>
              <button
                type="button"
                onClick={() => setBagSortDir((d) => (d === "asc" ? "desc" : "asc"))}
                aria-label={bagSortDir === "asc" ? "Orden ascendente; cambiar a descendente" : "Orden descendente; cambiar a ascendente"}
                title={bagSortDir === "asc" ? "Ascendente" : "Descendente"}
                className="shrink-0 inline-flex items-center gap-1 rounded-lg border border-slate-800 bg-surface-2 px-2.5 py-1.5 text-xs font-semibold text-slate-300 hover:text-white hover:border-slate-600 transition-colors"
              >
                {bagSortDir === "asc" ? <ArrowUp className="h-4 w-4" /> : <ArrowDown className="h-4 w-4" />}
                {bagSortDir === "asc" ? "Asc" : "Desc"}
              </button>
            </div>
            <details className="group rounded-lg border border-slate-800 bg-surface-2">
            <summary className="flex cursor-pointer list-none items-center justify-between px-3 py-1.5 text-xs font-semibold text-slate-300">
              <span className="flex items-center gap-1.5">
                <Filter className="h-3.5 w-3.5 text-slate-400" /> Filtros
              </span>
              <ChevronDown className="h-4 w-4 text-slate-500 transition-transform group-open:rotate-180" />
            </summary>
            <div className="flex flex-col gap-2 px-3 pb-3 pt-1">
              <select
                value={bagOriginFilter}
                onChange={(e) => setBagOriginFilter(e.target.value)}
                aria-label="Filtrar maletas por origen"
                className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                <option value="ALL">Origen: todos</option>
                {bagAirportOptions.origins.map((code) => {
                  const city = cityByIata.get(code);
                  return <option key={code} value={code}>{city ? `${code} — ${city}` : code}</option>;
                })}
              </select>
              <select
                value={bagDestFilter}
                onChange={(e) => setBagDestFilter(e.target.value)}
                aria-label="Filtrar maletas por destino"
                className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                <option value="ALL">Destino: todos</option>
                {bagAirportOptions.dests.map((code) => {
                  const city = cityByIata.get(code);
                  return <option key={code} value={code}>{city ? `${code} — ${city}` : code}</option>;
                })}
              </select>
              <select
                value={bagStatusFilter}
                onChange={(e) => setBagStatusFilter(e.target.value)}
                aria-label="Filtrar maletas por estado"
                className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-1 py-1.5 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                <option value="ALL">Estado: todos</option>
                {["EN_ALMACEN", "EN_TRANSITO", "ENTREGADA", "REPLANIFICANDO", "POR_RECOGER"].map((est) => (
                  <option key={est} value={est}>{est.replace(/_/g, " ")}</option>
                ))}
              </select>
              {(bagOriginFilter !== "ALL" || bagDestFilter !== "ALL" || bagStatusFilter !== "ALL") && (
                <button
                  type="button"
                  onClick={clearBagFilters}
                  className="self-start text-[11px] font-semibold text-info hover:underline"
                >
                  Limpiar filtros
                </button>
              )}
            </div>
          </details>
          </>
        )}
      </div>

      <div key={`${activeTab}-${simulationLoaded ? "loaded" : "empty"}`} className="app-scrollbar m-4 mr-0 flex-1 overflow-y-auto">
          {tabContent}
      </div>

      <ColorLegend />
      {flightPlansModal}
      {flightPlanConfirmationModal}
    </div>
  );
}

function TabBody({ loading, error, refetch, rows, empty, renderItem, virtuosoRef, keyForRow, onFirstItemIndexChange }) {
  if (loading) return <LoadingState />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  if (!rows || rows.length === 0) return <EmptyState title="Sin resultados" message={empty} />;

  return (
    <Virtuoso
      ref={virtuosoRef}
      style={{ height: "100%" }}
      components={{ Scroller: PanelScroller }}
      totalCount={rows.length}
      computeItemKey={(index) => keyForRow ? keyForRow(rows[index], index) : index}
      itemContent={(index) => renderItem(rows[index], index)}
      rangeChanged={(range) => {
        if (onFirstItemIndexChange && range) {
          onFirstItemIndexChange(range.startIndex);
        }
      }}
    />
  );
}
