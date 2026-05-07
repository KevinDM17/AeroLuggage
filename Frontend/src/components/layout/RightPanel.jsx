import { useState } from "react";
import { Filter, XCircle, Menu, ArrowRightCircle, MapPin, Globe } from "lucide-react";

const FLIGHTS = [
  { id: "#ABC-123", status: "EN PROGRESO", capacity: 267, maxCapacity: 320, origin: "MAD", originDate: "18 JUN 2026", originTime: "16:30", originTz: "(GMT-4)", dest: "MIA", destDate: "18 JUN 2026", destTime: "18:00", destTz: "(GMT-5)", canCancel: false },
  { id: "#ABC-123", status: "CONFIRMADO", capacity: 120, maxCapacity: 320, origin: "MAD", originDate: "18 JUN 2026", originTime: "16:30", originTz: "(GMT-4)", dest: "MIA", destDate: "18 JUN 2026", destTime: "18:00", destTz: "(GMT-5)", canCancel: true },
  { id: "#HJS-834", status: "CANCELADO", capacity: 0, maxCapacity: 320, origin: "MAD", originDate: "18 JUN 2026", originTime: "16:30", originTz: "(GMT-4)", dest: "MIA", destDate: "18 JUN 2026", destTime: "18:00", destTz: "(GMT-5)", canCancel: false },
  { id: "#ABC-123", status: "PROGRAMADO", capacity: 267, maxCapacity: 320, origin: "MAD", originDate: "18 JUN 2026", originTime: "16:30", originTz: "(GMT-4)", dest: "MIA", destDate: "18 JUN 2026", destTime: "18:00", destTz: "(GMT-5)", canCancel: true },
  { id: "#ABC-123", status: "FINALIZADO", capacity: 312, maxCapacity: 320, origin: "MAD", originDate: "18 JUN 2026", originTime: "16:30", originTz: "(GMT-4)", dest: "MIA", destDate: "18 JUN 2026", destTime: "18:00", destTz: "(GMT-5)", canCancel: false },
];

const ORDERS = [
  { id: "E-0001", totalBags: 6, deliveredBags: 1, bags: ["M-001", "M-002", "M-003", "M-004", "M-005", "M-006"] },
  { id: "E-0002", totalBags: 2, deliveredBags: 0, bags: ["M-007", "M-008"] },
  { id: "E-0003", totalBags: 4, deliveredBags: 0, bags: ["M-009", "M-010", "M-011", "M-012"] },
  { id: "E-0004", totalBags: 1, deliveredBags: 0, bags: ["M-013"] },
  { id: "E-0005", totalBags: 1, deliveredBags: 0, bags: ["M-014"] },
];

const ROUTES_DATA = [
  {
    id: "#PR-001", bag: "M-0001",
    stops: [
      { type: 'node', label: 'LIM', status: 'past' },
      { type: 'edge', label: '#ABC-123 (16:30 18/04/23 - 18:00 18/04/23)' },
      { type: 'node', label: 'BOG', status: 'current' },
      { type: 'edge', label: '#DEF-456 (22:00 18/04/23 - 01:30 19/04/23)' },
      { type: 'node', label: 'MIA', status: 'future' },
      { type: 'edge', label: '#ABC-123 (10:00 19/04/23 - 15:00 19/04/23)' },
      { type: 'node', label: 'JFK', status: 'future' },
    ]
  },
  { id: "#PR-002", bag: "M-0002" },
  { id: "#PR-003", bag: "M-0003" },
  { id: "#PR-004", bag: "M-0004" },
  { id: "#PR-005", bag: "M-0005" },
  { id: "#PR-006", bag: "M-0006" },
];

