import { useState, useEffect, useMemo, useCallback } from "react";
import { Luggage, X } from "lucide-react";

export default function PedidoModal({ open, airports, onClose, onSubmit, loading, lockedOrigin }) {
  const [origen, setOrigen] = useState("");
  const [destino, setDestino] = useState("");
  const [maletas, setMaletas] = useState("1");
  const [errors, setErrors] = useState({});

  const available = useMemo(() => {
    if (!origen) return 0;
    const apt = airports.find(
      (a) => (a.iata ?? a.idAeropuerto) === origen,
    );
    return Math.max(0, (apt?.capacity ?? 0) - (apt?.used ?? 0));
  }, [origen, airports]);

  const clearAll = useCallback(() => {
    setOrigen("");
    setDestino("");
    setMaletas("1");
    setErrors({});
  }, []);

  useEffect(() => {
    if (!open) clearAll();
  }, [open, clearAll]);

  useEffect(() => {
    if (open && lockedOrigin) setOrigen(lockedOrigin);
  }, [open, lockedOrigin]);

  if (!open) return null;

  const knownIata = (a) => a.iata ?? a.idAeropuerto;

  const airportOptions = airports.map((a) => {
    const iata = knownIata(a);
    return (
      <option key={iata} value={iata}>
        {iata} — {a.city ?? ""}
      </option>
    );
  });

  const clearError = (field) =>
    setErrors((prev) => {
      if (!prev[field]) return prev;
      const next = { ...prev };
      delete next[field];
      return next;
    });

  const handleSubmit = (e) => {
    e.preventDefault();
    const errs = {};
    if (!origen) errs.origen = "Seleccioná un aeropuerto de origen";
    if (!destino) errs.destino = "Seleccioná un aeropuerto de destino";
    else if (origen && destino === origen) errs.destino = "Origen y destino no pueden ser iguales";
    if (!maletas || isNaN(parseInt(maletas, 10)))
      errs.maletas = "Ingresá una cantidad";
    else {
      const c = parseInt(maletas, 10);
      if (c < 1) errs.maletas = "Mínimo 1 maleta";
      else if (available <= 0) errs.maletas = "No hay capacidad disponible en este aeropuerto";
      else if (c > available) errs.maletas = `Supera la capacidad disponible (${available} maletas)`;
    }
    setErrors(errs);
    if (Object.keys(errs).length > 0) return;
    onSubmit({
      idAeropuertoOrigen: origen,
      idAeropuertoDestino: destino,
      cantidadMaletas: parseInt(maletas, 10),
    });
  };

  const inputClass = (field) =>
    `w-full rounded-lg border bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:outline-none ${
      errors[field]
        ? "border-danger/60 focus:border-danger"
        : "border-slate-800 focus:border-slate-600"
    }`;

  return (
    <div className="fixed inset-0 z-[10002] flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={onClose}>
      <div className="bg-surface-1 border border-slate-800 rounded-xl shadow-2xl w-full max-w-md mx-4" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800">
          <h2 className="text-lg font-bold text-slate-200 flex items-center gap-2">
            <Luggage className="w-5 h-5 text-info" /> Nuevo Pedido
          </h2>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-surface-2 transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="px-6 py-4 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Aeropuerto Origen</label>
            {lockedOrigin ? (
              <input type="text" value={origen} disabled
                className="w-full rounded-lg border border-slate-700 bg-slate-800/50 py-2 px-3 text-sm text-slate-400 cursor-not-allowed" />
            ) : (
              <select
                value={origen}
                onChange={(e) => { setOrigen(e.target.value); clearError("origen"); clearError("destino"); clearError("maletas"); }}
                required
                className={inputClass("origen")}
              >
                <option value="">Seleccionar...</option>
                {airportOptions}
              </select>
            )}
            {errors.origen && <span className="text-danger text-[10px] mt-1 block">{errors.origen}</span>}
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Aeropuerto Destino</label>
            <select
              value={destino}
              onChange={(e) => { setDestino(e.target.value); clearError("destino"); }}
              required
              className={inputClass("destino")}
            >
              <option value="">Seleccionar...</option>
              {airportOptions}
            </select>
            {errors.destino && <span className="text-danger text-[10px] mt-1 block">{errors.destino}</span>}
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Cantidad de Maletas</label>
            <input
              type="number"
              min="1"
              value={maletas}
              onChange={(e) => {
                const v = parseInt(e.target.value, 10);
                if (!isNaN(v)) {
                  if (v < 1) setMaletas("1");
                  else if (available > 0 && v > available) setMaletas(String(available));
                  else setMaletas(e.target.value);
                } else {
                  setMaletas(e.target.value);
                }
                clearError("maletas");
              }}
              required
              className={inputClass("maletas")}
            />
            {origen && (
              <span className="text-[10px] text-slate-500 mt-1 block">
                Disponible: {available} maletas
              </span>
            )}
            {errors.maletas && <span className="text-danger text-[10px] mt-1 block">{errors.maletas}</span>}
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-semibold text-slate-300 hover:text-white rounded-lg border border-slate-700 hover:bg-surface-2 transition-colors">Cancelar</button>
            <button type="submit" disabled={loading} className="px-4 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-500 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2">
              {loading ? "Enviando..." : "Enviar Pedido"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
