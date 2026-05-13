import { useMemo, useState } from "react";
import { Filter, PanelRightClose, MapPin, Globe, Info, ChevronDown, Plane, RefreshCw } from "lucide-react";
import { useFetch } from "../../hooks/useFetch";
import { listFlights } from "../../api/flights";
import { listOrders } from "../../api/orders";
import { listAirports } from "../../api/airports";
import { listMaletas } from "../../api/maletas";
import { listRutas } from "../../api/rutas";
import { LoadingState, EmptyState, ErrorState } from "../ui/States";

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

// Estados del back: EN_ALMACEN | EN_TRASLADO | EN_VUELO | ENTREGADA | EXTRAVIADA
const bagStatusColor = (s) => {
  switch (s) {
    case "EN_ALMACEN":  return "bg-slate-300 text-slate-800";
    case "EN_TRASLADO": return "bg-warning text-yellow-900";
    case "EN_VUELO":    return "bg-info text-slate-900";
    case "ENTREGADA":   return "bg-success text-emerald-900";
    case "EXTRAVIADA":  return "bg-danger text-white";
    default:            return "bg-slate-500 text-white";
  }
};
const bagStatusLabel = (s) => (s ?? "").replace(/_/g, " ");

// Estados del back para rutas: PLANIFICADA | EN_CURSO | COMPLETADA | FALLIDA
const routeStatusColor = (s) => {
  switch (s) {
    case "PLANIFICADA": return "border-info/40 text-info";
    case "EN_CURSO":    return "border-warning/40 text-warning";
    case "COMPLETADA":  return "border-success/40 text-success";
    case "FALLIDA":     return "border-danger/40 text-danger";
    default:            return "border-slate-700 text-slate-400";
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
  const vuelos = route.vuelos ?? [];

  // Convertir vuelos [{ origen, destino }] en una secuencia de nodos (aeropuertos)
  // intercalada con edges (vuelos). El primer nodo es el origen del primer vuelo.
  const stops = useMemo(() => {
    if (vuelos.length === 0) return [];
    const out = [];
    out.push({ type: "node", icao: vuelos[0].aeropuertoOrigen });
    vuelos.forEach((v) => {
      out.push({
        type: "edge",
        label: `${v.codigo} · ${(v.fechaSalida ?? "").slice(11, 16)} → ${(v.fechaLlegada ?? "").slice(11, 16)}`,
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
            Plazo máximo: <span className="text-slate-200">{route.plazoMaximoDias}d</span> · Duración: <span className="text-slate-200">{route.duracion}d</span>
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
          {bag.ubicacionActual && (
            <div className="text-base font-bold text-slate-300 mb-2">{bag.ubicacionActual}</div>
          )}
          <div className="text-xs text-slate-400 flex flex-col gap-0.5">
            <div>Pedido: <span className="text-slate-200">{bag.idPedido}</span></div>
            <div>Registro: <span className="text-slate-200">{(bag.fechaRegistro ?? "").replace("T", " ").slice(0, 16)}</span></div>
            {bag.fechaLlegada && (
              <div>Entregada: <span className="text-slate-200">{bag.fechaLlegada.replace("T", " ").slice(0, 16)}</span></div>
            )}
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
  const maletas  = useFetch(listMaletas);
  const rutas    = useFetch(listRutas);

  const activeSource = {
    "Vuelos":  flights,
    "Pedidos": orders,
    "Rutas":   rutas,
    "Maletas": maletas,
    "Aerop.":  airports,
  }[activeTab];

  const filterByText = (rows, fields) => {
    const q = query.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((r) => fields.some((f) => String(r[f] ?? "").toLowerCase().includes(q)));
  };

  const flightsFiltered  = useMemo(() => filterByText(flights.data  ?? [], ["id", "origin", "dest", "status"]),     [flights.data, query]);
  const ordersFiltered   = useMemo(() => filterByText(orders.data   ?? [], ["id", "clientId", "origin", "dest", "status"]), [orders.data, query]);
  const airportsFiltered = useMemo(() => filterByText(airports.data ?? [], ["iata", "city", "continent"]),          [airports.data, query]);
  const maletasFiltered  = useMemo(() => filterByText(maletas.data  ?? [], ["idMaleta", "idPedido", "estado", "ubicacionActual"]), [maletas.data, query]);
  const rutasFiltered    = useMemo(() => filterByText(rutas.data    ?? [], ["idRuta", "idMaleta", "estado"]),       [rutas.data, query]);

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
        <Filter className="w-5 h-5 text-slate-400 shrink-0" />
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={`Buscar ${activeTab.toLowerCase()}...`}
          aria-label={`Buscar ${activeTab.toLowerCase()}`}
          className="w-full bg-surface-2 border border-slate-800 rounded-lg py-1.5 pl-3 pr-3 text-sm text-slate-200 placeholder:text-slate-400 focus:outline-none focus:border-slate-600"
        />
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

      <div className="flex-1 overflow-y-auto px-4 py-4 no-scrollbar">
        {activeTab === "Vuelos"  && <TabBody {...flights}  empty="No hay vuelos."        rows={flightsFiltered}  renderItem={(f) => <FlightItem  key={f.id}        flight={f} />} />}
        {activeTab === "Pedidos" && <TabBody {...orders}   empty="No hay pedidos."       rows={ordersFiltered}   renderItem={(o) => <OrderItem   key={o.id}        order={o}  />} />}
        {activeTab === "Rutas"   && <TabBody {...rutas}    empty="Sin rutas planificadas. Inicia una simulación para que el Planificador las genere." rows={rutasFiltered} renderItem={(r) => <RouteItem key={r.idRuta} route={r} />} />}
        {activeTab === "Maletas" && <TabBody {...maletas}  empty="No hay maletas."       rows={maletasFiltered}  renderItem={(b) => <BagItem     key={b.idMaleta}  bag={b}    />} />}
        {activeTab === "Aerop."  && <TabBody {...airports} empty="No hay aeropuertos."   rows={airportsFiltered} renderItem={(a) => <AirportItem key={a.iata}      apt={a}    />} />}
      </div>

      <ColorLegend />
    </div>
  );
}

function TabBody({ loading, error, refetch, rows, empty, renderItem }) {
  if (loading) return <LoadingState />;
  if (error)   return <ErrorState error={error} onRetry={refetch} />;
  if (!rows || rows.length === 0) return <EmptyState title="Sin resultados" message={empty} />;
  return rows.map(renderItem);
}