const BAGS = [
  { id: "M-0001", status: "EN ESPERA", location: "MIA", flight: "#ABC-123", routePlan: "#PR-0001", client: "Nombre Apellido" },
  { id: "M-0002", status: "EN TRASLADO", location: "MIA - GRU", flight: "#DEF-456", routePlan: "#PR-0002", client: "Nombre Apellido" },
  { id: "M-0003", status: "REGISTRADO", location: "MIA - BOG", flight: "#ABC-123", routePlan: "#PR-0001", client: "Nombre Apellido" },
  { id: "M-0004", status: "EN ESPERA", location: "JFK", flight: "--", routePlan: "#PR-0001", client: "Nombre Apellido" },
  { id: "M-0005", status: "EN TRASLADO", location: "JFK - BOG", flight: "#ABC-123", routePlan: "#PR-0001", client: "Nombre Apellido" },
  { id: "M-0006", status: "LLEGÓ", location: "JFK", flight: "--", routePlan: "#PR-0001", client: "Nombre Apellido" },
  { id: "M-0007", status: "ENTREGADO", location: "18/04/2026 18:36", flight: "--", routePlan: "#PR-0001", client: "Nombre Apellido" },
  { id: "M-0008", status: "EN ESPERA", location: "JFK", flight: "#ABC-123", routePlan: "#PR-0001", client: "Nombre Apellido" },
];

const AIRPORTS = [
  { id: "LIM", name: "Lima, Perú", region: "Sudamérica", used: 3400, capacity: 5000, pct: 68, color: "bg-yellow-400" },
  { id: "BOG", name: "Bogotá, Colombia", region: "Sudamérica", used: 600, capacity: 6000, pct: 10, color: "bg-green-500" },
  { id: "MIA", name: "Miami, EEUU", region: "Norteamérica", used: 4950, capacity: 5000, pct: 95, color: "bg-red-500" },
  { id: "MAD", name: "Madrid, España", region: "Europa", used: 2450, capacity: 5000, pct: 49, color: "bg-green-500" },
];

const getStatusColor = (status) => {
  switch (status) {
    case "EN PROGRESO": return "bg-yellow-400 text-yellow-900";
    case "CONFIRMADO": return "bg-indigo-600 text-white";
    case "CANCELADO": return "bg-red-500 text-white";
    case "PROGRAMADO": return "bg-slate-400 text-slate-900";
    case "FINALIZADO": return "bg-green-500 text-white";
    default: return "bg-slate-500 text-white";
  }
};

const getCapacityColor = (status) => {
  switch (status) {
    case "EN PROGRESO": return "bg-yellow-400 text-yellow-900";
    case "CONFIRMADO": return "bg-green-500 text-white";
    case "CANCELADO": return "bg-green-500 text-white";
    case "PROGRAMADO": return "bg-yellow-400 text-yellow-900";
    case "FINALIZADO": return "bg-red-500 text-white";
    default: return "bg-slate-500 text-white";
  }
}

const getBagStatusColor = (status) => {
  switch(status) {
    case "EN ESPERA": return "bg-slate-300 text-slate-800";
    case "EN TRASLADO": return "bg-yellow-400 text-yellow-900";
    case "REGISTRADO": return "bg-indigo-600 text-white";
    case "LLEGÓ": return "bg-cyan-500 text-white";
    case "ENTREGADO": return "bg-green-500 text-white";
    default: return "bg-slate-500 text-white";
  }
}

