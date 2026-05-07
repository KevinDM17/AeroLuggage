import { useState } from "react";
import { Edit2, Plus, UploadCloud, PieChart, Clock } from "lucide-react";

const FLIGHTS_DATA = [
  { id: "LA201", route: "LIM ➔ MIA", depTime: "10:00 (GMT -5)", arrTime: "16:30 (GMT -5)", status: "Finalizado", used: 280, capacity: 300, pct: 93, color: "bg-[#ff3b30]" },
  { id: "AV105", route: "BOG ➔ MAD", depTime: "14:00 (GMT -5)", arrTime: "06:00 (GMT +1)", status: "Cancelado", used: 395, capacity: 400, pct: 99, color: "bg-[#ff3b30]" },
  { id: "G3102", route: "GRU ➔ LIM", depTime: "08:00 (GMT -3)", arrTime: "11:30 (GMT -5)", status: "En progreso", used: 150, capacity: 250, pct: 60, color: "bg-[#00ff88]" },
  { id: "AA908", route: "MIA ➔ MAD", depTime: "18:00 (GMT -5)", arrTime: "08:30 (GMT +1)", status: "Confirmado", used: 300, capacity: 350, pct: 86, color: "bg-[#ffd700]" },
];

function getStatusBadge(status) {
  if (status === 'Finalizado' || status === 'Confirmado') return 'bg-[#00ff88]/10 text-[#00ff88] border-[#00ff88]/20';
  if (status === 'Cancelado') return 'bg-[#ff3b30]/10 text-[#ff3b30] border-[#ff3b30]/20';
  if (status === 'En progreso') return 'bg-blue-500/10 text-blue-400 border-blue-500/20';
  return 'bg-slate-800 text-slate-300 border-slate-700';
}

