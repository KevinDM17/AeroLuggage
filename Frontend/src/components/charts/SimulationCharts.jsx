import { ALGORITHM_COMPARATORS } from "../../data/mockData";
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';

export default function SimulationCharts() {
  return (
    <div className="bg-white p-6 rounded-b-2xl h-full flex flex-col gap-6 overflow-y-auto">
      <div className="grid grid-cols-2 gap-6">
        
        {/* Fitness Over Time */}
        <div className="border border-slate-200 rounded-2xl p-5 shadow-sm">
          <h3 className="font-semibold text-slate-800 mb-6">Fitness (Penalización Total)</h3>
          <div className="h-[300px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={ALGORITHM_COMPARATORS} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorAco" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.8}/>
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="colorHg" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.8}/>
                    <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                <XAxis dataKey="time" axisLine={false} tickLine={false} tick={{fill: '#64748b', fontSize: 12}} dy={10} />
                <YAxis axisLine={false} tickLine={false} tick={{fill: '#64748b', fontSize: 12}} />
                <Tooltip 
                  contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                />
                <Legend iconType="circle" wrapperStyle={{ paddingTop: '20px' }} />
                <Area type="monotone" dataKey="acoFitness" name="ACO" stroke="#3b82f6" strokeWidth={3} fillOpacity={1} fill="url(#colorAco)" />
                <Area type="monotone" dataKey="hgFitness" name="Híbrido Genético" stroke="#8b5cf6" strokeWidth={3} fillOpacity={1} fill="url(#colorHg)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
          <p className="text-xs text-slate-500 mt-4 text-center">Menor fitness indica mejor rendimiento (menos penalizaciones).</p>
        </div>

        {/* Routed Baggage */}
        <div className="border border-slate-200 rounded-2xl p-5 shadow-sm">
          <h3 className="font-semibold text-slate-800 mb-6">Maletas Enrutadas</h3>
          <div className="h-[300px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={ALGORITHM_COMPARATORS} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorAcoRouted" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#22c55e" stopOpacity={0.8}/>
                    <stop offset="95%" stopColor="#22c55e" stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="colorHgRouted" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#f59e0b" stopOpacity={0.8}/>
                    <stop offset="95%" stopColor="#f59e0b" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                <XAxis dataKey="time" axisLine={false} tickLine={false} tick={{fill: '#64748b', fontSize: 12}} dy={10} />
                <YAxis axisLine={false} tickLine={false} tick={{fill: '#64748b', fontSize: 12}} />
                <Tooltip 
                  contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                />
                <Legend iconType="circle" wrapperStyle={{ paddingTop: '20px' }} />
                <Area type="monotone" dataKey="acoRouted" name="ACO" stroke="#22c55e" strokeWidth={3} fillOpacity={1} fill="url(#colorAcoRouted)" />
                <Area type="monotone" dataKey="hgRouted" name="Híbrido Genético" stroke="#f59e0b" strokeWidth={3} fillOpacity={1} fill="url(#colorHgRouted)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
          <p className="text-xs text-slate-500 mt-4 text-center">Cantidad acumulada de maletas con ruta asignada exitosamente.</p>
        </div>

      </div>
    </div>
  );
}