function FlightItem({ flight }) {
  const [expanded, setExpanded] = useState(false);
  return (
    <div className="flex flex-col border-b border-slate-800/50 pb-4 mb-4 last:border-0 last:pb-0 cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-start mb-2">
        <div>
          <h4 className="font-bold text-lg text-slate-200">{flight.id}</h4>
          <div className={`mt-1 text-xs font-semibold px-2 py-0.5 rounded ${getCapacityColor(flight.status)} inline-block`}>
            Capacidad: {flight.capacity}/{flight.maxCapacity}
          </div>
        </div>
        <span className={`text-[10px] font-bold px-2 py-1 rounded tracking-wider ${getStatusColor(flight.status)}`}>
          {flight.status}
        </span>
      </div>

      {expanded && (
        <div onClick={(e) => e.stopPropagation()} className="cursor-default">
          <div className="flex justify-between mt-3 text-xs text-slate-400">
            <div className="flex flex-col">
              <span className="font-medium text-slate-200">{flight.origin} <span className="font-normal text-slate-400">| {flight.originDate}</span></span>
              <span className="mt-0.5">Salida: {flight.originTime}</span>
              <span className="mt-0.5">Zona horaria: {flight.originTz}</span>
            </div>
            <div className="flex flex-col text-right">
              <span className="font-medium text-slate-200"><span className="font-normal text-slate-400">{flight.destDate} |</span> {flight.dest}</span>
              <span className="mt-0.5">Llegada: {flight.destTime}</span>
              <span className="mt-0.5">Zona horaria: {flight.destTz}</span>
            </div>
          </div>

          {flight.canCancel && (
            <button className="mt-4 flex flex-row items-center justify-center gap-2 bg-[#ff3b30]/10 hover:bg-[#ff3b30]/20 text-[#ff3b30] border border-[#ff3b30]/30 py-1.5 px-3 rounded-lg text-xs font-bold transition-colors w-full">
              <XCircle className="w-3.5 h-3.5" /> CANCELAR VUELO
            </button>
          )}
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
        <span className="text-xs text-slate-400">Cantidad de maletas: {order.totalBags}</span>
      </div>
      {expanded && (
        <div onClick={e => e.stopPropagation()} className="cursor-default">
          {order.bags.length > 0 && (
            <div className="text-xs text-slate-400 mb-2">Entregadas: {order.deliveredBags}/{order.totalBags}</div>
          )}
          {order.bags.length > 0 && (
            <div className="grid grid-cols-3 gap-2 mt-2">
              {order.bags.map(bag => (
                <div key={bag} className="flex items-center gap-1 text-xs text-slate-300">
                  {bag} <ArrowRightCircle className="w-3 h-3 text-slate-500" />
                </div>
              ))}
            </div>
          )}
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
        <div className="flex flex-col pl-2 mt-2 cursor-default" onClick={e => e.stopPropagation()}>
          {route.stops.map((stop, j) => {
            if (stop.type === 'node') {
              let dotColor = 'bg-slate-600';
              if (stop.status === 'past') dotColor = 'bg-[#00ff88]';
              else if (stop.status === 'current') dotColor = 'bg-[#ffd700]';
              
              return (
                <div key={j} className="flex items-center gap-3 relative z-10">
                  <div className={`w-3 h-3 rounded-full ${dotColor}`}></div>
                  <span className="text-xs font-bold text-slate-200">{stop.label}</span>
                </div>
              );
            } else {
              return (
                <div key={j} className="flex items-center gap-3 my-1 relative">
                  <div className="w-[1px] h-6 bg-slate-700 absolute left-[5px] top-[-4px] z-0"></div>
                  <div className="ml-6 text-[9px] text-slate-500 whitespace-nowrap overflow-hidden text-ellipsis">{stop.label}</div>
                </div>
              );
            }
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
        <div className="flex flex-col items-end">
          <span className={`text-[10px] font-bold px-2 py-1 rounded tracking-wider ${getBagStatusColor(bag.status)} mb-1 w-max`}>
            {bag.status}
          </span>
        </div>
      </div>
      {expanded && (
        <div className="cursor-default mt-2" onClick={e => e.stopPropagation()}>
          <div className="text-base font-bold text-slate-300 mb-2">{bag.location}</div>
          <div className="text-xs text-slate-500 flex flex-col gap-0.5">
            <div>Vuelo: <span className="text-slate-300">{bag.flight}</span></div>
            <div>Plan de ruta: <span className="text-slate-300">{bag.routePlan}</span></div>
            <div>Cliente: <span className="text-slate-300">{bag.client}</span></div>
          </div>
        </div>
      )}
    </div>
  );
}

function AirportItem({ apt }) {
  const [expanded, setExpanded] = useState(false);
  return (
    <div className="flex flex-col border-b border-slate-800/50 pb-4 mb-4 last:border-0 last:pb-0 cursor-pointer" onClick={() => setExpanded(!expanded)}>
      <div className="flex justify-between items-center">
        <h4 className="font-bold text-lg text-slate-200">{apt.id}</h4>
        <span className="text-xs text-slate-400">{apt.pct}% Ocupado</span>
      </div>
      
      {expanded && (
        <div className="mt-4 flex justify-between items-start cursor-default" onClick={e => e.stopPropagation()}>
          <div className="flex flex-col gap-1 w-1/2">
            <div className="flex items-center gap-1 text-[10px] text-slate-500"><MapPin className="w-3 h-3"/> {apt.name}</div>
            <div className="flex items-center gap-1 text-[10px] text-slate-500"><Globe className="w-3 h-3"/> {apt.region}</div>
          </div>
          <div className="flex flex-col w-1/2 pt-1">
            <div className="text-[10px] text-slate-500 mb-2">{apt.used} de {apt.capacity} maletas</div>
            <div className="flex items-center gap-2">
              <div className="w-full h-2 bg-[#151b2b] rounded-full overflow-hidden border border-slate-800">
                <div className={`h-full ${apt.color}`} style={{ width: `${apt.pct}%` }}></div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default function RightPanel({ onClose }) {
  const [activeTab, setActiveTab] = useState("Vuelos");
  const tabs = ["Vuelos", "Pedidos", "Rutas", "Maletas", "Aerop."];

  return (
    <div className="w-[360px] shrink-0 bg-[#0B0E14] border-l border-slate-800 h-screen flex flex-col relative z-[9999]">
      <div className="flex items-center border-b border-slate-800">
        <button onClick={onClose} className="p-4 hover:bg-slate-800 text-slate-400 transition-colors">
          <Menu className="w-5 h-5" />
        </button>
        <div className="flex flex-1 overflow-x-auto no-scrollbar">
          {tabs.map((tab) => (
            <button 
              key={tab} 
              onClick={() => setActiveTab(tab)}
              className={`px-3 py-4 text-xs font-semibold whitespace-nowrap ${activeTab === tab ? "border-b-2 border-slate-300 text-slate-200" : "text-slate-500 hover:text-slate-300"}`}
            >
              {tab}
            </button>
          ))}
        </div>
      </div>

      <div className="p-4 border-b border-slate-800 flex items-center gap-3">
        <Filter className="w-5 h-5 text-slate-400" />
        <div className="relative flex-1">
          <input 
            type="text" 
            placeholder={`Buscar ${activeTab.toLowerCase()}...`} 
            className="w-full bg-[#151b2b] border border-slate-800 rounded-lg py-1.5 pl-3 pr-8 text-sm text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-slate-600"
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4 no-scrollbar">
        {/* Vuelos */}
        {activeTab === "Vuelos" && FLIGHTS.map((flight, index) => (
          <FlightItem key={index} flight={flight} />
        ))}

        {/* Pedidos */}
        {activeTab === "Pedidos" && ORDERS.map((order, i) => (
          <OrderItem key={i} order={order} />
        ))}

        {/* Rutas */}
        {activeTab === "Rutas" && ROUTES_DATA.map((route, i) => (
          <RouteItem key={i} route={route} />
        ))}

        {/* Maletas */}
        {activeTab === "Maletas" && BAGS.map((bag, i) => (
          <BagItem key={i} bag={bag} />
        ))}

        {/* Aerop. */}
        {activeTab === "Aerop." && AIRPORTS.map((apt, i) => (
          <AirportItem key={i} apt={apt} />
        ))}
      </div>
    </div>
  );
}