export default function FlightsPage() {
  const [showAddModal, setShowAddModal] = useState(false);
  const [showUploadModal, setShowUploadModal] = useState(false);

  return (
    <div className="flex-1 bg-[#050810] flex flex-col min-h-0 overflow-y-auto w-full h-full p-8 text-slate-200">
      <div className="flex justify-between items-start mb-8">
        <div>
          <h1 className="text-4xl font-extrabold text-white mb-2">Tabla de Vuelos</h1>
          <p className="text-slate-400 text-lg">Monitorea y programa la capacidad de los vuelos operativos.</p>
        </div>
        <div className="flex items-center gap-3">
          <button 
             onClick={() => setShowUploadModal(true)}
             className="bg-white hover:bg-slate-100 text-slate-900 px-5 py-2.5 rounded-lg flex items-center gap-2 font-medium transition-colors border border-transparent shadow-sm"
          >
            <UploadCloud className="w-5 h-5 text-slate-500" /> Carga Masiva
          </button>
          <button 
             onClick={() => setShowAddModal(true)}
             className="bg-blue-600 hover:bg-blue-500 text-white px-5 py-2.5 rounded-lg flex items-center gap-2 font-medium transition-colors"
          >
            <Plus className="w-5 h-5" /> Programar Vuelo
          </button>
        </div>
      </div>

      <div className="bg-[#0B0E14] border border-slate-800 rounded-xl overflow-hidden">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="border-b border-slate-800 text-slate-400 text-sm">
              <th className="py-4 px-6 font-medium">Vuelo</th>
              <th className="py-4 px-6 font-medium">Ruta</th>
              <th className="py-4 px-6 font-medium">Horarios</th>
              <th className="py-4 px-6 font-medium">Estado</th>
              <th className="py-4 px-6 font-medium min-w-[200px]">Ocupación</th>
              <th className="py-4 px-6 font-medium text-right">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {FLIGHTS_DATA.map((fl, i) => (
              <tr key={i} className="border-b border-slate-800/50 hover:bg-[#151b2b] transition-colors group">
                <td className="py-4 px-6 font-bold text-blue-400">{fl.id}</td>
                <td className="py-4 px-6 text-slate-300 font-medium">
                  {fl.route}
                </td>
                <td className="py-4 px-6 text-xs text-slate-400 space-y-1">
                   <div className="flex items-center gap-1"><Clock className="w-3 h-3 text-slate-500"/> <span className="font-medium text-slate-300">Salida: {fl.depTime.split(' ')[0]}</span> {fl.depTime.slice(6)}</div>
                   <div className="pl-4">Llegada: {fl.arrTime}</div>
                </td>
                <td className="py-4 px-6">
                  <span className={`px-3 py-1.5 rounded text-xs font-bold border ${getStatusBadge(fl.status)}`}>
                    {fl.status}
                  </span>
                </td>
                <td className="py-4 px-6">
                  <div className="flex flex-col gap-1.5">
                    <div className="flex justify-between text-xs text-slate-400">
                      <span>{fl.used} / {fl.capacity}</span>
                      <span className="font-bold border-white/10">{fl.pct}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-[#151b2b] border border-slate-800 rounded-full overflow-hidden">
                      <div className={`h-full ${fl.color}`} style={{ width: `${fl.pct}%` }}></div>
                    </div>
                  </div>
                </td>
                <td className="py-4 px-6 text-right">
                  <div className="flex justify-end gap-3 opacity-0 group-hover:opacity-100 transition-opacity text-slate-500">
                     <button className="hover:text-blue-400 transition-colors"><Edit2 className="w-4 h-4" /></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showUploadModal && (
        <div className="fixed inset-0 z-[10000] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="bg-[#151b2b] border border-slate-800 rounded-2xl w-full max-w-sm overflow-hidden shadow-2xl flex flex-col p-6">
             <div className="flex justify-between items-start mb-6">
                <div>
                   <h3 className="text-xl font-bold text-white mb-1">Carga Masiva de Vuelos</h3>
                   <p className="text-xs text-slate-400">Sube un archivo .txt con el formato requerido para vuelos programados.</p>
                </div>
                <button onClick={() => setShowUploadModal(false)} className="text-slate-500 hover:text-white p-1">
                   <Edit2 className="w-4 h-4 opacity-0" />
                   <div className="absolute top-6 right-6">✕</div>
                </button>
             </div>
             
             <div className="mb-2 text-sm font-bold text-white">Seleccionar Archivo (.txt)</div>
             <div className="bg-[#0B0E14] border border-slate-800 rounded-lg p-2 flex items-center gap-3">
                <button className="bg-[#1a2235] px-3 py-1.5 text-xs rounded text-slate-300">Seleccionar archivo</button>
                <span className="text-xs text-slate-500">Ningún archivo seleccionado</span>
             </div>
          </div>
        </div>
      )}

      {showAddModal && (
        <div className="fixed inset-0 z-[10000] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="bg-[#0B0E14] border border-slate-800 rounded-xl w-full max-w-3xl overflow-hidden shadow-2xl flex flex-col">
            <div className="p-8 overflow-y-auto">
              <button onClick={() => setShowAddModal(false)} className="mb-6 flex items-center gap-2 bg-[#151b2b] hover:bg-slate-800 px-4 py-2 rounded-lg text-sm transition-colors text-slate-300 border border-slate-800 w-fit">
                &larr; Volver al listado
              </button>

              <h2 className="text-2xl font-bold text-white mb-6">Programar Nuevo Vuelo</h2>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-x-6 gap-y-6">
                <div className="flex flex-col gap-2">
                  <label className="font-bold text-slate-200">Código de Vuelo</label>
                  <input type="text" className="bg-[#151b2b] border border-slate-800 rounded-lg px-4 py-3 outline-none focus:border-blue-500 text-sm" placeholder="LA201" />
                </div>
                <div className="flex flex-col gap-2 relative">
                  <label className="font-bold text-slate-200">Aeropuerto de Salida</label>
                  <select className="bg-[#151b2b] border border-slate-800 rounded-lg px-4 py-3 outline-none focus:border-blue-500 appearance-none text-slate-400 text-sm">
                    <option>Seleccionar origen</option>
                  </select>
                </div>
                <div className="flex flex-col gap-2 relative">
                  <label className="font-bold text-slate-200">Hora de Salida</label>
                  <input type="time" className="bg-[#151b2b] border border-slate-800 rounded-lg px-4 py-3 outline-none focus:border-blue-500 text-slate-400 text-sm appearance-none" />
                </div>

                <div className="flex flex-col gap-2">
                  <label className="font-bold text-slate-200">Capacidad de Maletas</label>
                  <input type="text" className="bg-[#151b2b] border border-slate-800 rounded-lg px-4 py-3 outline-none focus:border-blue-500 text-sm" placeholder="0" />
                </div>
                <div className="flex flex-col gap-2 relative">
                  <label className="font-bold text-slate-200">Aeropuerto de Llegada</label>
                  <select className="bg-[#151b2b] border border-slate-800 rounded-lg px-4 py-3 outline-none focus:border-blue-500 appearance-none text-slate-400 text-sm">
                    <option>Seleccionar destino</option>
                  </select>
                </div>
                <div className="flex flex-col gap-2 relative">
                  <label className="font-bold text-slate-200">Hora de Llegada</label>
                  <input type="time" className="bg-[#151b2b] border border-slate-800 rounded-lg px-4 py-3 outline-none focus:border-blue-500 text-slate-400 text-sm" />
                </div>
              </div>

              <div className="mt-12 flex justify-end gap-4 border-t border-slate-800 pt-6">
                 <button onClick={() => setShowAddModal(false)} className="font-bold text-slate-300 hover:text-white px-4 py-2">Cancelar</button>
                 <button className="bg-blue-600 hover:bg-blue-500 text-white font-bold px-8 py-3 rounded-lg transition-colors">Guardar Vuelo</button>
              </div>

            </div>
          </div>
         </div>
      )}
    </div>
  );
}
