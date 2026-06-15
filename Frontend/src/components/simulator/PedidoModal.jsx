import { useState, useEffect } from "react";
import { Luggage, X } from "lucide-react";

export default function PedidoModal({ open, airports, onClose, onSubmit, loading, lockedOrigin }) {
  const [origen, setOrigen] = useState("");
  const [destino, setDestino] = useState("");
  const [maletas, setMaletas] = useState("1");

  useEffect(() => {
    if (!open) {
      setOrigen("");
      setDestino("");
      setMaletas("1");
    }
  }, [open]);

  useEffect(() => {
    if (lockedOrigin) setOrigen(lockedOrigin);
  }, [lockedOrigin]);

  if (!open) return null;

  const airportOptions = airports.map((a) => (
    <option key={a.iata ?? a.idAeropuerto} value={a.iata ?? a.idAeropuerto}>
      {a.iata ?? a.idAeropuerto} — {a.city ?? ""}
    </option>
  ));

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!origen || !destino || !maletas) return;
    if (origen === destino) return;
    onSubmit({
      idAeropuertoOrigen: origen,
      idAeropuertoDestino: destino,
      cantidadMaletas: parseInt(maletas, 10),
    });
  };

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
              <select value={origen} onChange={(e) => setOrigen(e.target.value)} required className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none">
                <option value="">Seleccionar...</option>
                {airportOptions}
              </select>
            )}
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Aeropuerto Destino</label>
            <select value={destino} onChange={(e) => setDestino(e.target.value)} required className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none">
              <option value="">Seleccionar...</option>
              {airportOptions}
            </select>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Cantidad de Maletas</label>
            <input type="number" min="1" max="200" value={maletas} onChange={(e) => setMaletas(e.target.value)} required className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none" />
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
