import { MOCK_ROUTES } from "../../data/mockData";
import { Search, Filter, PlaneTakeoff, PlaneLanding } from "lucide-react";
import { cn } from "../../utils/cn";
import { formatTime } from "../../utils/formatting";

export default function FlightsTable() {
  return (
    <div className="bg-white p-6 rounded-b-2xl h-full flex flex-col">
      <div className="flex items-center justify-between mb-6">
        <h3 className="font-semibold text-slate-800 text-lg">Programación de Vuelos</h3>
        <div className="flex gap-3">
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input 
              type="text" 
              placeholder="Buscar vuelo o ruta..." 
              className="pl-9 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20"
            />
          </div>
          <button className="flex items-center gap-2 px-4 py-2 border border-slate-200 rounded-xl text-sm font-medium text-slate-600 hover:bg-slate-50 transition-colors">
            <Filter className="w-4 h-4" /> Filtros
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-auto rounded-xl border border-slate-200">
        <table className="w-full text-left text-sm whitespace-nowrap">
          <thead className="bg-slate-50 border-b border-slate-200 text-slate-600 font-medium sticky top-0 z-10">
            <tr>
              <th className="px-6 py-4">Vuelo ID</th>
              <th className="px-6 py-4">Ruta</th>
              <th className="px-6 py-4">Salida / Llegada</th>
              <th className="px-6 py-4">Ocupación</th>
              <th className="px-6 py-4">Estado</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {MOCK_ROUTES.map((route) => (
              <tr key={route.id} className="hover:bg-slate-50/50 transition-colors">
                <td className="px-6 py-4 font-medium text-slate-900">{route.flightId}</td>
                <td className="px-6 py-4">
                  <div className="flex items-center gap-2 text-slate-600">
                    <span className="font-semibold text-slate-700">{route.origin}</span>
                    <span className="text-slate-400">→</span>
                    <span className="font-semibold text-slate-700">{route.destination}</span>
                  </div>
                </td>
                <td className="px-6 py-4">
                  <div className="flex flex-col gap-1 text-xs">
                    <div className="flex items-center gap-1.5 text-blue-600">
                      <PlaneTakeoff className="w-3.5 h-3.5" /> {formatTime(route.departureTime)}
                    </div>
                    <div className="flex items-center gap-1.5 text-indigo-600">
                      <PlaneLanding className="w-3.5 h-3.5" /> {formatTime(route.arrivalTime)}
                    </div>
                  </div>
                </td>
                <td className="px-6 py-4">
                  <div className="flex items-center gap-3">
                    <div className="w-24 bg-slate-100 rounded-full h-2 overflow-hidden">
                      <div 
                        className={cn("h-2 rounded-full", (route.currentLoad/route.capacity) > 0.9 ? 'bg-red-500' : 'bg-blue-500')}
                        style={{ width: `${Math.min((route.currentLoad / route.capacity) * 100, 100)}%` }}
                      ></div>
                    </div>
                    <span className="text-slate-500 text-xs font-medium">{route.currentLoad} / {route.capacity}</span>
                  </div>
                </td>
                <td className="px-6 py-4">
                  <span className={cn(
                    "px-2.5 py-1 text-xs font-semibold rounded-full border",
                    route.status === 'active' ? "border-blue-200 bg-blue-50 text-blue-700" :
                    route.status === 'delayed' ? "border-red-200 bg-red-50 text-red-700" :
                    route.status === 'completed' ? "border-green-200 bg-green-50 text-green-700" :
                    "border-slate-200 bg-slate-50 text-slate-600"
                  )}>
                    {route.status.toUpperCase()}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
