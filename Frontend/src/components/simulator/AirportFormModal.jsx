import { useState, useEffect } from "react";
import { Plane, X } from "lucide-react";

const CONTINENTS = [
  "Asia",
  "Europa",
  "America Del Sur",
  "America Del Norte",
  "Centro America",
  "Oceania",
  "Africa",
];

export default function AirportFormModal({ open, initialData, onClose, onSubmit, loading }) {
  const [iata, setIata] = useState("");
  const [city, setCity] = useState("");
  const [continent, setContinent] = useState("");
  const [capacity, setCapacity] = useState("");
  const [lat, setLat] = useState("");
  const [lng, setLng] = useState("");
  const [gmt, setGmt] = useState("");

  const isEdit = !!initialData;

  useEffect(() => {
    if (!open) {
      setIata("");
      setCity("");
      setContinent("");
      setCapacity("");
      setLat("");
      setLng("");
      setGmt("");
    }
  }, [open]);

  useEffect(() => {
    if (initialData) {
      setIata(initialData.iata ?? "");
      setCity(initialData.city ?? "");
      setContinent(initialData.continent ?? "");
      setCapacity(initialData.capacity?.toString() ?? "");
      setLat(initialData.lat?.toString() ?? "");
      setLng(initialData.lng?.toString() ?? "");
      setGmt(initialData.gmt?.toString() ?? "");
    }
  }, [initialData]);

  if (!open) return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!iata || !city || !continent || !capacity) return;
    if (isNaN(parseFloat(lat)) || isNaN(parseFloat(lng))) return;
    onSubmit({
      idAeropuerto: iata.trim().toUpperCase(),
      nombreCiudad: city.trim(),
      continente: continent.toUpperCase().replace(/ /g, "_"),
      capacidadAlmacen: parseInt(capacity, 10),
      maletasActuales: initialData?.used ?? 0,
      latitud: parseFloat(lat),
      longitud: parseFloat(lng),
      husoGMT: parseInt(gmt, 10) || 0,
    });
  };

  return (
    <div className="fixed inset-0 z-[10002] flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={onClose}>
      <div className="bg-surface-1 border border-slate-800 rounded-xl shadow-2xl w-full max-w-md mx-4" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800">
          <h2 className="text-lg font-bold text-slate-200 flex items-center gap-2">
            <Plane className="w-5 h-5 text-info" /> {isEdit ? "Editar Aeropuerto" : "Nuevo Aeropuerto"}
          </h2>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-surface-2 transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="px-6 py-4 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Codigo IATA</label>
            <input
              type="text"
              value={iata}
              onChange={(e) => setIata(e.target.value.toUpperCase())}
              disabled={isEdit}
              maxLength={4}
              required
              className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none disabled:opacity-50 disabled:cursor-not-allowed uppercase"
              placeholder="SKBO"
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Ciudad</label>
            <input
              type="text"
              value={city}
              onChange={(e) => setCity(e.target.value)}
              required
              className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              placeholder="Bogota"
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Continente</label>
            <select
              value={continent}
              onChange={(e) => setContinent(e.target.value)}
              required
              className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
            >
              <option value="">Seleccionar...</option>
              {CONTINENTS.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Capacidad de Almacen</label>
            <input
              type="number"
              min="1"
              value={capacity}
              onChange={(e) => setCapacity(e.target.value)}
              required
              className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              placeholder="700"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Latitud</label>
              <input
                type="number"
                step="any"
                value={lat}
                onChange={(e) => setLat(e.target.value)}
                required
                className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
                placeholder="-12.02"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Longitud</label>
              <input
                type="number"
                step="any"
                value={lng}
                onChange={(e) => setLng(e.target.value)}
                required
                className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
                placeholder="-77.11"
              />
            </div>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Huso GMT</label>
            <input
              type="number"
              value={gmt}
              onChange={(e) => setGmt(e.target.value)}
              className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              placeholder="-5"
            />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-semibold text-slate-300 hover:text-white rounded-lg border border-slate-700 hover:bg-surface-2 transition-colors">Cancelar</button>
            <button type="submit" disabled={loading} className="px-4 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-500 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2">
              {loading ? "Guardando..." : isEdit ? "Actualizar" : "Crear Aeropuerto"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
