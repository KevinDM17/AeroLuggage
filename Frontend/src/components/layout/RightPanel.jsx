import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { Filter, PanelRightClose, MapPin, Globe, Info, ChevronDown, Plane, RefreshCw } from "lucide-react";
import { useFetch } from "../../hooks/useFetch";
import { listFlights } from "../../api/flights";
import { listOrders } from "../../api/orders";
import { listAirports } from "../../api/airports";
import { listMaletas } from "../../api/maletas";
import { listRutas } from "../../api/rutas";
import { LoadingState, EmptyState, ErrorState } from "../ui/States";

const STATUS_FILTER_TABS = new Set(["Vuelos", "Pedidos", "Rutas", "Maletas"]);
const ESTIMATED_ROW_HEIGHT = 96;
const VIRTUAL_OVERSCAN = 4;

const flightStatusColor = (s) => {
  switch ((s ?? "").toUpperCase().replace(/_/g, " ")) {
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

function FlightItem({ flight }) {
  const [expanded, setExpanded] = useState(false);
  const pct = flight.capacity > 0 ? Math.round((flight.used / flight.capacity) * 100) : 0;
  return (
    <div className="flex flex-col border-b border-slate-800/50 pb-4 mb-4 last:border-0 last:pb-0 cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-start mb-2">
        <div>
          <h4 className="font-bold text-lg text-slate-200">{flight.id}</h4>
          <div className={`mt-1 text-xs font-semibold px-2 py-0.5 rounded ${occupancyColor(pct)} inline-block text-slate-900`}>
            Capacidad: {flight.used}/{flight.capacity}
          </div>
        </div>
        <span className={`text-[10px] font-bold px-2 py-1 rounded tracking-wider ${flightStatusColor(flight.status === "En progreso" ? "EN PROGRESO" : flight.status?.toUpperCase())}`}>
          {(flight.status ?? "").toUpperCase().replace(/_/g, " ")}
        </span>
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
  return (
    <div className="flex flex-col border-b border-slate-800/50 pb-4 mb-4 last:border-0 last:pb-0 cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-center mb-2">
        <h4 className="font-bold text-lg text-slate-200">{order.id}</h4>
        <span className="text-xs text-slate-400">{order.bags} maleta{order.bags !== 1 ? "s" : ""}</span>
      </div>
      {expanded && (
        <div onClick={(e) => e.stopPropagation()} className="cursor-default text-xs text-slate-400 space-y-1">
          <div>Cliente: <span className="text-slate-200">{order.clientId}</span></div>
          <div>Ruta: <span className="text-slate-200">{order.origin} {"->"} {order.dest}</span></div>
          <div>Registrado: <span className="text-slate-200">{order.date} {order.time}</span></div>
          <div>Estado: <span className="text-slate-200">{order.status}</span></div>
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

function normalizeStatus(status) {
  return String(status ?? "")
    .trim()
    .toUpperCase()
    .replace(/_/g, " ")
    .replace(/\s+/g, "_");
}

function statusLabel(status) {
  const label = String(status ?? "").trim();
  if (!label) return "";
  return label.replace(/_/g, " ");
}

function getStatusValue(row, activeTab) {
  if (!STATUS_FILTER_TABS.has(activeTab)) return "";
  return activeTab === "Vuelos" || activeTab === "Pedidos" ? row?.status : row?.estado;
}

function buildStatusOptions(rows = [], activeTab) {
  if (!STATUS_FILTER_TABS.has(activeTab)) {
    return [{ value: "ALL", label: "Todos" }];
  }

  const seen = new Map();
  rows.forEach((row) => {
    const raw = getStatusValue(row, activeTab);
    const value = normalizeStatus(raw);
    if (!value || seen.has(value)) return;
    seen.set(value, statusLabel(raw));
  });

  return [
    { value: "ALL", label: "Todos" },
    ...Array.from(seen.entries())
      .sort((left, right) => left[1].localeCompare(right[1]))
      .map(([value, label]) => ({ value, label })),
  ];
}

export default function RightPanel({ onClose, simulationPanelData }) {
  const [activeTab, setActiveTab] = useState("Vuelos");
  const [query, setQuery] = useState("");
  const [statusFilters, setStatusFilters] = useState({});
  const location = useLocation();
  const tabs = ["Vuelos", "Pedidos", "Rutas", "Maletas", "Aerop."];
  const isSimulator = location.pathname === "/" || location.pathname.startsWith("/simulator");

  const onTabChange = (tab) => {
    setActiveTab(tab);
    setQuery("");
    setStatusFilters((current) => ({ ...current, [tab]: "ALL" }));
  };

  const flightsFetch = useFetch(() => (isSimulator ? Promise.resolve([]) : listFlights()), [isSimulator]);
  const ordersFetch = useFetch(() => (isSimulator ? Promise.resolve([]) : listOrders()), [isSimulator]);
  const airportsFetch = useFetch(() => (isSimulator ? Promise.resolve([]) : listAirports()), [isSimulator]);
  const maletasFetch = useFetch(() => (isSimulator ? Promise.resolve([]) : listMaletas()), [isSimulator]);
  const rutasFetch = useFetch(() => (isSimulator ? Promise.resolve([]) : listRutas()), [isSimulator]);

  const flights = isSimulator ? createStaticSource(simulationPanelData?.flights ?? []) : flightsFetch;
  const airports = isSimulator ? createStaticSource(simulationPanelData?.airports ?? []) : airportsFetch;
  const orders = isSimulator ? createStaticSource(simulationPanelData?.orders ?? []) : ordersFetch;
  const maletas = isSimulator ? createStaticSource(simulationPanelData?.bags ?? []) : maletasFetch;
  const rutas = isSimulator ? createStaticSource(simulationPanelData?.routes ?? []) : rutasFetch;

  const activeSource = {
    Vuelos: flights,
    Pedidos: orders,
    Rutas: rutas,
    Maletas: maletas,
    "Aerop.": airports,
  }[activeTab];

  const filterByText = (rows, fields) => {
    const q = query.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((r) => fields.some((f) => String(r[f] ?? "").toLowerCase().includes(q)));
  };

  const statusFilter = statusFilters[activeTab] ?? "ALL";
  const supportsStatusFilter = STATUS_FILTER_TABS.has(activeTab);
  const filterByStatus = (rows) => {
    if (!supportsStatusFilter || statusFilter === "ALL") return rows;
    return rows.filter((row) => normalizeStatus(getStatusValue(row, activeTab)) === statusFilter);
  };

  const flightStatusOptions = useMemo(
    () => buildStatusOptions(flights.data ?? [], "Vuelos"),
    [flights.data],
  );
  const orderStatusOptions = useMemo(
    () => buildStatusOptions(orders.data ?? [], "Pedidos"),
    [orders.data],
  );
  const routeStatusOptions = useMemo(
    () => buildStatusOptions(rutas.data ?? [], "Rutas"),
    [rutas.data],
  );
  const bagStatusOptions = useMemo(
    () => buildStatusOptions(maletas.data ?? [], "Maletas"),
    [maletas.data],
  );

  const statusOptionsByTab = {
    Vuelos: flightStatusOptions,
    Pedidos: orderStatusOptions,
    Rutas: routeStatusOptions,
    Maletas: bagStatusOptions,
  };
  const statusOptions = statusOptionsByTab[activeTab] ?? [{ value: "ALL", label: "Todos" }];

  useEffect(() => {
    if (!supportsStatusFilter || statusFilter === "ALL") return;
    if (statusOptions.some((option) => option.value === statusFilter)) return;
    setStatusFilters((current) => ({ ...current, [activeTab]: "ALL" }));
  }, [activeTab, statusFilter, statusOptions, supportsStatusFilter]);

  const visibleFlights = useMemo(() => {
    const byText = filterByText(flights.data ?? [], ["id", "origin", "dest", "status"]);
    return sortFlightsByDepartureAsc(filterByStatus(byText));
  }, [flights.data, query, statusFilter, activeTab]);
  const ordersFiltered = useMemo(() => {
    const byText = filterByText(orders.data ?? [], ["id", "clientId", "origin", "dest", "status"]);
    return filterByStatus(byText);
  }, [orders.data, query, statusFilter, activeTab]);
  const airportsFiltered = useMemo(() => filterByText(airports.data ?? [], ["iata", "city", "continent"]), [airports.data, query]);
  const maletasFiltered = useMemo(() => {
    const byText = filterByText(maletas.data ?? [], ["idMaleta", "idPedido", "estado", "ubicacionActual"]);
    return filterByStatus(byText);
  }, [maletas.data, query, statusFilter, activeTab]);
  const rutasFiltered = useMemo(() => {
    const byText = filterByText(rutas.data ?? [], ["idRuta", "idMaleta", "estado"]);
    return filterByStatus(byText);
  }, [rutas.data, query, statusFilter, activeTab]);

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
          {tabs.map((tab) => (
            <button
              key={tab}
              type="button"
              onClick={() => onTabChange(tab)}
              className={`px-3 py-4 text-xs font-semibold whitespace-nowrap ${activeTab === tab ? "border-b-2 border-slate-300 text-slate-200" : "text-slate-400 hover:text-slate-200"}`}
            >
              {tab}
            </button>
          ))}
        </div>
      </div>

      <div className="p-4 border-b border-slate-800 flex items-center gap-2">
        <div className="flex flex-1 min-w-0 gap-2">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={`Buscar ${activeTab.toLowerCase()}...`}
            aria-label={`Buscar ${activeTab.toLowerCase()}`}
            className="w-full bg-surface-2 border border-slate-800 rounded-lg py-1.5 pl-3 pr-3 text-sm text-slate-200 placeholder:text-slate-400 focus:outline-none focus:border-slate-600"
          />
          {supportsStatusFilter && (
            <div className="relative w-36 shrink-0">
              <Filter className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilters((current) => ({
                  ...current,
                  [activeTab]: e.target.value,
                }))}
                aria-label={`Filtrar ${activeTab.toLowerCase()} por estado`}
                className="w-full appearance-none rounded-lg border border-slate-800 bg-surface-2 py-1.5 pl-9 pr-8 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                {statusOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
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

      <div className="flex-1 min-h-0">
        {activeTab === "Vuelos" && <TabBody {...flights} empty="Ejecuta la simulacion para cargar los vuelos del periodo." rows={visibleFlights} itemKey={(f) => f.id} renderItem={(f) => <FlightItem flight={f} />} resetKey={`Vuelos:${query}:${statusFilter}`} />}
        {activeTab === "Pedidos" && <TabBody {...orders} empty="Ejecuta la simulacion para cargar los pedidos de la ventana activa." rows={ordersFiltered} itemKey={(o) => o.id} renderItem={(o) => <OrderItem order={o} />} resetKey={`Pedidos:${query}:${statusFilter}`} />}
        {activeTab === "Rutas" && <TabBody {...rutas} empty="Ejecuta la simulacion para cargar las rutas de la ventana activa." rows={rutasFiltered} itemKey={(r) => r.idRuta} renderItem={(r) => <RouteItem route={r} />} resetKey={`Rutas:${query}:${statusFilter}`} />}
        {activeTab === "Maletas" && <TabBody {...maletas} empty="Ejecuta la simulacion para cargar las maletas de la ventana activa." rows={maletasFiltered} itemKey={(b) => b.idMaleta} renderItem={(b) => <BagItem bag={b} />} resetKey={`Maletas:${query}:${statusFilter}`} />}
        {activeTab === "Aerop." && <TabBody {...airports} empty="Ejecuta la simulacion para cargar los aeropuertos del periodo." rows={airportsFiltered} itemKey={(a) => a.iata} renderItem={(a) => <AirportItem apt={a} />} resetKey={`Aerop.:${query}`} />}
      </div>

      <ColorLegend />
    </div>
  );
}

function TabBody({ loading, error, refetch, rows, empty, renderItem, itemKey, resetKey }) {
  if (loading) {
    return <div className="h-full overflow-y-auto px-4 py-4 no-scrollbar"><LoadingState /></div>;
  }
  if (error) {
    return <div className="h-full overflow-y-auto px-4 py-4 no-scrollbar"><ErrorState error={error} onRetry={refetch} /></div>;
  }
  if (!rows || rows.length === 0) {
    return <div className="h-full overflow-y-auto px-4 py-4 no-scrollbar"><EmptyState title="Sin resultados" message={empty} /></div>;
  }
  return (
    <VirtualizedList
      rows={rows}
      itemKey={itemKey}
      renderItem={renderItem}
      resetKey={resetKey}
    />
  );
}

function VirtualizedList({ rows, itemKey, renderItem, resetKey }) {
  const containerRef = useRef(null);
  const heightsRef = useRef(new Map());
  const [scrollTop, setScrollTop] = useState(0);
  const [viewportHeight, setViewportHeight] = useState(0);
  const [measureVersion, setMeasureVersion] = useState(0);

  const getKey = useCallback(
    (row, index) => String(itemKey?.(row, index) ?? index),
    [itemKey],
  );

  const onMeasure = useCallback((key, height) => {
    if (!Number.isFinite(height) || height <= 0) return;
    const current = heightsRef.current.get(key);
    if (current && Math.abs(current - height) < 1) return;
    heightsRef.current.set(key, height);
    setMeasureVersion((version) => version + 1);
  }, []);

  useEffect(() => {
    const node = containerRef.current;
    if (!node) return undefined;

    const updateHeight = () => setViewportHeight(node.clientHeight);
    updateHeight();

    if (typeof ResizeObserver === "undefined") {
      window.addEventListener("resize", updateHeight);
      return () => window.removeEventListener("resize", updateHeight);
    }

    const observer = new ResizeObserver(updateHeight);
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const node = containerRef.current;
    if (!node) return;
    node.scrollTop = 0;
    setScrollTop(0);
  }, [resetKey]);

  const layout = useMemo(() => {
    const positions = [];
    let totalHeight = 0;
    rows.forEach((row, index) => {
      positions[index] = totalHeight;
      totalHeight += heightsRef.current.get(getKey(row, index)) ?? ESTIMATED_ROW_HEIGHT;
    });
    return { positions, totalHeight };
  }, [rows, getKey, measureVersion]);

  const visibleRange = useMemo(() => {
    if (rows.length === 0) return { start: 0, end: -1 };

    let low = 0;
    let high = rows.length - 1;
    let start = 0;
    while (low <= high) {
      const mid = Math.floor((low + high) / 2);
      const row = rows[mid];
      const rowBottom = layout.positions[mid]
        + (heightsRef.current.get(getKey(row, mid)) ?? ESTIMATED_ROW_HEIGHT);
      if (rowBottom < scrollTop) {
        low = mid + 1;
      } else {
        start = mid;
        high = mid - 1;
      }
    }

    const viewportBottom = scrollTop + viewportHeight;
    let end = start;
    while (end < rows.length && layout.positions[end] <= viewportBottom) {
      end += 1;
    }

    return {
      start: Math.max(0, start - VIRTUAL_OVERSCAN),
      end: Math.min(rows.length - 1, end + VIRTUAL_OVERSCAN),
    };
  }, [rows, layout, scrollTop, viewportHeight, getKey]);

  const visibleRows = [];
  for (let index = visibleRange.start; index <= visibleRange.end; index += 1) {
    const row = rows[index];
    if (!row) continue;
    const key = getKey(row, index);
    visibleRows.push(
      <MeasuredVirtualRow
        key={key}
        itemKey={key}
        top={layout.positions[index]}
        onMeasure={onMeasure}
      >
        {renderItem(row, index)}
      </MeasuredVirtualRow>,
    );
  }

  return (
    <div
      ref={containerRef}
      onScroll={(event) => setScrollTop(event.currentTarget.scrollTop)}
      className="h-full overflow-y-auto px-4 py-4 no-scrollbar"
    >
      <div className="relative" style={{ height: layout.totalHeight }}>
        {visibleRows}
      </div>
    </div>
  );
}

function MeasuredVirtualRow({ itemKey, top, onMeasure, children }) {
  const rowRef = useRef(null);

  useEffect(() => {
    const node = rowRef.current;
    if (!node) return undefined;

    const report = () => {
      const child = node.firstElementChild;
      if (!child) {
        onMeasure(itemKey, node.getBoundingClientRect().height);
        return;
      }
      const style = window.getComputedStyle(child);
      const marginY = parseFloat(style.marginTop || "0") + parseFloat(style.marginBottom || "0");
      onMeasure(itemKey, child.getBoundingClientRect().height + marginY);
    };
    report();

    if (typeof ResizeObserver === "undefined") {
      window.addEventListener("resize", report);
      return () => window.removeEventListener("resize", report);
    }

    const observer = new ResizeObserver(report);
    observer.observe(node);
    return () => observer.disconnect();
  }, [itemKey, onMeasure]);

  return (
    <div
      ref={rowRef}
      className="absolute left-0 right-0"
      style={{ transform: `translateY(${top}px)` }}
    >
      {children}
    </div>
  );
}
