import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Virtuoso } from "react-virtuoso";
import { useLocation } from "react-router-dom";
import { Ban, Filter, PanelRightClose, MapPin, Globe, Info, ChevronDown, Plane, RefreshCw, Package, Luggage, ArrowDownUp, ArrowUp, ArrowDown, Route, X, Crosshair } from "lucide-react";
import { useFetch } from "../../hooks/useFetch";
import { useStompPublish } from "../../hooks/useStomp";
import { cancelFlight, listFlights } from "../../api/flights";
import { listOrders } from "../../api/orders";
import { listAirports } from "../../api/airports";
import { listMaletas } from "../../api/maletas";
import { listRutas } from "../../api/rutas";
import { obtenerContenidoAlmacen, obtenerEnviosDiaADia, obtenerEnviosPanel, obtenerManifiestoVuelo, obtenerManifiestoVueloDiaADia, obtenerRutaMaleta, obtenerRutasEnvio, obtenerRutasEnvioDiaADia } from "../../api/simulator";
import { apiGet, USE_MOCK } from "../../api/client";
import { useMapFocus } from "../../context/MapFocusContext";
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
  { id: "routes", label: "Rutas" },
  { id: "bags", label: "Maletas" },
  { id: "airports", label: "Aerop." },
];

const flightStatusColor = (s) => {
  switch ((s ?? "").toUpperCase().replace(/_/g, " ")) {
    case "PROGRAMADO": return "bg-info text-slate-900";
    case "EN PROGRESO": return "bg-warning text-yellow-900";
    case "CONFIRMADO": return "bg-indigo-600 text-white";
    case "CANCELADO": return "bg-danger text-white";
    case "FINALIZADO": return "bg-success text-emerald-900";
    default: return "bg-slate-500 text-white";
  }
};

const occupancyColor = (pct) => pct >= 85 ? "bg-danger" : pct >= 60 ? "bg-warning" : "bg-success";

const bagStatusColor = (s) => {
  switch (s) {
    case "EN_ALMACEN": return "bg-slate-300 text-slate-800";
    case "EN_TRASLADO": return "bg-warning text-yellow-900";
    case "EN_VUELO": return "bg-info text-slate-900";
    case "ENTREGADA": return "bg-success text-emerald-900";
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
    case "REPLANIFICADA": return "border-warning/40 text-warning";
    case "COMPLETADA": return "border-success/40 text-success";
    default: return "border-slate-700 text-slate-400";
  }
};

const EMPTY_MANIFEST = { bags: [], orders: [] };

