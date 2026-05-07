import { useState } from "react";
import { Edit2, EyeOff, Search, Plus, MapPin, Search as SearchIcon, UploadCloud, X } from "lucide-react";

const AIRPORTS_DATA = [
  { iata: "LIM", city: "Lima, Perú", continent: "Sudamérica", used: 3200, capacity: 5000, pct: 64, color: "bg-[#00ff88]" },
  { iata: "BOG", city: "Bogotá, Colombia", continent: "Sudamérica", used: 7500, capacity: 8000, pct: 94, color: "bg-[#ff3b30]" },
  { iata: "GRU", city: "São Paulo, Brasil", continent: "Sudamérica", used: 8000, capacity: 12000, pct: 67, color: "bg-[#00ff88]" },
  { iata: "MIA", city: "Miami, Estados Unidos", continent: "Norteamérica", used: 14200, capacity: 15000, pct: 95, color: "bg-[#ff3b30]" },
  { iata: "MAD", city: "Madrid, España", continent: "Europa", used: 4500, capacity: 9200, pct: 45, color: "bg-[#00ff88]" },
];

export default function AirportsPage() {
  const [showAddModal, setShowAddModal] = useState(false);
  const [showUploadModal, setShowUploadModal] = useState(false);

  return (
    <div className="flex-1 bg-[#050810] flex flex-col min-h-0 overflow-y-auto w-full h-full p-8 text-slate-200">
      {/* Header */}
      <div className="flex justify-between items-start mb-8">
        <div>
          <h1 className="text-4xl font-extrabold text-white mb-2">Tabla de Aeropuertos</h1>
          <p className="text-slate-400 text-lg">Gestiona la capacidad y estado de los nodos logísticos globales.</p>
        </div>
        <button 
          onClick={() => setShowAddModal(true)}
          className="bg-blue-600 hover:bg-blue-500 text-white px-5 py-2.5 rounded-lg flex items-center gap-2 font-medium transition-colors"
        >
          <Plus className="w-5 h-5" /> Agregar Aeropuerto
        </button>
      </div>

      {/* Table */}
      <div className="bg-[#0B0E14] border border-slate-800 rounded-xl overflow-hidden">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="border-b border-slate-800 text-slate-400 text-sm">
              <th className="py-4 px-6 font-medium">IATA</th>
              <th className="py-4 px-6 font-medium">Ciudad</th>
              <th className="py-4 px-6 font-medium">Continente</th>
              <th className="py-4 px-6 font-medium min-w-[300px]">Ocupación</th>
              <th className="py-4 px-6 font-medium text-right">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {AIRPORTS_DATA.map((apt, i) => (
              <tr key={i} className="border-b border-slate-800/50 hover:bg-[#151b2b] transition-colors group">
                <td className="py-4 px-6 font-bold text-blue-400">{apt.iata}</td>
                <td className="py-4 px-6">
                  <div className="flex items-center gap-2">
                    <div className="w-1.5 h-1.5 rounded-full bg-slate-500"></div>
                    {apt.city}
                  </div>
                </td>
                <td className="py-4 px-6 text-slate-300">{apt.continent}</td>
                <td className="py-4 px-6">
                  <div className="flex flex-col gap-1.5">
                    <div className="flex justify-between text-xs text-slate-400 font-medium">
                      <span>{apt.used} MALETAS</span>
                      <span>{apt.pct}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-[#151b2b] border border-slate-800 rounded-full overflow-hidden flex items-center pr-12 relative">
                      <div className={`h-full ${apt.color}`} style={{ width: `${apt.pct}%` }}></div>
                      <span className="absolute right-0 text-[10px] text-slate-500">{apt.capacity}</span>
                    </div>
                  </div>
                </td>
                <td className="py-4 px-6 text-right">
                  <div className="flex justify-end gap-3 opacity-0 group-hover:opacity-100 transition-opacity text-slate-500">
                     <button className="hover:text-blue-400 transition-colors"><Edit2 className="w-4 h-4" /></button>
                     <button className="hover:text-red-400 transition-colors"><EyeOff className="w-4 h-4" /></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showAddModal && (
        <div className="fixed inset-0 z-[10000] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="bg-[#0B0E14] border border-slate-800 rounded-xl w-full max-w-4xl overflow-hidden shadow-2xl flex flex-col max-h-full">
            <div className="p-8 overflow-y-auto">
              <button onClick={() => setShowAddModal(false)} className="mb-6 flex items-center gap-2 bg-[#151b2b] hover:bg-slate-800 px-4 py-2 rounded-lg text-sm transition-colors text-slate-300 border border-slate-800">
                &larr; Volver al listado
              </button>

              <h2 className="text-3xl font-bold text-white mb-2">Nuevo Aeropuerto</h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-x-12 gap-y-6 mt-8">
                <div className="flex flex-col gap-2">
                  <label className="font-bold text-slate-200">Código IATA (3 dígitos)</label>
                  <input type="text" className="bg-[#151b2b] border border-slate-800 rounded-lg px-4 py-3 outline-none focus:border-blue-500" placeholder="LIM" />
                </div>

                <div className="flex flex-col gap-2">
                  <label className="font-bold text-blue-400">Latitud</label>
                  <div className="flex gap-2">
                     <div className="flex-1 flex flex-col gap-1"><span className="text-[10px] text-slate-500 uppercase">Grados</span><input className="bg-[#151b2b] border border-slate-800 rounded-lg px-3 py-2 outline-none w-full" placeholder="0" /></div>
                     <div className="flex-1 flex flex-col gap-1"><span className="text-[10px] text-slate-500 uppercase">Minutos</span><input className="bg-[#151b2b] border border-slate-800 rounded-lg px-3 py-2 outline-none w-full" placeholder="0" /></div>
                     <div className="flex-1 flex flex-col gap-1"><span className="text-[10px] text-slate-500 uppercase">Segundos</span><input className="bg-[#151b2b] border border-slate-800 rounded-lg px-3 py-2 outline-none w-full" placeholder="0" /></div>
                     <div className="w-16 flex flex-col gap-1"><span className="text-[10px] text-slate-500 uppercase">Dir</span><input className="bg-[#151b2b] border border-slate-800 rounded-lg px-3 py-2 outline-none w-full text-center" placeholder="N" /></div>
                  </div>
                </div>

                <div className="flex flex-col gap-2">
                  <label className="font-bold text-slate-200">Nombre del Aeropuerto</label>
                  <input type="text" className="bg-[#151b2b] border border-slate-800 rounded-lg px-4 py-3 outline-none focus:border-blue-500" placeholder="Jorge Chávez" />
                </div>

                <div className="flex flex-col gap-2">
                  <label className="font-bold text-blue-400">Longitud</label>
                  <div className="flex gap-2">
                     <div className="flex-1 flex flex-col gap-1"><span className="text-[10px] text-slate-500 uppercase">Grados</span><input className="bg-[#151b2b] border border-slate-800 rounded-lg px-3 py-2 outline-none w-full" placeholder="0" /></div>
                     <div className="flex-1 flex flex-col gap-1"><span className="text-[10px] text-slate-500 uppercase">Minutos</span><input className="bg-[#151b2b] border border-slate-800 rounded-lg px-3 py-2 outline-none w-full" placeholder="0" /></div>
                     <div className="flex-1 flex flex-col gap-1"><span className="text-[10px] text-slate-500 uppercase">Segundos</span><input className="bg-[#151b2b] border border-slate-800 rounded-lg px-3 py-2 outline-none w-full" placeholder="0" /></div>
                     <div className="w-16 flex flex-col gap-1"><span className="text-[10px] text-slate-500 uppercase">Dir</span><input className="bg-[#151b2b] border border-slate-800 rounded-lg px-3 py-2 outline-none w-full text-center" placeholder="E" /></div>
                  </div>
                </div>

                <div className="flex flex-col gap-2">
                  <label className="font-bold text-slate-200">GMT</label>
                  <input type="text" className="bg-[#151b2b] border border-slate-800 rounded-lg px-4 py-3 outline-none focus:border-blue-500" placeholder="0" />
                </div>

                <div className="flex flex-col gap-4">
                  <div className="flex flex-col gap-2">
                    <label className="font-bold text-slate-200">Capacidad de Maletas</label>
                    <input type="text" className="bg-[#151b2b] border border-slate-800 rounded-lg px-4 py-3 outline-none focus:border-blue-500" placeholder="0" />
                  </div>
                  <div className="flex flex-col gap-2">
                    <label className="font-bold text-slate-200">Ciudad</label>
                    <div className="flex gap-2">
                      <select className="bg-[#151b2b] border border-slate-800 rounded-lg px-4 py-3 outline-none focus:border-blue-500 flex-1 appearance-none text-slate-400">
                        <option>Seleccionar ciudad</option>
                      </select>
                      <div className="w-12 h-[50px] bg-white rounded-lg flex items-center justify-center shrink-0"></div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="mt-12 flex justify-end gap-4 border-t border-slate-800 pt-6">
                 <button onClick={() => setShowAddModal(false)} className="font-bold text-slate-300 hover:text-white px-4 py-2">Cancelar</button>
                 <button className="bg-blue-600 hover:bg-blue-500 text-white font-bold px-8 py-3 rounded-lg transition-colors">Guardar Aeropuerto</button>
              </div>

            </div>
          </div>
        </div>
      )}
    </div>
  );
}
