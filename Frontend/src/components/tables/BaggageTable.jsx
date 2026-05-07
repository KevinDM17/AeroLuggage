import { MOCK_SUITCASES } from "../../data/mockData";
import { Search, Filter, AlertCircle, CheckCircle2, Clock } from "lucide-react";
import { cn } from "../../utils/cn";

export default function BaggageTable() {
  return (
    <div className="bg-white p-6 rounded-b-2xl h-full flex flex-col">
      <div className="flex items-center justify-between mb-6">
        <h3 className="font-semibold text-slate-800 text-lg">Estado de Maletas</h3>
        <div className="flex gap-3">
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input 
              type="text" 
              placeholder="Buscar por ID..." 
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
              <th className="px-6 py-4">ID Maleta</th>
              <th className="px-6 py-4">Origen</th>
              <th className="px-6 py-4">Destino</th>
              <th className="px-6 py-4">Ruta Asignada</th>
              <th className="px-6 py-4">Prioridad</th>
              <th className="px-6 py-4">Estado</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {MOCK_SUITCASES.map((bag) => (
              <tr key={bag.id} className="hover:bg-slate-50/50 transition-colors">
                <td className="px-6 py-4 font-medium text-slate-900">{bag.id}</td>
                <td className="px-6 py-4 text-slate-600">{bag.origin}</td>
                <td className="px-6 py-4 text-slate-600">{bag.destination}</td>
                <td className="px-6 py-4 text-slate-600">{bag.assignedRoute}</td>
                <td className="px-6 py-4">
                  <span className={cn(
                    "px-2.5 py-1 text-xs font-semibold rounded-full",
                    bag.priority === 'high' ? "bg-orange-100 text-orange-700" :
                    bag.priority === 'low' ? "bg-slate-100 text-slate-600" :
                    "bg-blue-100 text-blue-700"
                  )}>
                    {bag.priority}
                  </span>
                </td>
                <td className="px-6 py-4">
                  <div className="flex items-center gap-2">
                    {bag.status === 'routed' && <CheckCircle2 className="w-4 h-4 text-green-500" />}
                    {bag.status === 'delayed' && <AlertCircle className="w-4 h-4 text-red-500" />}
                    {bag.status === 'pending' && <Clock className="w-4 h-4 text-amber-500" />}
                    {bag.status === 'unrouted' && <AlertCircle className="w-4 h-4 text-slate-400" />}
                    <span className={cn(
                      "font-medium",
                      bag.status === 'routed' && "text-green-700",
                      bag.status === 'delayed' && "text-red-700",
                      bag.status === 'pending' && "text-amber-700",
                      bag.status === 'unrouted' && "text-slate-500",
                    )}>
                      {bag.status}
                    </span>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
