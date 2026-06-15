import { useOutletContext } from "react-router-dom";
import { Clock } from "lucide-react";
import { LoadingState, EmptyState, ErrorState } from "../components/ui/States";

const statusBadge = (status) => {
  const s = String(status ?? "").toUpperCase();
  if (s === "CONFIRMADO" || s === "EN_PROGRESO") return "bg-success/10 text-success border-success/20";
  if (s === "CANCELADO") return "bg-danger/10 text-danger border-danger/20";
  if (s === "FINALIZADO") return "bg-blue-500/10 text-blue-400 border-blue-500/20";
  return "bg-slate-800 text-slate-300 border-slate-700";
};

const statusLabel = (status) => {
  const s = String(status ?? "").toUpperCase();
  const map = {
    PROGRAMADO: "Programado",
    CONFIRMADO: "Confirmado",
    EN_PROGRESO: "En progreso",
    FINALIZADO: "Finalizado",
    CANCELADO: "Cancelado",
  };
  return map[s] ?? s;
};

const occupancyColor = (pct) =>
  pct >= 85 ? "bg-danger" : pct >= 60 ? "bg-warning" : "bg-success";

const formatFlightTime = (iso) => {
  if (!iso) return "--:--";
  const parts = iso.split("T");
  if (parts.length < 2) return iso;
  return parts[1].substring(0, 5);
};

export default function FlightsPage() {
  const { simulationPanelData } = useOutletContext();

  const flights = simulationPanelData?.flights;
  const loaded = simulationPanelData?.loaded;
  const loading = !loaded;
  const error = null;
  const flightArray = flights ? Array.from(flights.values()) : [];

  return (
    <div className="flex-1 bg-surface-0 flex flex-col min-h-0 overflow-y-auto w-full h-full p-4 sm:p-8 text-slate-200">
      <div className="flex flex-col lg:flex-row justify-between lg:items-start gap-4 mb-8 pl-12 sm:pl-14">
        <div>
          <h1 className="text-2xl sm:text-4xl font-extrabold text-white mb-2">Tabla de Vuelos</h1>
          <p className="text-slate-400 text-base sm:text-lg">Monitorea la capacidad y estado de los vuelos operativos en tiempo real.</p>
        </div>
      </div>

      <div className="bg-surface-1 border border-slate-800 rounded-xl overflow-x-auto">
        {loading && <LoadingState label="Cargando vuelos..." />}
        {!loading && error && <ErrorState error={error} />}
        {!loading && !error && flightArray.length === 0 && (
          <EmptyState title="Sin vuelos" message="No hay vuelos activos en la sesion de operaciones." />
        )}
        {!loading && !error && flightArray.length > 0 && (
          <table className="w-full text-left border-collapse min-w-[720px]">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400 text-sm">
                <th className="py-4 px-6 font-medium">Vuelo</th>
                <th className="py-4 px-6 font-medium">Ruta</th>
                <th className="py-4 px-6 font-medium">Horarios</th>
                <th className="py-4 px-6 font-medium">Estado</th>
                <th className="py-4 px-6 font-medium min-w-[200px]">Ocupacion</th>
              </tr>
            </thead>
            <tbody>
              {flightArray.map((fl) => {
                const capacity = fl.capacity ?? 1;
                const pct = Math.min(100, Math.round(((fl.used ?? 0) / capacity) * 100));
                return (
                  <tr key={fl.idVueloInstancia ?? fl.id} className="border-b border-slate-800/50 hover:bg-surface-2 transition-colors group">
                    <td className="py-4 px-6 font-bold text-blue-400">{fl.id ?? fl.idVueloInstancia}</td>
                    <td className="py-4 px-6 text-slate-300 font-medium">{fl.origin} → {fl.dest}</td>
                    <td className="py-4 px-6 text-xs text-slate-400 space-y-1">
                      <div className="flex items-center gap-1"><Clock className="w-3 h-3 text-slate-400" /> <span className="font-medium text-slate-300">Salida: {formatFlightTime(fl.depTime)}</span></div>
                      <div className="pl-4">Llegada: {formatFlightTime(fl.arrTime)}</div>
                    </td>
                    <td className="py-4 px-6">
                      <span className={`px-3 py-1.5 rounded text-xs font-bold border ${statusBadge(fl.status)}`}>
                        {statusLabel(fl.status)}
                      </span>
                    </td>
                    <td className="py-4 px-6">
                      <div className="flex flex-col gap-1.5">
                        <div className="flex justify-between text-xs text-slate-400">
                          <span>{fl.used ?? 0} / {fl.capacity}</span>
                          <span className="font-bold">{pct}%</span>
                        </div>
                        <div className="w-full h-1.5 bg-surface-2 border border-slate-800 rounded-full overflow-hidden">
                          <div className={`h-full ${occupancyColor(pct)}`} style={{ width: `${pct}%` }}></div>
                        </div>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
