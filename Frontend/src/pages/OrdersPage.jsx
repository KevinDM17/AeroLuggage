import { useState } from "react";
import { Search, Plus, Calendar, Clock } from "lucide-react";
import Modal from "../components/ui/Modal";

const ORDERS_DATA = [
  { id: "SHP-001", client: "CUST-101", route: "LIM ➔ MIA", bags: 45, date: "2026-04-14", time: "10:30", status: "Procesando" },
  { id: "SHP-002", client: "CUST-202", route: "BOG ➔ MAD", bags: 120, date: "2026-04-14", time: "11:15", status: "Pendiente" },
  { id: "SHP-003", client: "CUST-303", route: "GRU ➔ LIM", bags: 30, date: "2026-04-14", time: "09:00", status: "Enviado" },
];

function getStatusBadge(status) {
   if (status === 'Enviado') return 'border-success/30 text-success bg-transparent';
   if (status === 'Pendiente') return 'border-warning/30 text-warning bg-transparent';
   if (status === 'Procesando') return 'border-blue-500/30 text-blue-400 bg-transparent';
   return 'border-slate-700 text-slate-400';
}

const today = new Date();
const todayISO = today.toISOString().slice(0, 10);
const nowHHMM = today.toTimeString().slice(0, 5);

export default function OrdersPage() {
  const [showAddModal, setShowAddModal] = useState(false);

  return (
    <div className="flex-1 bg-surface-0 flex flex-col min-h-0 overflow-y-auto w-full h-full p-4 sm:p-8 text-slate-200">
      <div className="flex flex-col sm:flex-row justify-between sm:items-start gap-4 mb-8 pl-12 sm:pl-14">
        <div>
          <h1 className="text-2xl sm:text-4xl font-extrabold text-white mb-2">Tabla de Pedidos</h1>
          <p className="text-slate-400 text-base sm:text-lg">Administra y registra los envíos masivos de maletas por cliente.</p>
        </div>
        <button
          type="button"
          onClick={() => setShowAddModal(true)}
          className="bg-blue-600 hover:bg-blue-500 text-white px-5 py-2.5 rounded-lg flex items-center gap-2 font-medium transition-colors shrink-0 w-full sm:w-auto justify-center"
        >
          <Plus className="w-5 h-5" /> Registrar Envío
        </button>
      </div>

      <div className="bg-surface-1 border border-slate-800 rounded-xl overflow-x-auto p-4 sm:p-6 pb-2">
        <div className="mb-6 max-w-xl">
           <div className="relative">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" aria-hidden="true" />
              <label htmlFor="orders-search" className="sr-only">Buscar pedidos</label>
              <input
                 id="orders-search"
                 type="text"
                 placeholder="Buscar por ID, cliente o ruta..."
                 className="w-full bg-surface-2 border border-slate-800 rounded-full pl-11 pr-4 py-2.5 text-sm outline-none focus:border-blue-500 text-white placeholder-slate-400"
              />
           </div>
        </div>

        <table className="w-full text-left border-collapse min-w-[640px]">
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
              <tr key={i} className="border-b border-slate-800/50 hover:bg-surface-2 transition-colors">
                <td className="py-5 px-4 font-bold text-blue-400">{ord.id}</td>
                <td className="py-5 px-4 font-bold text-slate-200 text-sm">{ord.client}</td>
                <td className="py-5 px-4 text-slate-300 text-sm font-medium">{ord.route}</td>
                <td className="py-5 px-4">
                   <div className="flex items-center gap-2 text-slate-200 font-medium">
                      <div className="w-3 h-3 rounded-full border border-slate-600 bg-transparent flex items-center justify-center"><div className="w-1.5 h-1.5 bg-slate-400 rounded-full"></div></div>
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

      <Modal open={showAddModal} onClose={() => setShowAddModal(false)} title="Nuevo Registro de Pedido" maxWidth="max-w-lg">
        <div className="p-8">
          <div className="flex items-center gap-2 mb-2 text-blue-400 font-bold">
              <div className="w-3 h-3 rounded-full border-2 border-blue-500 bg-transparent"></div>
              Nuevo Registro de Pedido
          </div>
          <p className="text-sm text-slate-400 mb-6">Ingresa los detalles del envío para su procesamiento en la red.</p>

          <div className="space-y-4">
             <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="order-origin" className="font-bold text-slate-200 text-xs">Origen</label>
                  <select id="order-origin" className="bg-surface-1 border border-slate-800 rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-slate-400 text-sm appearance-none">
                    <option>Origen</option>
                  </select>
                </div>
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="order-dest" className="font-bold text-slate-200 text-xs">Destino</label>
                  <select id="order-dest" className="bg-surface-1 border border-slate-800 rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-slate-400 text-sm appearance-none">
                    <option>Destino</option>
                  </select>
                </div>
             </div>

             <div className="flex flex-col gap-1.5">
               <label htmlFor="order-bags" className="font-bold text-slate-200 text-xs">Cantidad de Maletas</label>
               <input id="order-bags" type="text" className="bg-surface-1 border border-slate-800 rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-sm text-white" placeholder="0" />
             </div>

             <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="order-date" className="font-bold text-slate-200 text-xs">Fecha de Registro</label>
                  <input id="order-date" type="date" defaultValue={todayISO} className="bg-surface-1 border border-slate-800 rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-slate-300 text-sm" />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="order-time" className="font-bold text-slate-200 text-xs">Hora de Registro</label>
                  <input id="order-time" type="time" defaultValue={nowHHMM} className="bg-surface-1 border border-slate-800 rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-slate-300 text-sm" />
                </div>
             </div>

             <div className="flex flex-col gap-1.5">
               <label htmlFor="order-client" className="font-bold text-slate-200 text-xs">ID de Cliente</label>
               <input id="order-client" type="text" className="bg-surface-1 border border-slate-800 rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-sm text-slate-200 placeholder-slate-500" placeholder="CUST-000" />
             </div>
          </div>

          <div className="mt-8 flex justify-end gap-3">
             <button type="button" onClick={() => setShowAddModal(false)} className="font-bold text-slate-300 hover:text-white px-4 py-2 text-sm">Cancelar</button>
             <button type="button" className="bg-blue-600 hover:bg-blue-500 text-white font-bold px-6 py-2.5 rounded-lg transition-colors text-sm">Registrar Pedido</button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
