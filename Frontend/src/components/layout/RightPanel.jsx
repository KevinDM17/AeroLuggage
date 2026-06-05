import { useEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import { Ban, Filter, PanelRightClose, MapPin, Globe, Info, ChevronDown, Plane, RefreshCw } from "lucide-react";
import { useFetch } from "../../hooks/useFetch";
import { useStompPublish } from "../../hooks/useStomp";
import { cancelFlight, listFlights } from "../../api/flights";
import { listOrders } from "../../api/orders";
import { listAirports } from "../../api/airports";
import { listMaletas } from "../../api/maletas";
import { listRutas } from "../../api/rutas";
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
    case "CONFIRMADA": return "border-indigo-500/40 text-indigo-300";
    case "COMPLETADA": return "border-success/40 text-success";
    case "FALLIDA": return "border-danger/40 text-danger";
    default: return "border-slate-700 text-slate-400";
  }
};

function FlightItem({ flight, onCancel, canceling }) {
  const [expanded, setExpanded] = useState(false);
  const pct = flight.capacity > 0 ? Math.round((flight.used / flight.capacity) * 100) : 0;
  const normalizedStatus = normalizeFlightStatus(flight.status);
  const canCancel = normalizedStatus === "PROGRAMADO";

  return (
    <div className="flex flex-col border-b border-slate-800/50 pb-4 mb-4 last:border-0 last:pb-0 cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-start mb-2">
        <div>
          <h4 className="font-bold text-lg text-slate-200">{flight.id}</h4>
          <div className={`mt-1 text-xs font-semibold px-2 py-0.5 rounded ${occupancyColor(pct)} inline-block text-slate-900`}>
            Capacidad: {flight.used}/{flight.capacity}
          </div>
        </div>
        <div className="flex flex-col items-end gap-2">
          <span className={`text-[10px] font-bold px-2 py-1 rounded tracking-wider ${flightStatusColor(normalizedStatus)}`}>
            {normalizedStatus.replace(/_/g, " ")}
          </span>
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
        </div>
      )}
    </div>
  );
}

function OrderItem({ order }) {
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
    <div className="flex flex-col border-b border-slate-800/50 pb-4 mb-4 last:border-0 last:pb-0 cursor-pointer" onClick={() => setExpanded(!expanded)}>
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
}

function RouteItem({ route }) {
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
    <div className="flex flex-col border-b border-slate-800/50 pb-4 mb-4 last:border-0 last:pb-0 cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-center mb-2">
        <div>
          <h4 className="font-bold text-lg text-slate-200">{route.idRuta}</h4>
          <span className="text-xs text-slate-400">Maleta: {route.idMaleta}</span>
        </div>
        <span className={`text-[10px] font-bold px-2 py-1 rounded tracking-wider border bg-transparent ${routeStatusColor(route.estado)}`}>
          {(route.estado ?? "").replace(/_/g, " ")}
        </span>
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
}