const FlightItem = memo(function FlightItem({ flight, onCancel, canceling, loadManifest, onFocus, onDeselect, isSelected }) {
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
    <div className={`flex flex-col border-b border-slate-800/50 h-full cursor-pointer ${isSelected ? "rounded-lg ring-1 ring-info/70 bg-info/5" : ""}`} onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-start mb-2">
        <div>
          <h4 className="font-bold text-lg text-slate-200">{flight.id}</h4>
          <div className={`mt-1 text-xs font-semibold px-2 py-0.5 rounded ${occupancyColor(pct)} inline-block text-slate-900`}>
            Capacidad: {flight.used}/{flight.capacity}
          </div>
        </div>
        <div className="flex flex-col items-end gap-2">
          <div className="flex items-center gap-1.5">
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
        <div onClick={(e) => e.stopPropagation()} className="cursor-default text-xs text-slate-400">
          <div className="flex justify-between mt-2">
            <div className="flex flex-col">
              <span className="font-medium text-slate-200">{flight.origin}</span>
              <span className="mt-0.5">Salida: {formatUtcDateTime(flight.depTime)}</span>
            </div>
            <div className="flex flex-col text-right">
              <span className="font-medium text-slate-200">{flight.dest}</span>
              <span className="mt-0.5">Llegada: {formatUtcDateTime(flight.arrTime)}</span>
            </div>
          </div>

          <div className="mt-3 space-y-3 border-t border-slate-800 pt-3">
            {manifestStatus === "loading" ? (
              <div className="text-[11px] text-slate-500">Cargando manifiesto...</div>
            ) : manifestStatus === "error" ? (
              <div className="text-[11px] text-danger">No se pudo cargar el manifiesto de esta UT.</div>
            ) : (
              <>
                <ManifestSection
                  icon={Package}
                  title="Envios a bordo"
                  count={manifest.orders.length}
                  emptyLabel="Sin envios asignados a esta UT."
                >
                  {manifest.orders.map((o) => (
                    <li key={o.id} className="flex items-center justify-between gap-2">
                      <span className="truncate font-medium text-slate-200">{o.id}</span>
                      <span className="shrink-0 text-[10px] text-slate-400">
                        {o.origin} {"->"} {o.dest} · {o.bags} mal.
                      </span>
                    </li>
                  ))}
                </ManifestSection>

                <ManifestSection
                  icon={Luggage}
                  title="Maletas a bordo"
                  count={manifest.bags.length}
                  emptyLabel="Sin maletas asignadas a esta UT."
                  scroll
                >
                  {manifest.bags.map((b) => (
                    <li key={b.idMaleta} className="flex items-center justify-between gap-2">
                      <span className="truncate text-slate-200">{b.idMaleta}</span>
                      <span className={`shrink-0 rounded px-1.5 py-0.5 text-[9px] font-bold tracking-wider ${bagStatusColor(b.estado)}`}>
                        {bagStatusLabel(b.estado)}
                      </span>
                    </li>
                  ))}
                </ManifestSection>
              </>
            )}
          </div>
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
    <div className="flex flex-col border-b border-slate-800/50 h-full cursor-pointer" onClick={() => setExpanded(!expanded)}>
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

const RouteItem = memo(function RouteItem({ route, onShowRoute }) {
  const [expanded, setExpanded] = useState(false);
  const vuelos = route.vuelos ?? [];
  const stops = useMemo(() => {
    if (vuelos.length === 0) return [];
    const out = [];
    out.push({ type: "node", icao: vuelos[0].aeropuertoOrigen });
    vuelos.forEach((v) => {
      out.push({
        type: "edge",
        label: `${v.codigo} · ${(v.fechaSalida ?? "").slice(11, 16)} -> ${(v.fechaLlegada ?? "").slice(11, 16)}`,
      });
      out.push({ type: "node", icao: v.aeropuertoDestino });
    });
    return out;
  }, [vuelos]);

  return (
    <div className="flex flex-col border-b border-slate-800/50 h-full cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-center mb-2">
        <div>
          <h4 className="font-bold text-lg text-slate-200">{route.idRuta}</h4>
          <span className="text-xs text-slate-400">Maleta: {route.idMaleta}</span>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-1.5">
          <span className={`text-[10px] font-bold px-2 py-1 rounded tracking-wider border bg-transparent ${routeStatusColor(route.estado)}`}>
            {(route.estado ?? "").replace(/_/g, " ")}
          </span>
          {onShowRoute && (
            <button
              type="button"
              onClick={(ev) => { ev.stopPropagation(); onShowRoute(route.idMaleta); }}
              className="inline-flex items-center gap-1 rounded-md border border-info/40 bg-info/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-info transition-colors hover:bg-info/20"
            >
              <Route className="h-3 w-3" /> Ver en mapa
            </button>
          )}
        </div>
      </div>
      {expanded && (
        <div className="flex flex-col pl-2 mt-2 cursor-default" onClick={(e) => e.stopPropagation()}>
          {stops.map((stop, j) => {
            if (stop.type === "node") {
              return (
                <div key={j} className="flex items-center gap-3 relative z-10">
                  <div className="w-3 h-3 rounded-full bg-success" />
                  <span className="text-xs font-bold text-slate-200">{stop.icao}</span>
                </div>
              );
            }
            return (
              <div key={j} className="flex items-center gap-3 my-1 relative">
                <div className="w-[1px] h-6 bg-slate-700 absolute left-[5px] top-[-4px] z-0" />
                <div className="ml-6 text-[10px] text-slate-400 whitespace-nowrap overflow-hidden text-ellipsis">
                  {stop.label}
                </div>
              </div>
            );
          })}
          <div className="text-[10px] text-slate-400 mt-3 pt-2 border-t border-slate-800">
            Plazo maximo: <span className="text-slate-200">{route.plazoMaximoDias}d</span> · Duracion: <span className="text-slate-200">{route.duracion}d</span>
          </div>
        </div>
      )}
    </div>
  );
});

const BagItem = memo(function BagItem({ bag, onShowRoute }) {
  const [expanded, setExpanded] = useState(false);
  const label = bagStatusLabel(bag.estado);
  return (
    <div className="flex flex-col border-b border-slate-800/50 h-full cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-start mb-2 gap-2">
        <h4 className="font-bold text-sm text-slate-200 break-all pr-2">{bag.idMaleta}</h4>
        <div className="flex shrink-0 flex-col items-end gap-1.5">
          <span className={`text-[10px] font-bold px-2 py-1 rounded tracking-wider whitespace-nowrap ${bagStatusColor(bag.estado)}`}>
            {label}
          </span>
          {onShowRoute && (
            <button
              type="button"
              onClick={(ev) => { ev.stopPropagation(); onShowRoute(bag.idMaleta); }}
              className="inline-flex items-center gap-1 rounded-md border border-info/40 bg-info/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-info transition-colors hover:bg-info/20"
            >
              <Route className="h-3 w-3" /> Ver ruta
            </button>
          )}
        </div>
      </div>
      {expanded && (
        <div className="cursor-default mt-2" onClick={(e) => e.stopPropagation()}>
          {bag.ubicacionActual && <div className="text-base font-bold text-slate-300 mb-2">{bag.ubicacionActual}</div>}
          <div className="text-xs text-slate-400 flex flex-col gap-0.5">
            <div>Pedido: <span className="text-slate-200">{bag.idPedido}</span></div>
            <div>Registro: <span className="text-slate-200">{(bag.fechaRegistro ?? "").replace("T", " ").slice(0, 16)}</span></div>
            {bag.fechaLlegada && <div>Entregada: <span className="text-slate-200">{bag.fechaLlegada.replace("T", " ").slice(0, 16)}</span></div>}
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
  origen: v?.aeropuertoOrigen,
  destino: v?.aeropuertoDestino,
  salida: v?.fechaSalida,
  llegada: v?.fechaLlegada,
});

// Seccion plegable con render DIFERIDO: los hijos no se montan hasta abrirla,
// asi las listas pesadas no cargan el DOM mientras estan cerradas.
function Collapsible({ title, defaultOpen = false, children }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="rounded-lg border border-slate-800 bg-surface-2/40">
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

const EnvioItem = memo(function EnvioItem({ envio, showOrigin = true, onShowRoute }) {
  const [expanded, setExpanded] = useState(false);
  const uts = envio.uts ?? [];
  const utResumen = uts.length === 0 ? "sin asignar" : uts.length <= 2 ? uts.join(", ") : `${uts[0]} +${uts.length - 1}`;
  return (
    <div className="flex flex-col border-b border-slate-800/50 h-full cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h4 className="truncate text-sm font-bold text-slate-200">{envio.id}</h4>
          <div className="text-[11px] text-slate-400">
            {showOrigin && envio.origin ? `${envio.origin} -> ` : ""}{envio.dest ?? "--"}
          </div>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-1 text-right">
          <div className="text-xs font-medium text-slate-300">{envio.bags} mal.</div>
          <div className="text-[10px] text-slate-400">UT: {utResumen}</div>
          {onShowRoute && (
            <button
              type="button"
              onClick={(ev) => { ev.stopPropagation(); onShowRoute(envio.id); }}
              className="inline-flex items-center gap-1 rounded-md border border-info/40 bg-info/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-info transition-colors hover:bg-info/20"
            >
              <Route className="h-3 w-3" /> Ver rutas
            </button>
          )}
        </div>
      </div>
      {expanded && (
        <div className="mt-2 space-y-1 text-[11px] text-slate-400" onClick={(e) => e.stopPropagation()}>
          <div>UT: <span className="text-slate-200">{uts.length ? uts.join(", ") : "—"}</span></div>
          {envio.horaEntrega && (
            <div>Entregado: <span className="text-slate-200">{formatHoraPlan(envio.horaEntrega)}</span></div>
          )}
        </div>
      )}
    </div>
  );
});

function EnvioSeccion({ title, envios, showOrigin = true, defaultOpen = false, max = 200, onShowRoute }) {
  const shown = envios.slice(0, max);
  return (
    <Collapsible title={`${title} (${envios.length})`} defaultOpen={defaultOpen}>
      {envios.length === 0 ? (
        <div className="pl-1 text-[11px] text-slate-500">Sin envios.</div>
      ) : (
        <>
          {shown.map((e, i) => <EnvioItem key={`${e.id}-${i}`} envio={e} showOrigin={showOrigin} onShowRoute={onShowRoute} />)}
          {envios.length > shown.length && (
            <div className="pt-1 text-[10px] text-slate-500">… y {envios.length - shown.length} mas (usa los filtros)</div>
          )}
        </>
      )}
    </Collapsible>
  );
}

const AirportItem = memo(function AirportItem({ apt, loadContenido, onFocus, onDeselect, isSelected }) {
  const [expanded, setExpanded] = useState(false);
  const pct = apt.capacity > 0 ? Math.round((apt.used / apt.capacity) * 100) : 0;

  // Contenido del almacen (envios/maletas presentes) pedido al back al expandir.
  const airportKey = apt.iata ?? apt.idAeropuerto;
  const [contenido, setContenido] = useState(EMPTY_CONTENIDO);
  const [contenidoStatus, setContenidoStatus] = useState("idle");

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
    <div className={`flex flex-col border-b border-slate-800/50 h-full cursor-pointer ${isSelected ? "rounded-lg ring-1 ring-info/70 bg-info/5" : ""}`} onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-center gap-2">
        <h4 className="font-bold text-lg text-slate-200">{apt.iata}</h4>
        <div className="flex items-center gap-2 shrink-0">
          <span className="text-xs text-slate-400">{pct}% Ocupado</span>
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
      {expanded && (
        <div className="cursor-default" onClick={(e) => e.stopPropagation()}>
          <div className="mt-4 flex justify-between items-start">
            <div className="flex flex-col gap-1 w-1/2">
              <div className="flex items-center gap-1 text-[10px] text-slate-400"><MapPin className="w-3 h-3" /> {apt.city}</div>
              <div className="flex items-center gap-1 text-[10px] text-slate-400"><Globe className="w-3 h-3" /> {apt.continent}</div>
            </div>
            <div className="flex flex-col w-1/2 pt-1">
              <div className="text-[10px] text-slate-400 mb-2">{apt.used} de {apt.capacity} maletas</div>
              <div className="w-full h-2 bg-surface-2 rounded-full overflow-hidden border border-slate-800">
                <div className={`h-full ${occupancyColor(pct)}`} style={{ width: `${pct}%` }} />
              </div>
            </div>
          </div>

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
          <LegendRow color="bg-warning shadow-[0_0_10px_rgba(255,221,0,0.7)]" label="Amarillo" value="60% a 84% ocupado" />
          <LegendRow color="bg-danger shadow-[0_0_10px_rgba(255,59,48,0.7)]" label="Rojo" value="85% o mas ocupado" />
        </div>
        <div>
          <div className="mb-2 font-semibold text-slate-200">Carga de vuelos</div>
          <FlightLegendRow color="text-white/60" label="Blanco translucido" value="vuelo vacio / 0% ocupado" />
          <FlightLegendRow color="text-success" label="Verde" value="menos de 60% ocupado" />
          <FlightLegendRow color="text-warning" label="Amarillo" value="60% a 84% ocupado" />
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

function normalizeFlightStatus(status) {
  return String(status ?? "")
    .trim()
    .toUpperCase()
    .replace(/\s+/g, "_");
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
  if (pct >= 60) return "AMBAR";
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
    if (key === "nextDep" || key === "nextArr") {
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
  const [query, setQuery] = useState("");
  // Marca si el query/filtro activo lo impuso una seleccion (click en mapa).
  // Al deseleccionar hay que limpiarlo para que el mapa deje de atenuar el resto.
  const selectionDrivenFilterRef = useRef(false);
  const [flightStatusFilter, setFlightStatusFilter] = useState("DEFAULT");
  const [flightOriginFilter, setFlightOriginFilter] = useState("ALL");
  const [flightDestFilter, setFlightDestFilter] = useState("ALL");
  const [flightCodePattern, setFlightCodePattern] = useState("");
  const [flightSortKey, setFlightSortKey] = useState("departure");
  const [flightSortDir, setFlightSortDir] = useState("asc");
  const [airportRegionFilter, setAirportRegionFilter] = useState("ALL");
  const [airportCodePattern, setAirportCodePattern] = useState("");
  const [airportSortKey, setAirportSortKey] = useState("occupancy");
  const [airportSortDir, setAirportSortDir] = useState("desc");
  const [envioOriginFilter, setEnvioOriginFilter] = useState("ALL");
  const [envioDestFilter, setEnvioDestFilter] = useState("ALL");
  const [enviosData, setEnviosData] = useState({ planificados: [], enVuelos: [] });
  const [enviosStatus, setEnviosStatus] = useState("idle");
  const [airportSemaforo, setAirportSemaforo] = useState("ALL");
  const [flightSemaforo, setFlightSemaforo] = useState("ALL");
  const [cancelingFlightId, setCancelingFlightId] = useState(null);
  const publish = useStompPublish();
  const sessionId = simulationPanelData?.sessionId ?? null;
  const { mapHighlight, setMapHighlight, selected, setSelected, setMapFocus, panelFocus, setMapDim, setFlightManifestLoader } = useMapFocus();
  const location = useLocation();
  const isDiaADia = location.pathname === "/";
  const isPeriodo = location.pathname === "/simulator/period";
  const isSimulator = location.pathname === "/" || location.pathname.startsWith("/simulator");
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
    setAirportSortKey("occupancy");
    setAirportSortDir("desc");
  };

  const clearEnvioFilters = () => {
    setEnvioOriginFilter("ALL");
    setEnvioDestFilter("ALL");
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

  // IATA -> coordenadas, para enfocar la camara del mapa (req 5/7/9).
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
    const hasExtraCriteria =
      q !== "" || pattern !== "" || flightOriginFilter !== "ALL" || flightDestFilter !== "ALL" || flightSemaforo !== "ALL";

    // Alcance por estado.
    let filtered;
    if (flightStatusFilter === "ALL") {
      // "Todos" sin ningun otro criterio: evitamos volcar todo el periodo de golpe.
      if (!hasExtraCriteria) return [];
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
        markFlightAsCanceled(flight.id);
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

  const simTime = simulationPanelData?.simTime;

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
      setEnviosData({ planificados: [], enVuelos: [] });
      setEnviosStatus("ready");
      return Promise.resolve();
    }
    setEnviosStatus("loading");

    if (isDiaADia) {
      return obtenerEnviosDiaADia()
        .then((data) => {
          setEnviosData({
            planificados: data?.planificados ?? [],
            enVuelos: data?.enVuelos ?? [],
          });
          setEnviosStatus("ready");
        })
        .catch(() => {
          setEnviosData({ planificados: [], enVuelos: [] });
          setEnviosStatus("error");
        });
    }

    return obtenerEnviosPanel(sessionId)
      .then((data) => {
        setEnviosData({
          planificados: data?.planificados ?? [],
          enVuelos: data?.enVuelos ?? [],
        });
        setEnviosStatus("ready");
      })
      .catch(() => {
        setEnviosData({ planificados: [], enVuelos: [] });
        setEnviosStatus("error");
      });
  }, [sessionId, isDiaADia, isPeriodo]);

  useEffect(() => {
    if ((isPeriodo || isDiaADia) && activeTab === "orders") fetchEnvios();
  }, [isPeriodo, isDiaADia, activeTab, fetchEnvios]);

  const enviosAirportOptions = useMemo(() => {
    const origins = new Set();
    const dests = new Set();
    for (const e of [...enviosData.planificados, ...enviosData.enVuelos]) {
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
    return rows;
  }, [envioOriginFilter, envioDestFilter, query, cityByIata]);

  const maletasFiltered = useMemo(() => {
    return filterByText(maletasVisibles, ["idMaleta", "idPedido", "estado", "ubicacionActual"]);
  }, [maletasVisibles, query]);

  const rutasFiltered = useMemo(() => {
    const source = rutas.data ?? [];
    const filtrados = simTime ? source.filter(r => idMaletasVisibles.has(r.idMaleta)) : source;
    return filterByText(filtrados, ["idRuta", "idMaleta", "estado"]);
  }, [rutas.data, idMaletasVisibles, query, simTime]);

  // Pide el manifiesto de una UT al back (envios/maletas que traslada). Se
  // resuelve alli porque el enlace UT->carga solo existe en las rutas globales
  // de la sesion, no en el subconjunto por-ventana que se carga en el panel.
  const loadFlightManifest = useCallback(
    (flightKey) => {
      if (USE_MOCK || !sessionId || !flightKey) {
        return Promise.resolve({ maletas: [], pedidos: [] });
      }
      return isDiaADia
        ? obtenerManifiestoVueloDiaADia(flightKey)
        : obtenerManifiestoVuelo(sessionId, flightKey);
    },
    [sessionId, isDiaADia]
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
      if (isDiaADia) {
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
        const ruta = await obtenerRutaMaleta(sessionId, idMaleta);
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
        toast.push({ type: "error", title: "No se pudo cargar la ruta", message: err.message });
      }
    },
    [sessionId, setMapHighlight, setMapFocus, centroideDe, toast]
  );

  // Resalta en el mapa las rutas de un envio por su ID (req 3/4).
  const showEnvioRoutes = useCallback(
    async (idPedido) => {
      if (!idPedido || !sessionId || USE_MOCK) return;
      try {
        const rutas = isDiaADia
          ? await obtenerRutasEnvioDiaADia(idPedido)
          : await obtenerRutasEnvio(sessionId, idPedido);
        const rutasVistas = new Set();
        const rutasNorm = [];
        const legSeen = new Set();
        const legs = [];
        for (const r of rutas ?? []) {
          const escalas = (r?.vuelos ?? []).map(toEscala);
          const clave = escalas.map((e) => e.codigo).join(">");
          if (!rutasVistas.has(clave)) {
            rutasVistas.add(clave);
            rutasNorm.push({ idMaleta: r.idMaleta, escalas });
          }
          for (const e of escalas) {
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
          label: `Envio ${idPedido} (${rutasNorm.length} ruta${rutasNorm.length !== 1 ? "s" : ""})`,
          rutas: rutasNorm,
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
    [sessionId, isDiaADia, setMapHighlight, setMapFocus, centroideDe, toast]
  );

  // Enfocar un almacen (req 5) o una UT (req 7) en el mapa desde el panel.
  const focusAirportOnMap = useCallback(
    (iata) => {
      if (!iata) return;
      setSelected({ kind: "airport", id: iata });
      const c = airportCoords.get(iata);
      if (c) setMapFocus({ lng: c.lng, lat: c.lat, zoom: 5, ts: Date.now() });
    },
    [airportCoords, setSelected, setMapFocus]
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

  // Mapa -> panel: al clicar el mapa, cambia de pestana y enfoca la entidad (req 6/8).
  useEffect(() => {
    if (!panelFocus) return;
    if (panelFocus.kind === "airport") {
      setActiveTab("airports");
      clearAirportFilters();
      setQuery(panelFocus.id);
    } else if (panelFocus.kind === "flight") {
      setActiveTab("flights");
      clearFlightFilters();
      setFlightStatusFilter("ALL");
      const f = (flights.data ?? []).find((fl) => (fl.idVueloInstancia ?? fl.id) === panelFocus.id);
      setQuery(f?.id ?? panelFocus.id);
    }
    // El filtro recien aplicado proviene de una seleccion en el mapa.
    selectionDrivenFilterRef.current = true;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [panelFocus]);

  // Al deseleccionar (boton X del panel o click en zona vacia del mapa), limpiar
  // el filtro que la seleccion habia impuesto para que el mapa deje de atenuar
  // los demas vuelos/aeropuertos y todo vuelva a su color normal.
  useEffect(() => {
    if (selected) return;
    if (!selectionDrivenFilterRef.current) return;
    selectionDrivenFilterRef.current = false;
    setQuery("");
    setFlightStatusFilter("DEFAULT");
    clearFlightFilters();
    clearAirportFilters();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected]);

  // Panel -> mapa: refleja el filtro de almacenes (semaforo y otros) (req 10/12).
  useEffect(() => {
    const active = airportSemaforo !== "ALL" || airportRegionFilter !== "ALL"
      || airportCodePattern.trim() !== "" || query.trim() !== "";
    if (activeTab === "airports" && active) {
      const set = new Set(airportsFiltered.map((a) => a.iata ?? a.idAeropuerto));
      setMapDim((prev) => ({ ...prev, airports: set }));
    } else {
      setMapDim((prev) => (prev.airports ? { ...prev, airports: null } : prev));
    }
  }, [activeTab, airportsFiltered, airportSemaforo, airportRegionFilter, airportCodePattern, query, setMapDim]);

  // Panel -> mapa: refleja el filtro de UT (semaforo y otros) (req 11/13).
  useEffect(() => {
    const active = flightSemaforo !== "ALL" || flightOriginFilter !== "ALL" || flightDestFilter !== "ALL"
      || flightCodePattern.trim() !== "" || query.trim() !== "" || flightStatusFilter !== "DEFAULT";
    if (activeTab === "flights" && active) {
      const set = new Set(visibleFlights.map((f) => f.idVueloInstancia ?? f.id));
      setMapDim((prev) => ({ ...prev, flights: set }));
    } else {
      setMapDim((prev) => (prev.flights ? { ...prev, flights: null } : prev));
    }
  }, [activeTab, visibleFlights, flightSemaforo, flightOriginFilter, flightDestFilter, flightCodePattern, query, flightStatusFilter, setMapDim]);

  const tabContent = {
    flights: (
      <TabBody
        {...flights}
        empty="Ejecuta la simulacion para cargar los vuelos del periodo."
        rows={visibleFlights}
        renderItem={(f, index) => (
          <FlightItem
            key={`${f.id}-${index}`}
            flight={f}
            onCancel={handleCancelFlight}
            canceling={cancelingFlightId === f.id}
            loadManifest={loadFlightManifest}
            onFocus={focusFlightOnMap}
            onDeselect={() => setSelected(null)}
            isSelected={selected?.kind === "flight" && selected.id === (f.idVueloInstancia ?? f.id)}
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
            defaultOpen
          />
          <EnvioSeccion
            title="En vuelos"
            envios={filterEnvios(enviosData.enVuelos)}
            onShowRoute={showEnvioRoutes}
            defaultOpen
          />
        </div>
      )
    ),
    routes: (
      <TabBody
        {...rutas}
        empty="Ejecuta la simulacion para cargar las rutas de la ventana activa."
        rows={rutasFiltered}
        renderItem={(r, index) => <RouteItem key={r.idRuta ?? r.id ?? index} route={r} onShowRoute={showMaletaRoute} />}
      />
    ),
    bags: (
      <TabBody
        {...maletas}
        empty="Ejecuta la simulacion para cargar las maletas de la ventana activa."
        rows={maletasFiltered}
        renderItem={(b, index) => <BagItem key={b.idMaleta ?? b.id ?? index} bag={b} onShowRoute={showMaletaRoute} />}
      />
    ),
    airports: (
      <TabBody
        {...airports}
        empty="Ejecuta la simulacion para cargar los aeropuertos del periodo."
        rows={airportsFiltered}
        renderItem={(a, index) => (
          <AirportItem
            key={a.iata ?? a.idAeropuerto ?? index}
            apt={a}
            loadContenido={loadAirportContenido}
            onFocus={focusAirportOnMap}
            onDeselect={() => setSelected(null)}
            isSelected={selected?.kind === "airport" && selected.id === (a.iata ?? a.idAeropuerto)}
          />
        )}
      />
    ),
  }[activeTab];

  return (
    <div className="w-[min(360px,90vw)] lg:w-[360px] shrink-0 bg-surface-1 border-l border-slate-800 h-screen flex flex-col relative z-[9999]">
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
          <div className="max-h-40 space-y-2 overflow-y-auto no-scrollbar">
            {mapHighlight.rutas.map((r, ri) => (
              <div key={ri}>
                {mapHighlight.kind === "envio" && (
                  <div className="mb-1 text-[10px] font-semibold uppercase tracking-wide text-slate-400">Maleta {r.idMaleta}</div>
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
        )}
      </div>

      <div key={`${activeTab}-${simulationLoaded ? "loaded" : "empty"}`} className="flex-1 overflow-y-auto m-4 no-scrollbar">
          {tabContent}
      </div>

      <ColorLegend />
    </div>
  );
}

function TabBody({ loading, error, refetch, rows, empty, renderItem }) {
  if (loading) return <LoadingState />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  if (!rows || rows.length === 0) return <EmptyState title="Sin resultados" message={empty} />;

  return (
    <Virtuoso
      style={{ height: "100%" }}
      totalCount={rows.length}
      itemContent={(index) => renderItem(rows[index], index)}
    />
  );
}
