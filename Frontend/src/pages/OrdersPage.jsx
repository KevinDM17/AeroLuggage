import { useMemo, useState } from "react";
import { Search, Plus, Calendar, Clock } from "lucide-react";
import Modal from "../components/ui/Modal";
import { LoadingState, EmptyState, ErrorState } from "../components/ui/States";
import { useToast } from "../components/ui/Toast";
import { useFetch } from "../hooks/useFetch";
import { listOrders, createOrder } from "../api/orders";
import { listAirports } from "../api/airports";

const statusBadge = (status) => {
  if (status === "Enviado")    return "border-success/30 text-success bg-transparent";
  if (status === "Pendiente")  return "border-warning/30 text-warning bg-transparent";
  if (status === "Procesando") return "border-blue-500/30 text-blue-400 bg-transparent";
  return "border-slate-700 text-slate-400";
};

const todayISO = () => new Date().toISOString().slice(0, 10);
const nowHHMM  = () => new Date().toTimeString().slice(0, 5);

export default function OrdersPage() {
  const [showAddModal, setShowAddModal] = useState(false);
  const [query, setQuery] = useState("");
  const { data: orders, loading, error, refetch } = useFetch(listOrders);

  const filtered = useMemo(() => {
    if (!orders) return [];
    const q = query.trim().toLowerCase();
    if (!q) return orders;
    return orders.filter((o) =>
      [o.id, o.clientId, o.origin, o.dest].some((v) => String(v).toLowerCase().includes(q))
    );
  }, [orders, query]);

  return (
    <div className="flex-1 bg-surface-0 flex flex-col min-h-0 overflow-y-auto w-full h-full p-4 sm:p-8 text-slate-200">
      <div className="flex flex-col sm:flex-row justify-between sm:items-start gap-4 mb-8 pl-12 sm:pl-14">
        <div>
          <h1 className="text-2xl sm:text-4xl font-extrabold text-white mb-2">Tabla de Pedidos</h1>
          <p className="text-slate-400 text-base sm:text-lg">Registra y administra los envíos masivos de maletas por cliente.</p>
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
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Buscar por ID, cliente, origen o destino..."
              className="w-full bg-surface-2 border border-slate-800 rounded-full pl-11 pr-4 py-2.5 text-sm outline-none focus:border-blue-500 text-white placeholder-slate-400"
            />
          </div>
        </div>

        {loading && <LoadingState label="Cargando pedidos..." />}
        {!loading && error && <ErrorState error={error} onRetry={refetch} />}
        {!loading && !error && orders?.length === 0 && (
          <EmptyState title="Sin pedidos" message="Registra el primer envío con el botón de arriba." />
        )}
        {!loading && !error && orders?.length > 0 && filtered.length === 0 && (
          <EmptyState title="Sin resultados" message={`Ningún pedido coincide con "${query}".`} />
        )}
        {!loading && !error && filtered.length > 0 && (
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
              {filtered.map((ord) => (
                <tr key={ord.id} className="border-b border-slate-800/50 hover:bg-surface-2 transition-colors">
                  <td className="py-5 px-4 font-bold text-blue-400">{ord.id}</td>
                  <td className="py-5 px-4 font-bold text-slate-200 text-sm">{ord.clientId}</td>
                  <td className="py-5 px-4 text-slate-300 text-sm font-medium">{ord.origin} ➔ {ord.dest}</td>
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
                    <span className={`px-4 py-1.5 rounded-full text-xs font-medium border ${statusBadge(ord.status)} inline-block`}>
                      {ord.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <AddOrderModal
        open={showAddModal}
        onClose={() => setShowAddModal(false)}
        onCreated={() => { setShowAddModal(false); refetch(); }}
      />
    </div>
  );
}

function AddOrderModal({ open, onClose, onCreated }) {
  const toast = useToast();
  const { data: airports } = useFetch(listAirports);
  const initial = { origin: "", dest: "", bags: 1, date: todayISO(), time: nowHHMM(), clientId: "" };
  const [form, setForm] = useState(initial);
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));

  const validate = () => {
    const e = {};
    if (!form.origin)            e.origin = "Selecciona aeropuerto de origen";
    if (!form.dest)              e.dest   = "Selecciona aeropuerto de destino";
    if (form.origin && form.origin === form.dest) e.dest = "Origen y destino deben diferir";
    const bags = Number(form.bags);
    if (!Number.isFinite(bags) || bags < 1) e.bags = "Cantidad debe ser ≥ 1";
    if (!form.date)              e.date = "Fecha requerida";
    if (!form.time)              e.time = "Hora requerida";
    if (!form.clientId.trim())   e.clientId = "ID de cliente requerido";
    return e;
  };

  const handleSubmit = async (ev) => {
    ev.preventDefault();
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length > 0) return;

    setSubmitting(true);
    try {
      const created = await createOrder({
        origin:   form.origin,
        dest:     form.dest,
        bags:     Number(form.bags),
        date:     form.date,
        time:     form.time,
        clientId: form.clientId.trim().toUpperCase(),
      });
      toast.push({
        type: "success",
        title: "Pedido registrado",
        message: `${created.id ?? "Envío"}: ${form.bags} maleta(s) ${form.origin} ➔ ${form.dest}`,
      });
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
    <Modal open={open} onClose={onClose} title="Nuevo Registro de Pedido" maxWidth="max-w-lg">
      <form onSubmit={handleSubmit} className="p-6 sm:p-8">
        <div className="flex items-center gap-2 mb-2 text-blue-400 font-bold">
          <div className="w-3 h-3 rounded-full border-2 border-blue-500 bg-transparent"></div>
          Nuevo Registro de Pedido
        </div>
        <p className="text-sm text-slate-400 mb-6">Ingresa los detalles del envío para su procesamiento en la red.</p>

        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Field label="Origen" id="ord-origin" error={errors.origin}>
              <select id="ord-origin" value={form.origin} onChange={(e) => set("origin", e.target.value)} className={inputCls(errors.origin)}>
                <option value="">Origen</option>
                {(airports ?? []).map((a) => <option key={a.iata} value={a.iata}>{a.iata} — {a.city}</option>)}
              </select>
            </Field>

            <Field label="Destino" id="ord-dest" error={errors.dest}>
              <select id="ord-dest" value={form.dest} onChange={(e) => set("dest", e.target.value)} className={inputCls(errors.dest)}>
                <option value="">Destino</option>
                {(airports ?? []).map((a) => <option key={a.iata} value={a.iata}>{a.iata} — {a.city}</option>)}
              </select>
            </Field>
          </div>

          <Field label="Cantidad de Maletas" id="ord-bags" error={errors.bags}>
            <input id="ord-bags" type="number" min={1} value={form.bags}
              onChange={(e) => set("bags", e.target.value)} className={inputCls(errors.bags)} />
          </Field>

          <div className="grid grid-cols-2 gap-4">
            <Field label="Fecha de Registro" id="ord-date" error={errors.date}>
              <input id="ord-date" type="date" value={form.date}
                onChange={(e) => set("date", e.target.value)} className={inputCls(errors.date)} />
            </Field>

            <Field label="Hora de Registro" id="ord-time" error={errors.time}>
              <input id="ord-time" type="time" value={form.time}
                onChange={(e) => set("time", e.target.value)} className={inputCls(errors.time)} />
            </Field>
          </div>

          <Field label="ID de Cliente" id="ord-client" error={errors.clientId}>
            <input id="ord-client" type="text" value={form.clientId}
              onChange={(e) => set("clientId", e.target.value.toUpperCase())}
              placeholder="CUST-000" className={inputCls(errors.clientId)} />
          </Field>
        </div>

        <div className="mt-8 flex justify-end gap-3">
          <button type="button" onClick={onClose} className="font-bold text-slate-300 hover:text-white px-4 py-2 text-sm">Cancelar</button>
          <button type="submit" disabled={submitting} className="bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-bold px-6 py-2.5 rounded-lg transition-colors text-sm">
            {submitting ? "Registrando..." : "Registrar Pedido"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

const inputCls = (hasError) =>
  `bg-surface-1 border ${hasError ? "border-danger" : "border-slate-700"} rounded-lg px-4 py-2.5 outline-none focus:border-blue-500 text-slate-200 text-sm w-full`;

function Field({ label, id, error, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="font-bold text-slate-200 text-xs">{label}</label>
      {children}
      {error && <span className="text-xs text-danger">{error}</span>}
    </div>
  );
}