function BagItem({ bag }) {
  const [expanded, setExpanded] = useState(false);
  const label = bagStatusLabel(bag.estado);
  return (
    <div className="flex flex-col border-b border-slate-800/50 pb-4 mb-4 last:border-0 last:pb-0 cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-start mb-2">
        <h4 className="font-bold text-sm text-slate-200 break-all pr-2">{bag.idMaleta}</h4>
        <span className={`text-[10px] font-bold px-2 py-1 rounded tracking-wider whitespace-nowrap ${bagStatusColor(bag.estado)}`}>
          {label}
        </span>
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
}

function AirportItem({ apt }) {
  const [expanded, setExpanded] = useState(false);
  const pct = apt.capacity > 0 ? Math.round((apt.used / apt.capacity) * 100) : 0;
  return (
    <div className="flex flex-col border-b border-slate-800/50 pb-4 mb-4 last:border-0 last:pb-0 cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-center">
        <h4 className="font-bold text-lg text-slate-200">{apt.iata}</h4>
        <span className="text-xs text-slate-400">{pct}% Ocupado</span>
      </div>
      {expanded && (
        <div className="mt-4 flex justify-between items-start cursor-default" onClick={(e) => e.stopPropagation()}>
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
      )}
    </div>
  );
}

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
          <LegendRow color="bg-success shadow-[0_0_10px_rgba(0,255,136,0.7)]" label="Verde" value="operativo / capacidad disponible" />
          <LegendRow color="bg-warning shadow-[0_0_10px_rgba(255,221,0,0.7)]" label="Amarillo" value="alta ocupacion / alerta" />
          <LegendRow color="bg-danger shadow-[0_0_10px_rgba(255,59,48,0.7)]" label="Rojo" value="critico / colapso" />
        </div>
        <div>
          <div className="mb-2 font-semibold text-slate-200">Carga de vuelos</div>
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

function sortFlightsByDepartureAsc(rows = []) {
  return [...rows].sort((left, right) => {
    const leftTime = parseUtcDateTime(left?.depTime);
    const rightTime = parseUtcDateTime(right?.depTime);
    const leftValid = Number.isFinite(leftTime);
    const rightValid = Number.isFinite(rightTime);

    if (leftValid && rightValid) return leftTime - rightTime;
    if (leftValid) return -1;
    if (rightValid) return 1;
    return String(left?.id ?? "").localeCompare(String(right?.id ?? ""));
  });
}

function normalizeFlightStatus(status) {
  return String(status ?? "")
    .trim()
    .toUpperCase()
    .replace(/\s+/g, "_");
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
  const [flightStatusFilter, setFlightStatusFilter] = useState("DEFAULT");
  const [cancelingFlightId, setCancelingFlightId] = useState(null);
  const publish = useStompPublish();
  const sessionId = simulationPanelData?.sessionId ?? null;
  const location = useLocation();
  const isSimulator = location.pathname === "/" || location.pathname.startsWith("/simulator");
  const simulationLoaded = !isSimulator || simulationPanelData?.loaded === true;
  const activeTabLabel = PANEL_TABS.find((tab) => tab.id === activeTab)?.label ?? "";

  useEffect(() => {
    if (!isSimulator || simulationLoaded) return;
    setActiveTab("flights");
    setQuery("");
    setFlightStatusFilter("DEFAULT");
    setCancelingFlightId(null);
  }, [isSimulator, simulationLoaded]);

  const onTabChange = (tab) => {
    setActiveTab(tab);
    setQuery("");
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

  const flights = isSimulator ? createStaticSource(simulationLoaded ? simulationPanelData?.flights ?? [] : []) : flightsFetch;
  const airports = isSimulator ? createStaticSource(simulationLoaded ? simulationPanelData?.airports ?? [] : []) : airportsFetch;
  const orders = isSimulator ? createStaticSource(simulationLoaded ? simulationPanelData?.orders ?? [] : []) : ordersFetch;
  const maletas = isSimulator ? createStaticSource(simulationLoaded ? simulationPanelData?.bags ?? [] : []) : maletasFetch;
  const rutas = isSimulator ? createStaticSource(simulationLoaded ? simulationPanelData?.routes ?? [] : []) : rutasFetch;

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

  const FILTER_STATUSES = new Set(["CONFIRMADO", "EN_PROGRESO", "CANCELADO"]);

  const visibleFlights = useMemo(() => {
    const sourceFlights = Array.isArray(flights.data) ? flights.data : [];
    if (flightStatusFilter === "ALL") {
      return [];
    }
    const filtered = flightStatusFilter === "DEFAULT"
      ? sourceFlights.filter((flight) => FILTER_STATUSES.has(normalizeFlightStatus(flight?.status)))
      : sourceFlights.filter((flight) => normalizeFlightStatus(flight?.status) === flightStatusFilter);
    return sortFlightsByDepartureAsc(filtered);
  }, [flights.data, flightStatusFilter]);

  const markFlightAsCanceled = (flightId) => {
    setCancelledFlightIds?.((previous) => {
      const next = new Set(previous);
      next.add(flightId);
      return next;
    });
    setSimulationPanelData?.((previous) => ({
      ...previous,
      flights: (previous?.flights ?? []).map((flight) =>
        flight.id === flightId
          ? { ...flight, status: "CANCELADO", used: 0 }
          : flight,
      ),
    }));
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

  const ordersFiltered = useMemo(() => filterByText(orders.data ?? [], ["id", "idPedido", "clientId", "idCliente", "origin", "dest", "status", "estado"]), [orders.data, query]);
  const airportsFiltered = useMemo(() => filterByText(airports.data ?? [], ["iata", "city", "continent"]), [airports.data, query]);
  const maletasFiltered = useMemo(() => filterByText(maletas.data ?? [], ["idMaleta", "idPedido", "estado", "ubicacionActual"]), [maletas.data, query]);
  const rutasFiltered = useMemo(() => filterByText(rutas.data ?? [], ["idRuta", "idMaleta", "estado"]), [rutas.data, query]);

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
          />
        )}
      />
    ),
    orders: (
      <TabBody
        {...orders}
        empty="Ejecuta la simulacion para cargar los pedidos de la ventana activa."
        rows={ordersFiltered}
        renderItem={(o, index) => <OrderItem key={o.id ?? o.idPedido ?? index} order={o} />}
      />
    ),
    routes: (
      <TabBody
        {...rutas}
        empty="Ejecuta la simulacion para cargar las rutas de la ventana activa."
        rows={rutasFiltered}
        renderItem={(r, index) => <RouteItem key={r.idRuta ?? r.id ?? index} route={r} />}
      />
    ),
    bags: (
      <TabBody
        {...maletas}
        empty="Ejecuta la simulacion para cargar las maletas de la ventana activa."
        rows={maletasFiltered}
        renderItem={(b, index) => <BagItem key={b.idMaleta ?? b.id ?? index} bag={b} />}
      />
    ),
    airports: (
      <TabBody
        {...airports}
        empty="Ejecuta la simulacion para cargar los aeropuertos del periodo."
        rows={airportsFiltered}
        renderItem={(a, index) => <AirportItem key={a.iata ?? a.idAeropuerto ?? index} apt={a} />}
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

      <div className="p-4 border-b border-slate-800 flex items-center gap-2">
        {activeTab === "flights" ? (
          <div className="flex items-center gap-2 flex-1 min-w-0">
            <div className="relative flex-1 min-w-0">
              <Filter className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <select
                value={flightStatusFilter}
                onChange={(e) => setFlightStatusFilter(e.target.value)}
                aria-label="Filtrar vuelos por estado"
                className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-2 py-1.5 pl-9 pr-8 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                {FLIGHT_STATUS_FILTERS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        ) : (
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={`Buscar ${activeTabLabel.toLowerCase()}...`}
            aria-label={`Buscar ${activeTabLabel.toLowerCase()}`}
            className="w-full bg-surface-2 border border-slate-800 rounded-lg py-1.5 pl-3 pr-3 text-sm text-slate-200 placeholder:text-slate-400 focus:outline-none focus:border-slate-600"
          />
        )}
        <button
          type="button"
          onClick={() => activeSource?.refetch?.()}
          disabled={activeSource?.loading}
          aria-label="Refrescar"
          title="Refrescar"
          className="shrink-0 p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-surface-2 transition-colors disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${activeSource?.loading ? "animate-spin" : ""}`} />
        </button>
      </div>

      <div key={`${activeTab}-${simulationLoaded ? "loaded" : "empty"}`} className="flex-1 overflow-y-auto px-4 py-4 no-scrollbar">
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
  return rows.map(renderItem);
}
