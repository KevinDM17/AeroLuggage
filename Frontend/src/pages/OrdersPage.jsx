import { useMemo, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { Search, Calendar, Clock } from "lucide-react";
import { LoadingState, EmptyState, ErrorState } from "../components/ui/States";

const statusBadge = (status) => {
  const s = String(status ?? "").toUpperCase();
  if (s === "ENTREGADO" || s === "FINALIZADO") return "border-success/30 text-success bg-transparent";
  if (s === "REGISTRADO" || s === "PENDIENTE") return "border-warning/30 text-warning bg-transparent";
  if (s === "PROCESANDO" || s === "PROCESADO") return "border-blue-500/30 text-blue-400 bg-transparent";
  return "border-slate-700 text-slate-400";
};

const statusLabel = (status) => {
  const s = String(status ?? "").toUpperCase();
  const map = {
    REGISTRADO: "Registrado",
    PENDIENTE: "Pendiente",
    PROCESANDO: "Procesando",
    PROCESADO: "Procesado",
    ENVIADO: "Enviado",
    ENTREGADO: "Entregado",
    FINALIZADO: "Finalizado",
  };
  return map[s] ?? s;
};

export default function OrdersPage() {
  const { simulationPanelData } = useOutletContext();
  const [query, setQuery] = useState("");

  const orders = simulationPanelData?.orders;
  const loaded = simulationPanelData?.loaded;
  const loading = !loaded;
  const error = null;
  const orderArray = orders ? Array.from(orders.values()) : [];

  const filtered = useMemo(() => {
    if (!orderArray.length) return [];
    const q = query.trim().toLowerCase();
    if (!q) return orderArray;
    return orderArray.filter((o) =>
      [o.id, o.origin, o.dest].some((v) => String(v).toLowerCase().includes(q))
    );
  }, [orderArray, query]);

  return (
    <div className="flex-1 bg-surface-0 flex flex-col min-h-0 overflow-y-auto w-full h-full p-4 sm:p-8 text-slate-200">
      <div className="flex flex-col sm:flex-row justify-between sm:items-start gap-4 mb-8 pl-12 sm:pl-14">
        <div>
          <h1 className="text-2xl sm:text-4xl font-extrabold text-white mb-2">Tabla de Pedidos</h1>
          <p className="text-slate-400 text-base sm:text-lg">Monitorea los envios y su estado en tiempo real.</p>
        </div>
      </div>

      <div className="bg-surface-1 border border-slate-800 rounded-xl overflow-x-auto p-4 sm:p-6 pb-2">
        <div className="mb-6 max-w-xl">
          <div className="relative">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" aria-hidden="true" />
            <label htmlFor="orders-search" className="sr-only">Buscar pedidos</label>
            <input
              id="orders-search"
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Buscar por ID, origen o destino..."
              className="w-full bg-surface-2 border border-slate-800 rounded-full pl-11 pr-4 py-2.5 text-sm outline-none focus:border-blue-500 text-white placeholder-slate-400"
            />
          </div>
        </div>

        {loading && <LoadingState label="Cargando pedidos..." />}
        {!loading && error && <ErrorState error={error} />}
        {!loading && !error && orderArray.length === 0 && (
          <EmptyState title="Sin pedidos" message="No hay pedidos en la sesion activa." />
        )}
        {!loading && !error && orderArray.length > 0 && filtered.length === 0 && (
          <EmptyState title="Sin resultados" message={`Ningun pedido coincide con "${query}".`} />
        )}
        {!loading && !error && filtered.length > 0 && (
          <table className="w-full text-left border-collapse min-w-[640px]">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400 text-sm">
                <th className="py-4 px-4 font-medium">ID Pedido</th>
                <th className="py-4 px-4 font-medium">Ruta</th>
                <th className="py-4 px-4 font-medium">Maletas</th>
                <th className="py-4 px-4 font-medium">Registro</th>
                <th className="py-4 px-4 font-medium text-right">Estado</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((ord) => (
                <tr key={ord.id ?? ord.idPedido} className="border-b border-slate-800/50 hover:bg-surface-2 transition-colors">
                  <td className="py-5 px-4 font-bold text-blue-400">{ord.id ?? ord.idPedido}</td>
                  <td className="py-5 px-4 text-slate-300 text-sm font-medium">{ord.origin} → {ord.dest}</td>
                  <td className="py-5 px-4">
                    <div className="flex items-center gap-2 text-slate-200 font-medium">
                      <div className="w-3 h-3 rounded-full border border-slate-600 bg-transparent flex items-center justify-center"><div className="w-1.5 h-1.5 bg-slate-400 rounded-full"></div></div>
                      {ord.bags ?? ord.cantidadMaletas ?? 0}
                    </div>
                  </td>
                  <td className="py-5 px-4 text-xs text-slate-400 space-y-1">
                    <div className="flex items-center gap-1"><Calendar className="w-3 h-3" /> {ord.date ?? ord.fecha ?? "--"}</div>
                    <div className="flex items-center gap-1"><Clock className="w-3 h-3" /> {ord.time ?? ord.hora ?? "--"}</div>
                  </td>
                  <td className="py-5 px-4 text-right">
                    <span className={`px-4 py-1.5 rounded-full text-xs font-medium border ${statusBadge(ord.status ?? ord.estado)} inline-block`}>
                      {statusLabel(ord.status ?? ord.estado)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
