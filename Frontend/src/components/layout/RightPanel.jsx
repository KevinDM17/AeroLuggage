import { useMemo, useState } from "react";
import { Filter, PanelRightClose, ArrowRightCircle, MapPin, Globe } from "lucide-react";
import { useFetch } from "../../hooks/useFetch";
import { listFlights } from "../../api/flights";
import { listOrders } from "../../api/orders";
import { listAirports } from "../../api/airports";
import { LoadingState, EmptyState, ErrorState } from "../ui/States";

// --- Mock data para tabs sin endpoint todavía (Rutas y Maletas) ---
const ROUTES_DATA = [
  {
    id: "#PR-001", bag: "M-0001",
    stops: [
      { type: "node", label: "LIM", status: "past" },
      { type: "edge", label: "LA201 (16:30 - 18:00)" },
      { type: "node", label: "BOG", status: "current" },
      { type: "edge", label: "AV105 (22:00 - 01:30+1)" },
      { type: "node", label: "MIA", status: "future" },
    ],
  },
  { id: "#PR-002", bag: "M-0002" },
  { id: "#PR-003", bag: "M-0003" },
];

const BAGS = [
  { id: "M-0001", status: "EN ESPERA",   location: "MIA",       flight: "LA201",  routePlan: "#PR-001", client: "Cliente A" },
  { id: "M-0002", status: "EN TRASLADO", location: "MIA - GRU", flight: "AV105",  routePlan: "#PR-002", client: "Cliente A" },
  { id: "M-0003", status: "REGISTRADO", location: "MIA - BOG", flight: "G3102",  routePlan: "#PR-001", client: "Cliente B" },
  { id: "M-0004", status: "LLEGÓ",      location: "JFK",       flight: "--",     routePlan: "#PR-001", client: "Cliente C" },
  { id: "M-0005", status: "ENTREGADO",  location: "18/04 18:36", flight: "--",   routePlan: "#PR-001", client: "Cliente D" },
];

// --- helpers de estilo ---
const flightStatusColor = (s) => {
  switch ((s ?? "").toUpperCase()) {
    case "EN PROGRESO": return "bg-warning text-yellow-900";
    case "CONFIRMADO":  return "bg-indigo-600 text-white";
    case "CANCELADO":   return "bg-danger text-white";
    case "FINALIZADO":  return "bg-success text-emerald-900";
    default:            return "bg-slate-500 text-white";
  }
};
const occupancyColor = (pct) => pct >= 85 ? "bg-danger" : pct >= 60 ? "bg-warning" : "bg-success";
const bagStatusColor = (s) => {
  switch (s) {
    case "EN ESPERA":   return "bg-slate-300 text-slate-800";
    case "EN TRASLADO": return "bg-warning text-yellow-900";
    case "REGISTRADO":  return "bg-indigo-600 text-white";
    case "LLEGÓ":       return "bg-cyan-500 text-white";
    case "ENTREGADO":   return "bg-success text-emerald-900";
    default:            return "bg-slate-500 text-white";
  }
};

// --- items ---
function FlightItem({ flight }) {
  const [expanded, setExpanded] = useState(false);
  const pct = Math.round((flight.used / flight.capacity) * 100);
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
          {(flight.status ?? "").toUpperCase()}
        </span>
      </div>
      {expanded && (
        <div onClick={(e) => e.stopPropagation()} className="cursor-default text-xs text-slate-400">
          <div className="flex justify-between mt-2">
            <div className="flex flex-col">
              <span className="font-medium text-slate-200">{flight.origin}</span>
              <span className="mt-0.5">Salida: {flight.depTime}</span>
            </div>
            <div className="flex flex-col text-right">
              <span className="font-medium text-slate-200">{flight.dest}</span>
              <span className="mt-0.5">Llegada: {flight.arrTime}</span>
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
          <div>Ruta: <span className="text-slate-200">{order.origin} ➔ {order.dest}</span></div>
          <div>Registrado: <span className="text-slate-200">{order.date} {order.time}</span></div>
          <div>Estado: <span className="text-slate-200">{order.status}</span></div>
        </div>
      )}
    </div>
  );
}

