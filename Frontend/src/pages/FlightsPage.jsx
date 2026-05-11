import { useState } from "react";
import { Edit2, Plus, UploadCloud, Clock } from "lucide-react";
import Modal from "../components/ui/Modal";
import { LoadingState, EmptyState, ErrorState } from "../components/ui/States";
import { useToast } from "../components/ui/Toast";
import { useFetch } from "../hooks/useFetch";
import { listFlights, createFlight, bulkUploadFlights } from "../api/flights";
import { listAirports } from "../api/airports";

const statusBadge = (status) => {
  if (status === "Finalizado" || status === "Confirmado") return "bg-success/10 text-success border-success/20";
  if (status === "Cancelado")   return "bg-danger/10 text-danger border-danger/20";
  if (status === "En progreso") return "bg-blue-500/10 text-blue-400 border-blue-500/20";
  return "bg-slate-800 text-slate-300 border-slate-700";
};

const occupancyColor = (pct) =>
  pct >= 85 ? "bg-danger" : pct >= 60 ? "bg-warning" : "bg-success";

export default function FlightsPage() {
  const [showAddModal, setShowAddModal] = useState(false);
  const [showUploadModal, setShowUploadModal] = useState(false);
  const { data: flights, loading, error, refetch } = useFetch(listFlights);

  return (
    <div className="flex-1 bg-surface-0 flex flex-col min-h-0 overflow-y-auto w-full h-full p-4 sm:p-8 text-slate-200">
      <div className="flex flex-col lg:flex-row justify-between lg:items-start gap-4 mb-8 pl-12 sm:pl-14">
        <div>
          <h1 className="text-2xl sm:text-4xl font-extrabold text-white mb-2">Tabla de Vuelos</h1>
          <p className="text-slate-400 text-base sm:text-lg">Monitorea y programa la capacidad de los vuelos operativos.</p>
        </div>
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 shrink-0">
          <button type="button" onClick={() => setShowUploadModal(true)}
            className="bg-surface-2 hover:bg-surface-3 border border-slate-700 text-slate-200 px-5 py-2.5 rounded-lg flex items-center gap-2 font-medium transition-colors justify-center">
            <UploadCloud className="w-5 h-5 text-slate-400" /> Carga Masiva
          </button>
          <button type="button" onClick={() => setShowAddModal(true)}
            className="bg-blue-600 hover:bg-blue-500 text-white px-5 py-2.5 rounded-lg flex items-center gap-2 font-medium transition-colors justify-center">
            <Plus className="w-5 h-5" /> Programar Vuelo
          </button>
        </div>
      </div>

      <div className="bg-surface-1 border border-slate-800 rounded-xl overflow-x-auto">
        {loading && <LoadingState label="Cargando vuelos..." />}
        {!loading && error && <ErrorState error={error} onRetry={refetch} />}
        {!loading && !error && flights?.length === 0 && (
          <EmptyState title="Sin vuelos" message="Programa el primer vuelo o sube un archivo en Carga Masiva." />
        )}
        {!loading && !error && flights?.length > 0 && (
          <table className="w-full text-left border-collapse min-w-[720px]">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400 text-sm">
                <th className="py-4 px-6 font-medium">Vuelo</th>
                <th className="py-4 px-6 font-medium">Ruta</th>
                <th className="py-4 px-6 font-medium">Horarios</th>
                <th className="py-4 px-6 font-medium">Estado</th>
                <th className="py-4 px-6 font-medium min-w-[200px]">Ocupación</th>
                <th className="py-4 px-6 font-medium text-right">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {flights.map((fl) => {
                const pct = Math.round((fl.used / fl.capacity) * 100);
                return (
                  <tr key={fl.id} className="border-b border-slate-800/50 hover:bg-surface-2 transition-colors group">
                    <td className="py-4 px-6 font-bold text-blue-400">{fl.id}</td>
                    <td className="py-4 px-6 text-slate-300 font-medium">{fl.origin} ➔ {fl.dest}</td>
                    <td className="py-4 px-6 text-xs text-slate-400 space-y-1">
                       <div className="flex items-center gap-1"><Clock className="w-3 h-3 text-slate-400"/> <span className="font-medium text-slate-300">Salida: {fl.depTime}</span></div>
                       <div className="pl-4">Llegada: {fl.arrTime}</div>
                    </td>
                    <td className="py-4 px-6">
                      <span className={`px-3 py-1.5 rounded text-xs font-bold border ${statusBadge(fl.status)}`}>
                        {fl.status}
                      </span>
                    </td>
                    <td className="py-4 px-6">
                      <div className="flex flex-col gap-1.5">
                        <div className="flex justify-between text-xs text-slate-400">
                          <span>{fl.used} / {fl.capacity}</span>
                          <span className="font-bold">{pct}%</span>
                        </div>
                        <div className="w-full h-1.5 bg-surface-2 border border-slate-800 rounded-full overflow-hidden">
                          <div className={`h-full ${occupancyColor(pct)}`} style={{ width: `${pct}%` }}></div>
                        </div>
                      </div>
                    </td>
                    <td className="py-4 px-6 text-right">
                      <div className="flex justify-end gap-1 opacity-60 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity text-slate-400">
                        <button type="button" aria-label={`Editar ${fl.id}`} className="p-2 rounded-lg hover:bg-surface-2 hover:text-blue-400 transition-colors"><Edit2 className="w-4 h-4" /></button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      <AddFlightModal
        open={showAddModal}
        onClose={() => setShowAddModal(false)}
        onCreated={() => { setShowAddModal(false); refetch(); }}
      />

      <BulkUploadModal
        open={showUploadModal}
        onClose={() => setShowUploadModal(false)}
        onUploaded={() => { setShowUploadModal(false); refetch(); }}
      />
    </div>
  );
}

function AddFlightModal({ open, onClose, onCreated }) {
  const toast = useToast();
  const { data: airports } = useFetch(listAirports);
  const initial = { id: "", origin: "", dest: "", depTime: "", arrTime: "", capacity: 200 };
  const [form, setForm] = useState(initial);
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));

  const sameContinent = () => {
    if (!airports) return null;
    const o = airports.find((a) => a.iata === form.origin);
    const d = airports.find((a) => a.iata === form.dest);
    if (!o || !d) return null;
    return o.continent === d.continent;
  };

  const validate = () => {
    const e = {};
    if (!form.id.trim())     e.id     = "Código requerido";
    if (!form.origin)        e.origin = "Selecciona aeropuerto de salida";
    if (!form.dest)          e.dest   = "Selecciona aeropuerto de llegada";
    if (form.origin && form.origin === form.dest) e.dest = "Origen y destino deben diferir";
    if (!form.depTime)       e.depTime = "Hora de salida requerida";
    if (!form.arrTime)       e.arrTime = "Hora de llegada requerida";

    const cap = Number(form.capacity);
    const sc = sameContinent();
    const min = 150, max = sc === false ? 400 : 250;
    if (!Number.isFinite(cap) || cap < min || cap > max) {
      e.capacity = sc === false
        ? "Distinto continente: 150-400 maletas"
        : "Mismo continente: 150-250 maletas";
    }
    return e;
  };

  const handleSubmit = async (ev) => {
    ev.preventDefault();
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length > 0) return;

    setSubmitting(true);
    try {
      await createFlight({
        id:       form.id.trim().toUpperCase(),
        origin:   form.origin,
        dest:     form.dest,
        depTime:  form.depTime,
        arrTime:  form.arrTime,
        capacity: Number(form.capacity),
      });
      toast.push({ type: "success", title: "Vuelo programado", message: `${form.id.toUpperCase()} ${form.origin} ➔ ${form.dest}` });
      setForm(initial);
      setErrors({});
      onCreated();
    } catch (err) {
      toast.push({ type: "error", title: "No se pudo programar", message: err.message });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="Programar Nuevo Vuelo" maxWidth="max-w-3xl">
      <form onSubmit={handleSubmit} className="p-6 sm:p-8 overflow-y-auto">
        <h2 className="text-2xl font-bold text-white mb-6">Programar Nuevo Vuelo</h2>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-x-6 gap-y-5">
          <Field label="Código de Vuelo" id="fl-id" error={errors.id}>
            <input id="fl-id" type="text" value={form.id}
              onChange={(e) => set("id", e.target.value.toUpperCase())}
              placeholder="LA201" className={inputCls(errors.id)} />
          </Field>

          <Field label="Aeropuerto de Salida" id="fl-origin" error={errors.origin}>
            <select id="fl-origin" value={form.origin} onChange={(e) => set("origin", e.target.value)} className={inputCls(errors.origin)}>
              <option value="">Seleccionar origen</option>
              {(airports ?? []).map((a) => (
                <option key={a.iata} value={a.iata}>{a.iata} — {a.city}</option>
              ))}
            </select>
          </Field>

          <Field label="Hora de Salida" id="fl-dep" error={errors.depTime}>
            <input id="fl-dep" type="time" value={form.depTime}
              onChange={(e) => set("depTime", e.target.value)} className={inputCls(errors.depTime)} />
          </Field>

          <Field
            label="Capacidad de Maletas"
            id="fl-cap"
            error={errors.capacity}
            hint={sameContinent() === false ? "150-400 (distinto continente)" : "150-250 (mismo continente)"}
          >
            <input id="fl-cap" type="number" min={150} max={400} value={form.capacity}
              onChange={(e) => set("capacity", e.target.value)} className={inputCls(errors.capacity)} />
          </Field>

          <Field label="Aeropuerto de Llegada" id="fl-dest" error={errors.dest}>
            <select id="fl-dest" value={form.dest} onChange={(e) => set("dest", e.target.value)} className={inputCls(errors.dest)}>
              <option value="">Seleccionar destino</option>
              {(airports ?? []).map((a) => (
                <option key={a.iata} value={a.iata}>{a.iata} — {a.city}</option>
              ))}
            </select>
          </Field>

          <Field label="Hora de Llegada" id="fl-arr" error={errors.arrTime}>
            <input id="fl-arr" type="time" value={form.arrTime}
              onChange={(e) => set("arrTime", e.target.value)} className={inputCls(errors.arrTime)} />
          </Field>
        </div>

        <div className="mt-8 flex justify-end gap-3 border-t border-slate-800 pt-6">
          <button type="button" onClick={onClose} className="font-bold text-slate-300 hover:text-white px-4 py-2 text-sm">Cancelar</button>
          <button type="submit" disabled={submitting} className="bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-bold px-6 py-2.5 rounded-lg transition-colors text-sm">
            {submitting ? "Guardando..." : "Guardar Vuelo"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function BulkUploadModal({ open, onClose, onUploaded }) {
  const toast = useToast();
  const [file, setFile] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (ev) => {
    ev.preventDefault();
    if (!file) {
      toast.push({ type: "warning", title: "Falta archivo", message: "Selecciona un .txt con los vuelos." });
      return;
    }
    setSubmitting(true);
    try {
      const text = await file.text();
      const result = await bulkUploadFlights(text);
      toast.push({
        type: "success",
        title: "Carga masiva procesada",
        message: `${result.accepted ?? 0} aceptados / ${result.rejected ?? 0} rechazados`,
      });
      setFile(null);
      onUploaded();
    } catch (err) {
      toast.push({ type: "error", title: "Error en carga masiva", message: err.message });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="Carga Masiva de Vuelos" maxWidth="max-w-md">
      <form onSubmit={handleSubmit} className="p-6">
        <h3 className="text-xl font-bold text-white mb-1">Carga Masiva de Vuelos</h3>
        <p className="text-xs text-slate-400 mb-6">Sube un archivo .txt con un vuelo por línea en el formato del back.</p>

        <label htmlFor="bulk-file" className="block mb-2 text-sm font-bold text-white">Archivo (.txt)</label>
        <input
          id="bulk-file"
          type="file"
          accept=".txt,text/plain"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          className="block w-full text-sm text-slate-300 file:mr-3 file:py-1.5 file:px-3 file:rounded file:border-0 file:bg-surface-3 file:text-slate-200 hover:file:bg-surface-2 cursor-pointer"
        />
        {file && <p className="mt-2 text-xs text-slate-400">{file.name} · {Math.ceil(file.size / 1024)} KB</p>}

        <div className="mt-6 flex justify-end gap-3">
          <button type="button" onClick={onClose} className="font-bold text-slate-300 hover:text-white px-4 py-2 text-sm">Cancelar</button>
          <button type="submit" disabled={submitting} className="bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-bold px-6 py-2.5 rounded-lg transition-colors text-sm">
            {submitting ? "Subiendo..." : "Subir Archivo"}
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
