import { useState, useEffect } from "react";
import { Plane, X } from "lucide-react";

export default function FlightPlanFormModal({ open, initialData, airports, onClose, onSubmit, loading }) {
  const [code, setCode] = useState("");
  const [origin, setOrigin] = useState("");
  const [dest, setDest] = useState("");
  const [depTime, setDepTime] = useState("");
  const [arrTime, setArrTime] = useState("");
  const [capacity, setCapacity] = useState("");

  const isEdit = !!initialData;

  useEffect(() => {
    if (!open) {
      setCode("");
      setOrigin("");
      setDest("");
      setDepTime("");
      setArrTime("");
      setCapacity("");
    }
  }, [open]);

  useEffect(() => {
    if (initialData) {
      setCode(initialData.id ?? "");
      setOrigin(initialData.origin ?? "");
      setDest(initialData.dest ?? "");
      setDepTime(initialData.depTime ?? "");
      setArrTime(initialData.arrTime ?? "");
      setCapacity(initialData.capacity?.toString() ?? "");
    }
  }, [initialData]);

  if (!open) return null;

  const airportOptions = (airports || []).map((a) => (
    <option key={a.iata ?? a.idAeropuerto} value={a.iata ?? a.idAeropuerto}>
      {a.iata ?? a.idAeropuerto} — {a.city ?? a?.ciudad?.nombre ?? ""}
    </option>
  ));

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!code || !origin || !dest || !depTime || !arrTime || !capacity) return;
    if (origin === dest) return;
    onSubmit({
      id: code.trim().toUpperCase(),
      origin: origin,
      dest: dest,
      depTime: depTime,
      arrTime: arrTime,
      capacity: parseInt(capacity, 10),
      used: initialData?.used ?? 0,
    });
  };

  return (
    <div className="fixed inset-0 z-[10002] flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={onClose}>
      <div className="bg-surface-1 border border-slate-800 rounded-xl shadow-2xl w-full max-w-md mx-4" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800">
          <h2 className="text-lg font-bold text-slate-200 flex items-center gap-2">
            <Plane className="w-5 h-5 text-info" /> {isEdit ? "Editar Plan de Vuelo" : "Nuevo Plan de Vuelo"}
          </h2>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-surface-2 transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="px-6 py-4 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Codigo</label>
            <input
              type="text"
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              disabled={isEdit}
              required
              className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none disabled:opacity-50 disabled:cursor-not-allowed uppercase"
              placeholder="LIM-MIA-10:00"
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Aeropuerto Origen</label>
            <select
              value={origin}
              onChange={(e) => setOrigin(e.target.value)}
              required
              className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
            >
              <option value="">Seleccionar...</option>
              {airportOptions}
            </select>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Aeropuerto Destino</label>
            <select
              value={dest}
              onChange={(e) => setDest(e.target.value)}
              required
              className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
            >
              <option value="">Seleccionar...</option>
              {airportOptions}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Hora Salida</label>
              <input
                type="time"
                value={depTime}
                onChange={(e) => setDepTime(e.target.value)}
                required
                className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Hora Llegada</label>
              <input
                type="time"
                value={arrTime}
                onChange={(e) => setArrTime(e.target.value)}
                required
                className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              />
            </div>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Capacidad</label>
            <input
              type="number"
              min="1"
              value={capacity}
              onChange={(e) => setCapacity(e.target.value)}
              required
              className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              placeholder="250"
            />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-semibold text-slate-300 hover:text-white rounded-lg border border-slate-700 hover:bg-surface-2 transition-colors">Cancelar</button>
            <button type="submit" disabled={loading} className="px-4 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-500 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2">
              {loading ? "Guardando..." : isEdit ? "Actualizar" : "Crear Plan de Vuelo"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
