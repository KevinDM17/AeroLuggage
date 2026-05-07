import { useState } from "react";
import { SearchIcon, Plus, Calendar, Clock } from "lucide-react";

const ORDERS_DATA = [
  { id: "SHP-001", client: "CUST-101", route: "LIM ➔ MIA", bags: 45, date: "2026-04-14", time: "10:30", status: "Procesando" },
  { id: "SHP-002", client: "CUST-202", route: "BOG ➔ MAD", bags: 120, date: "2026-04-14", time: "11:15", status: "Pendiente" },
  { id: "SHP-003", client: "CUST-303", route: "GRU ➔ LIM", bags: 30, date: "2026-04-14", time: "09:00", status: "Enviado" },
];

function getStatusBadge(status) {
   if (status === 'Enviado') return 'border-[#00ff88]/30 text-[#00ff88] bg-transparent';
   if (status === 'Pendiente') return 'border-[#ffd700]/30 text-[#ffd700] bg-transparent';
   if (status === 'Procesando') return 'border-blue-500/30 text-blue-400 bg-transparent';
   return 'border-slate-700 text-slate-400';
}

export default function OrdersPage() {
  const [showAddModal, setShowAddModal] = useState(false);

  return (
    <div className="flex-1 bg-[#050810] flex flex-col min-h-0 overflow-y-auto w-full h-full p-8 text-slate-200">
      <div className="flex justify-between items-start mb-8">
        <div>
          <h1 className="text-4xl font-extrabold text-white mb-2">Tabla de Pedidos</h1>
          <p className="text-slate-400 text-lg">Administra y registra los envíos masivos de maletas por cliente.</p>
        </div>
        <button 
          onClick={() => setShowAddModal(true)}
          className="bg-blue-600 hover:bg-blue-500 text-white px-5 py-2.5 rounded-lg flex items-center gap-2 font-medium transition-colors"
        >
          <Plus className="w-5 h-5" /> Registrar Envío
        </button>
      </div>

      <div className="bg-[#0B0E14] border border-slate-800 rounded-xl overflow-hidden p-6 pb-2">
        {/* Search Bar */}
        <div className="mb-6 max-w-xl">
           <div className="relative">
              <SearchIcon className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
              <input 
                 type="text" 
                 placeholder="Buscar por ID, cliente o ruta..." 
                 className="w-full bg-[#151b2b] border border-slate-800 rounded-full pl-11 pr-4 py-2.5 text-sm outline-none focus:border-blue-500 text-white placeholder-slate-500"
              />
           </div>
        </div>

        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="border-b border-slate-800 text-slate-400 text-sm">
              <th className="py-4 px-4 font-medium">ID Envío</th>
              <th className="py-4 px-4 font-medium">Cliente</th>
              <th className="py-4 px-4 font-medium">Ruta</th>
              <th className="py-4 px-4 font-medium">Maletas</th>
              <th className="py-4 px-4 font-medium">Registro</th>
              <th className="py-4 px-4 font-medium text-right">Estado</th>
            </tr>
          </thead>
          <tbody>
            {ORDERS_DATA.map((ord, i) => (
              <tr key={i} className="border-b border-slate-800/50 hover:bg-[#151b2b] transition-colors">
                <td className="py-5 px-4 font-bold text-blue-400">{ord.id}</td>
                <td className="py-5 px-4 font-bold text-slate-200 text-sm">{ord.client}</td>
                <td className="py-5 px-4 text-slate-300 text-sm font-medium">{ord.route}</td>
                <td className="py-5 px-4">
                   <div className="flex items-center gap-2 text-slate-200 font-medium">
                      <div className="w-3 h-3 rounded-full border border-slate-600 bg-transparent flex items-center justify-center"><div className="w-1.5 h-1.5 bg-slate-500 rounded-full"></div></div> 
                      {ord.bags}
                   </div>
                </td>
                <td className="py-5 px-4 text-xs text-slate-400 space-y-1">
                   <div className="flex items-center gap-1"><Calendar className="w-3 h-3"/> {ord.date}</div>
                   <div className="flex items-center gap-1"><Clock className="w-3 h-3"/> {ord.time}</div>
                </td>
                <td className="py-5 px-4 text-right">
                  <span className={`px-4 py-1.5 rounded-full text-xs font-medium border ${getStatusBadge(ord.status)} inline-block`}>
                    {ord.status}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showAddModal && (
        <div className="fixed inset-0 z-[10000] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="bg-[#151b2b] border border-slate-800 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl flex flex-col p-8 relative">
             <button onClick={() => setShowAddModal(false)} className="absolute top-6 right-6 text-slate-500 hover:text-white">✕</button>

             <div className="flex items-center gap-2 mb-2 text-blue-400 font-bold">
                 <div className="w-3 h-3 rounded-full border-2 border-blue-500 bg-transparent"></div>
                 Nuevo Registro de Pedido
             </div>
             <p className="text-sm text-slate-400 mb-6">Ingresa los detalles del envío para su procesamiento en la red.</p>

             <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                   <div className="flex flex-col gap-1.5">
                     <label className="font-bold text-slate-200 text-xs">Origen</label>
                     <select className="bg-[#0B0E14] border border-slate-800 rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-slate-400 text-sm appearance-none">
                       <option>Origen</option>
                     </select>
                   </div>
                   <div className="flex flex-col gap-1.5">
                     <label className="font-bold text-slate-200 text-xs">Destino</label>
                     <select className="bg-[#0B0E14] border border-slate-800 rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-slate-400 text-sm appearance-none">
                       <option>Destino</option>
                     </select>
                   </div>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="font-bold text-slate-200 text-xs">Cantidad de Maletas</label>
                  <input type="text" className="bg-[#0B0E14] border border-slate-800 rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-sm text-white" placeholder="0" />
                </div>

                <div className="grid grid-cols-2 gap-4">
                   <div className="flex flex-col gap-1.5 relative">
                     <label className="font-bold text-slate-200 text-xs">Fecha de Registro</label>
                     <input type="date" className="bg-[#0B0E14] border border-slate-800 rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-slate-400 text-sm" value="2026-04-14" readOnly />
                   </div>
                   <div className="flex flex-col gap-1.5 relative">
                     <label className="font-bold text-slate-200 text-xs">Hora de Registro</label>
                     <input type="time" className="bg-[#0B0E14] border border-slate-800 rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-slate-400 text-sm" value="17:27" readOnly />
                   </div>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="font-bold text-slate-200 text-xs">ID de Cliente</label>
                  <input type="text" className="bg-[#0B0E14] border border-slate-800 rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-sm text-slate-400" placeholder="  CUST-000" />
                </div>
             </div>

             <div className="mt-8 flex justify-end gap-3 rounded-b-2xl bg-slate-500/0">
                 <button onClick={() => setShowAddModal(false)} className="font-bold text-slate-300 hover:text-white px-4 py-2 text-sm">Cancelar</button>
                 <button className="bg-blue-600 hover:bg-blue-500 text-white font-bold px-6 py-2.5 rounded-lg transition-colors text-sm">Registrar Pedido</button>
             </div>
          </div>
        </div>
      )}
    </div>
  );
}
