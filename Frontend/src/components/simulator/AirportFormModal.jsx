import { useState, useEffect, useCallback } from "react";
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

const IATA_RE = /^[A-Z]{3,5}$/;

function validateFields({ iata, city, continent, capacity, lat, lng, gmt }) {
  const errs = {};
  if (!iata || !IATA_RE.test(iata.trim()))
    errs.iata = "Debe tener entre 3 y 5 letras mayúsculas (ej: SKBO)";
  if (!city || !city.trim())
    errs.city = "La ciudad es obligatoria";
  else if (city.trim().length > 100)
    errs.city = "Máximo 100 caracteres";
  if (!continent)
    errs.continent = "Seleccioná un continente";
  if (!capacity || isNaN(parseInt(capacity, 10)))
    errs.capacity = "Ingresá una capacidad";
  else {
    const c = parseInt(capacity, 10);
    if (c < 1 || c > 10000)
      errs.capacity = "Debe estar entre 1 y 10000";
  }
  if (lat === "" || isNaN(parseFloat(lat)))
    errs.lat = "Ingresá una latitud";
  else {
    const v = parseFloat(lat);
    if (v < -90 || v > 90)
      errs.lat = "Debe estar entre -90 y 90";
  }
  if (lng === "" || isNaN(parseFloat(lng)))
    errs.lng = "Ingresá una longitud";
  else {
    const v = parseFloat(lng);
    if (v < -180 || v > 180)
      errs.lng = "Debe estar entre -180 y 180";
  }
  if (gmt !== "" && (isNaN(parseInt(gmt, 10)) || parseInt(gmt, 10) < -12 || parseInt(gmt, 10) > 14))
    errs.gmt = "Debe estar entre -12 y +14";
  return errs;
}

export default function AirportFormModal({ open, initialData, onClose, onSubmit, loading }) {
  const [iata, setIata] = useState("");
  const [city, setCity] = useState("");
  const [continent, setContinent] = useState("");
  const [capacity, setCapacity] = useState("");
  const [lat, setLat] = useState("");
  const [lng, setLng] = useState("");
  const [gmt, setGmt] = useState("");
  const [errors, setErrors] = useState({});

  const isEdit = !!initialData;

  const clearAll = useCallback(() => {
    setIata("");
    setCity("");
    setContinent("");
    setCapacity("");
    setLat("");
    setLng("");
    setGmt("");
    setErrors({});
  }, []);

  useEffect(() => {
    if (!open) clearAll();
  }, [open, clearAll]);

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
    const errs = validateFields({ iata, city, continent, capacity, lat, lng, gmt });
    setErrors(errs);
    if (Object.keys(errs).length > 0) return;
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

  const clearError = (field) => setErrors((prev) => {
    if (!prev[field]) return prev;
    const next = { ...prev };
    delete next[field];
    return next;
  });

  const inputClass = (field) =>
    `w-full rounded-lg border bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:outline-none ${
      errors[field] ? "border-danger/60 focus:border-danger" : "border-slate-800 focus:border-slate-600"
    }`;

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
              onChange={(e) => { setIata(e.target.value.toUpperCase()); clearError("iata"); }}
              disabled={isEdit}
               maxLength={5}
              required
              className={`${inputClass("iata")} disabled:opacity-50 disabled:cursor-not-allowed uppercase`}
              placeholder="SKBO"
            />
            {errors.iata && <span className="text-danger text-[10px] mt-1 block">{errors.iata}</span>}
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Ciudad</label>
            <input
              type="text"
              value={city}
              onChange={(e) => { setCity(e.target.value); clearError("city"); }}
              required
              className={inputClass("city")}
              placeholder="Bogota"
            />
            {errors.city && <span className="text-danger text-[10px] mt-1 block">{errors.city}</span>}
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Continente</label>
            <select
              value={continent}
              onChange={(e) => { setContinent(e.target.value); clearError("continent"); }}
              required
              className={inputClass("continent")}
            >
              <option value="">Seleccionar...</option>
              {CONTINENTS.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
            {errors.continent && <span className="text-danger text-[10px] mt-1 block">{errors.continent}</span>}
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Capacidad de Almacen</label>
            <input
              type="number"
              min="1"
              value={capacity}
               onChange={(e) => { const v = parseInt(e.target.value, 10); if (!isNaN(v)) { if (v < 1) setCapacity("1"); else if (v > 10000) setCapacity("10000"); else setCapacity(e.target.value); } else { setCapacity(e.target.value); } clearError("capacity"); }}
              required
              className={inputClass("capacity")}
              placeholder="700"
            />
            {errors.capacity && <span className="text-danger text-[10px] mt-1 block">{errors.capacity}</span>}
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Latitud</label>
              <input
                type="number"
                step="any"
                value={lat}
                 onChange={(e) => { const v = parseFloat(e.target.value); if (!isNaN(v)) { if (v < -90) setLat("-90"); else if (v > 90) setLat("90"); else setLat(e.target.value); } else { setLat(e.target.value); } clearError("lat"); }}
                required
                className={inputClass("lat")}
                placeholder="-12.02"
              />
              {errors.lat && <span className="text-danger text-[10px] mt-1 block">{errors.lat}</span>}
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Longitud</label>
              <input
                type="number"
                step="any"
                value={lng}
                 onChange={(e) => { const v = parseFloat(e.target.value); if (!isNaN(v)) { if (v < -180) setLng("-180"); else if (v > 180) setLng("180"); else setLng(e.target.value); } else { setLng(e.target.value); } clearError("lng"); }}
                required
                className={inputClass("lng")}
                placeholder="-77.11"
              />
              {errors.lng && <span className="text-danger text-[10px] mt-1 block">{errors.lng}</span>}
            </div>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Huso GMT</label>
            <input
              type="number"
              value={gmt}
               onChange={(e) => { const v = parseInt(e.target.value, 10); if (!isNaN(v)) { if (v < -12) setGmt("-12"); else if (v > 14) setGmt("14"); else setGmt(e.target.value); } else { setGmt(e.target.value); } clearError("gmt"); }}
              className={inputClass("gmt")}
              placeholder="-5"
            />
            {errors.gmt && <span className="text-danger text-[10px] mt-1 block">{errors.gmt}</span>}
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
