import { Settings2, Cpu } from "lucide-react";

export default function SimulationConfigPanel() {
  return (
    <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200">
      <div className="flex items-center gap-2 mb-5">
        <div className="bg-purple-100 p-1.5 rounded-lg">
          <Settings2 className="w-5 h-5 text-purple-600" />
        </div>
        <h3 className="font-semibold text-slate-800">Configuración</h3>
      </div>

      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1.5">Algoritmo</label>
          <div className="relative">
            <select className="w-full appearance-none bg-slate-50 border border-slate-200 text-slate-700 py-2 pl-3 pr-10 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 text-sm">
              <option value="aco">ACO (Ant Colony Optimization)</option>
              <option value="hg">Híbrido Genético</option>
              <option value="compare">Comparar Ambos (Dual)</option>
            </select>
            <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center px-3 text-slate-400">
              <Cpu className="w-4 h-4" />
            </div>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Ventana (hrs)</label>
            <input type="number" defaultValue={24} className="w-full bg-slate-50 border border-slate-200 text-slate-700 py-2 px-3 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 text-sm" />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Ciclos/Día</label>
            <input type="number" defaultValue={4} className="w-full bg-slate-50 border border-slate-200 text-slate-700 py-2 px-3 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 text-sm" />
          </div>
        </div>

        <div className="pt-2">
          <label className="flex items-center text-sm text-slate-600 cursor-pointer">
            <input type="checkbox" defaultChecked className="mr-2 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />
            Forzar capacidades de aeropuertos
          </label>
        </div>
      </div>
    </div>
  );
}
