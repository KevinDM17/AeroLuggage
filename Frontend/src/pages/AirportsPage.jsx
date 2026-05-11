import { useState } from "react";
import { Edit2, EyeOff, Plus } from "lucide-react";
import Modal from "../components/ui/Modal";
import { LoadingState, EmptyState, ErrorState } from "../components/ui/States";
import { useToast } from "../components/ui/Toast";
import { useFetch } from "../hooks/useFetch";
import { listAirports, createAirport } from "../api/airports";

const CONTINENTS = ["Sudamerica", "Norteamerica", "Europa", "Asia"];

const colorByOccupancy = (pct) =>
  pct >= 85 ? "bg-danger" : pct >= 60 ? "bg-warning" : "bg-success";

export default function AirportsPage() {
  const [showAddModal, setShowAddModal] = useState(false);
  const { data: airports, loading, error, refetch } = useFetch(listAirports);

  return (
    <div className="flex-1 bg-surface-0 flex flex-col min-h-0 overflow-y-auto w-full h-full p-4 sm:p-8 text-slate-200">
      <div className="flex flex-col sm:flex-row justify-between sm:items-start gap-4 mb-8 pl-12 sm:pl-14">
        <div>
          <h1 className="text-2xl sm:text-4xl font-extrabold text-white mb-2">Tabla de Aeropuertos</h1>
          <p className="text-slate-400 text-base sm:text-lg">Gestiona la capacidad y estado de los nodos logísticos globales.</p>
        </div>
        <button
          type="button"
          onClick={() => setShowAddModal(true)}
          className="bg-blue-600 hover:bg-blue-500 text-white px-5 py-2.5 rounded-lg flex items-center gap-2 font-medium transition-colors shrink-0 w-full sm:w-auto justify-center"
        >
          <Plus className="w-5 h-5" /> Agregar Aeropuerto
        </button>
      </div>

      <div className="bg-surface-1 border border-slate-800 rounded-xl overflow-x-auto">
        {loading && <LoadingState label="Cargando aeropuertos..." />}
        {!loading && error && <ErrorState error={error} onRetry={refetch} />}
        {!loading && !error && airports?.length === 0 && (
          <EmptyState title="Sin aeropuertos" message="Agrega el primer aeropuerto con el botón de arriba." />
        )}
        {!loading && !error && airports?.length > 0 && (
          <table className="w-full text-left border-collapse min-w-[640px]">
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
              {airports.map((apt) => {
                const pct = Math.round((apt.used / apt.capacity) * 100);
                return (
                  <tr key={apt.iata} className="border-b border-slate-800/50 hover:bg-surface-2 transition-colors group">
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
                          <span>{pct}%</span>
                        </div>
                        <div className="w-full h-1.5 bg-surface-2 border border-slate-800 rounded-full overflow-hidden flex items-center pr-12 relative">
                          <div className={`h-full ${colorByOccupancy(pct)}`} style={{ width: `${pct}%` }}></div>
                          <span className="absolute right-0 text-[10px] text-slate-400">{apt.capacity}</span>
                        </div>
                      </div>
                    </td>
                    <td className="py-4 px-6 text-right">
                      <div className="flex justify-end gap-1 opacity-60 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity text-slate-400">
                         <button type="button" aria-label={`Editar ${apt.iata}`} className="p-2 rounded-lg hover:bg-surface-2 hover:text-blue-400 transition-colors"><Edit2 className="w-4 h-4" /></button>
                         <button type="button" aria-label={`Ocultar ${apt.iata}`} className="p-2 rounded-lg hover:bg-surface-2 hover:text-red-400 transition-colors"><EyeOff className="w-4 h-4" /></button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      <AddAirportModal
        open={showAddModal}
        onClose={() => setShowAddModal(false)}
        onCreated={() => { setShowAddModal(false); refetch(); }}
      />
    </div>
  );
}

function AddAirportModal({ open, onClose, onCreated }) {
  const toast = useToast();
  const initial = { iata: "", name: "", city: "", continent: CONTINENTS[0], gmt: 0, capacity: 600 };
  const [form, setForm] = useState(initial);
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));

  const validate = () => {
    const e = {};
    if (!/^[A-Z]{3}$/.test(form.iata.trim().toUpperCase())) e.iata = "Debe ser 3 letras (ej. LIM)";
    if (!form.name.trim())  e.name  = "Nombre requerido";
    if (!form.city.trim())  e.city  = "Ciudad requerida";
    if (!CONTINENTS.includes(form.continent)) e.continent = "Selecciona un continente";
    const gmt = Number(form.gmt);
    if (!Number.isInteger(gmt) || gmt < -12 || gmt > 14) e.gmt = "GMT entre -12 y +14";
    const cap = Number(form.capacity);
    if (!Number.isFinite(cap) || cap < 500 || cap > 800) e.capacity = "Entre 500 y 800 maletas (PDF)";
    return e;
  };

  const handleSubmit = async (ev) => {
    ev.preventDefault();
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length > 0) return;

    setSubmitting(true);
    try {
      await createAirport({
        iata:      form.iata.trim().toUpperCase(),
        name:      form.name.trim(),
        city:      form.city.trim(),
        continent: form.continent,
        gmt:       Number(form.gmt),
        capacity:  Number(form.capacity),
      });
      toast.push({ type: "success", title: "Aeropuerto registrado", message: `${form.iata.toUpperCase()} agregado correctamente.` });
      setForm(initial);
      setErrors({});
      onCreated();
    } catch (err) {
      toast.push({ type: "error", title: "No se pudo registrar", message: err.message });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="Nuevo Aeropuerto" maxWidth="max-w-2xl">
      <form onSubmit={handleSubmit} className="p-6 sm:p-8 overflow-y-auto">
        <h2 className="text-2xl sm:text-3xl font-bold text-white mb-6">Nuevo Aeropuerto</h2>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-5">
          <Field label="Código IATA" id="apt-iata" error={errors.iata}>
            <input id="apt-iata" type="text" maxLength={3} value={form.iata}
              onChange={(e) => set("iata", e.target.value.toUpperCase())}
              placeholder="LIM" className={inputCls(errors.iata)} />
          </Field>

          <Field label="Nombre del Aeropuerto" id="apt-name" error={errors.name}>
            <input id="apt-name" type="text" value={form.name}
              onChange={(e) => set("name", e.target.value)}
              placeholder="Jorge Chávez" className={inputCls(errors.name)} />
          </Field>

          <Field label="Ciudad" id="apt-city" error={errors.city}>
            <input id="apt-city" type="text" value={form.city}
              onChange={(e) => set("city", e.target.value)}
              placeholder="Lima, Perú" className={inputCls(errors.city)} />
          </Field>

          <Field label="Continente" id="apt-continent" error={errors.continent}>
            <select id="apt-continent" value={form.continent}
              onChange={(e) => set("continent", e.target.value)}
              className={inputCls(errors.continent)}>
              {CONTINENTS.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </Field>

          <Field label="GMT" id="apt-gmt" error={errors.gmt} hint="-12 a +14">
            <input id="apt-gmt" type="number" min={-12} max={14} value={form.gmt}
              onChange={(e) => set("gmt", e.target.value)}
              className={inputCls(errors.gmt)} />
          </Field>

          <Field label="Capacidad de Maletas" id="apt-cap" error={errors.capacity} hint="Entre 500 y 800 (PDF)">
            <input id="apt-cap" type="number" min={500} max={800} value={form.capacity}
              onChange={(e) => set("capacity", e.target.value)}
              className={inputCls(errors.capacity)} />
          </Field>
        </div>

        <div className="mt-8 flex justify-end gap-3 border-t border-slate-800 pt-6">
          <button type="button" onClick={onClose} className="font-bold text-slate-300 hover:text-white px-4 py-2 text-sm">Cancelar</button>
          <button type="submit" disabled={submitting} className="bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-bold px-6 py-2.5 rounded-lg transition-colors text-sm">
            {submitting ? "Guardando..." : "Guardar Aeropuerto"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

const inputCls = (hasError) =>
  `bg-surface-2 border ${hasError ? "border-danger" : "border-slate-700"} rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-slate-200 text-sm w-full`;

function Field({ label, id, error, hint, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="font-bold text-slate-200 text-sm">{label}</label>
      {children}
      {error && <span className="text-xs text-danger">{error}</span>}
      {!error && hint && <span className="text-[11px] text-slate-400">{hint}</span>}
    </div>
  );
}