function RouteItem({ route }) {
  const [expanded, setExpanded] = useState(false);
  return (
    <div className="flex flex-col border-b border-slate-800/50 pb-4 mb-4 last:border-0 last:pb-0 cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-center mb-4">
        <h4 className="font-bold text-lg text-slate-200">{route.id}</h4>
        <span className="text-xs text-slate-400">Maleta: {route.bag}</span>
      </div>
      {expanded && route.stops && (
        <div className="flex flex-col pl-2 mt-2 cursor-default" onClick={(e) => e.stopPropagation()}>
          {route.stops.map((stop, j) => {
            if (stop.type === "node") {
              let dotColor = "bg-slate-600";
              if (stop.status === "past")     dotColor = "bg-success";
              else if (stop.status === "current") dotColor = "bg-warning";
              return (
                <div key={j} className="flex items-center gap-3 relative z-10">
                  <div className={`w-3 h-3 rounded-full ${dotColor}`}></div>
                  <span className="text-xs font-bold text-slate-200">{stop.label}</span>
                </div>
              );
            }
            return (
              <div key={j} className="flex items-center gap-3 my-1 relative">
                <div className="w-[1px] h-6 bg-slate-700 absolute left-[5px] top-[-4px] z-0"></div>
                <div className="ml-6 text-[10px] text-slate-400 whitespace-nowrap overflow-hidden text-ellipsis">{stop.label}</div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function BagItem({ bag }) {
  const [expanded, setExpanded] = useState(false);
  return (
    <div className="flex flex-col border-b border-slate-800/50 pb-4 mb-4 last:border-0 last:pb-0 cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-start mb-2">
        <h4 className="font-bold text-lg text-slate-200">{bag.id}</h4>
        <span className={`text-[10px] font-bold px-2 py-1 rounded tracking-wider ${bagStatusColor(bag.status)}`}>{bag.status}</span>
      </div>
      {expanded && (
        <div className="cursor-default mt-2" onClick={(e) => e.stopPropagation()}>
          <div className="text-base font-bold text-slate-300 mb-2">{bag.location}</div>
          <div className="text-xs text-slate-400 flex flex-col gap-0.5">
            <div>Vuelo: <span className="text-slate-200">{bag.flight}</span></div>
            <div>Plan de ruta: <span className="text-slate-200">{bag.routePlan}</span></div>
            <div>Cliente: <span className="text-slate-200">{bag.client}</span></div>
          </div>
        </div>
      )}
    </div>
  );
}

function AirportItem({ apt }) {
  const [expanded, setExpanded] = useState(false);
  const pct = Math.round((apt.used / apt.capacity) * 100);
  return (
    <div className="flex flex-col border-b border-slate-800/50 pb-4 mb-4 last:border-0 last:pb-0 cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-center">
        <h4 className="font-bold text-lg text-slate-200">{apt.iata}</h4>
        <span className="text-xs text-slate-400">{pct}% Ocupado</span>
      </div>
      {expanded && (
        <div className="mt-4 flex justify-between items-start cursor-default" onClick={(e) => e.stopPropagation()}>
          <div className="flex flex-col gap-1 w-1/2">
            <div className="flex items-center gap-1 text-[10px] text-slate-400"><MapPin className="w-3 h-3"/> {apt.city}</div>
            <div className="flex items-center gap-1 text-[10px] text-slate-400"><Globe className="w-3 h-3"/> {apt.continent}</div>
          </div>
          <div className="flex flex-col w-1/2 pt-1">
            <div className="text-[10px] text-slate-400 mb-2">{apt.used} de {apt.capacity} maletas</div>
            <div className="w-full h-2 bg-surface-2 rounded-full overflow-hidden border border-slate-800">
              <div className={`h-full ${occupancyColor(pct)}`} style={{ width: `${pct}%` }}></div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// --- panel principal ---
export default function RightPanel({ onClose }) {
  const [activeTab, setActiveTab] = useState("Vuelos");
  const [query, setQuery] = useState("");
  const tabs = ["Vuelos", "Pedidos", "Rutas", "Maletas", "Aerop."];

  // Reset query al cambiar tab
  const onTabChange = (t) => { setActiveTab(t); setQuery(""); };

  const flights  = useFetch(listFlights);
  const orders   = useFetch(listOrders);
  const airports = useFetch(listAirports);

  const filterByText = (rows, fields) => {
    const q = query.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((r) => fields.some((f) => String(r[f] ?? "").toLowerCase().includes(q)));
  };

  const flightsFiltered = useMemo(() => filterByText(flights.data ?? [], ["id", "origin", "dest", "status"]), [flights.data, query]);
  const ordersFiltered  = useMemo(() => filterByText(orders.data  ?? [], ["id", "clientId", "origin", "dest", "status"]), [orders.data, query]);
  const airportsFiltered = useMemo(() => filterByText(airports.data ?? [], ["iata", "city", "continent"]), [airports.data, query]);
  const routesFiltered = useMemo(() => filterByText(ROUTES_DATA, ["id", "bag"]), [query]);
  const bagsFiltered   = useMemo(() => filterByText(BAGS, ["id", "location", "client", "status"]), [query]);

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

      <div className="p-4 border-b border-slate-800 flex items-center gap-3">
        <Filter className="w-5 h-5 text-slate-400" />
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={`Buscar ${activeTab.toLowerCase()}...`}
          aria-label={`Buscar ${activeTab.toLowerCase()}`}
          className="w-full bg-surface-2 border border-slate-800 rounded-lg py-1.5 pl-3 pr-3 text-sm text-slate-200 placeholder:text-slate-400 focus:outline-none focus:border-slate-600"
        />
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4 no-scrollbar">
        {activeTab === "Vuelos"  && <TabBody {...flights}  empty="No hay vuelos." rows={flightsFiltered}  renderItem={(f) => <FlightItem key={f.id} flight={f} />} />}
        {activeTab === "Pedidos" && <TabBody {...orders}   empty="No hay pedidos." rows={ordersFiltered}  renderItem={(o) => <OrderItem  key={o.id} order={o}  />} />}
        {activeTab === "Aerop."  && <TabBody {...airports} empty="No hay aeropuertos." rows={airportsFiltered} renderItem={(a) => <AirportItem key={a.iata} apt={a} />} />}
        {activeTab === "Rutas"   && routesFiltered.map((r) => <RouteItem key={r.id} route={r} />)}
        {activeTab === "Maletas" && bagsFiltered.map((b) => <BagItem key={b.id} bag={b} />)}
        {activeTab === "Rutas"   && routesFiltered.length === 0 && <EmptyState title="Sin resultados" message={`Nada coincide con "${query}".`} />}
        {activeTab === "Maletas" && bagsFiltered.length === 0   && <EmptyState title="Sin resultados" message={`Nada coincide con "${query}".`} />}
      </div>
    </div>
  );
}

function TabBody({ loading, error, refetch, rows, empty, renderItem }) {
  if (loading) return <LoadingState />;
  if (error)   return <ErrorState error={error} onRetry={refetch} />;
  if (!rows || rows.length === 0) return <EmptyState title="Sin resultados" message={empty} />;
  return rows.map(renderItem);
}
