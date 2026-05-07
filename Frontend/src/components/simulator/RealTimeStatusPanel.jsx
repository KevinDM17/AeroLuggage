import { Activity, Plane, Luggage, Database } from "lucide-react";

export default function RealTimeStatusPanel({ simulationTime, metrics }) {
  // simulationTime is assumed to be an integer (minutes) for this mock
  const formatTime = (mins) => {
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')} hrs`;
  };

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4 mb-6">
      <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-slate-500">Tiempo Simulado</p>
          <p className="text-2xl font-bold text-slate-900 mt-1">{formatTime(simulationTime)}</p>
        </div>
        <div className="w-12 h-12 bg-blue-50 rounded-full flex items-center justify-center">
          <Activity className="w-6 h-6 text-blue-600" />
        </div>
      </div>

      <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-slate-500">Vuelos Activos</p>
          <p className="text-2xl font-bold text-slate-900 mt-1">{metrics.activeFlights}</p>
        </div>
        <div className="w-12 h-12 bg-indigo-50 rounded-full flex items-center justify-center">
          <Plane className="w-6 h-6 text-indigo-600" />
        </div>
      </div>

      <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-slate-500">Maletas Enrutadas</p>
          <p className="text-2xl font-bold text-slate-900 mt-1">{metrics.routedBaggage}</p>
        </div>
        <div className="w-12 h-12 bg-green-50 rounded-full flex items-center justify-center">
          <Luggage className="w-6 h-6 text-green-600" />
        </div>
      </div>

      <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-slate-500">Uso Total Capacidad</p>
          <p className="text-2xl font-bold text-slate-900 mt-1">{metrics.capacityUsage}%</p>
        </div>
        <div className="w-12 h-12 bg-orange-50 rounded-full flex items-center justify-center">
          <Database className="w-6 h-6 text-orange-600" />
        </div>
      </div>
    </div>
  );
}
