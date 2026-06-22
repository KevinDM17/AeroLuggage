import { memo, useEffect, useMemo, useState } from "react";
import { useOutletContext, useNavigate } from "react-router-dom";
import { FileUp, Plus, Eye, Plane, Ban } from "lucide-react";
import PedidoModal from "../components/simulator/PedidoModal";
import AirportFormModal from "../components/simulator/AirportFormModal";
import BulkPedidoModal from "../components/simulator/BulkPedidoModal";
import Modal from "../components/ui/Modal";
import { LoadingState, EmptyState, ErrorState } from "../components/ui/States";
import { useToast } from "../components/ui/Toast";
import {
  procesarPedidoOperacionesDiaADia,
  procesarPedidosBulkOperacionesDiaADia,
  obtenerPedidosOperacionesDiaADia,
  obtenerMaletasOperacionesDiaADia,
  cancelarVueloProgramadoOperacionesDiaADia,
  obtenerRutasOperacionesDiaADia,
  crearAeropuertoOperacionesDiaADia,
  eliminarAeropuertoOperacionesDiaADia,
  obtenerAeropuertosOperacionesDiaADia,
} from "../api/simulator";
import { adaptAirport } from "../api/airports";
import { listFlightPlans } from "../api/flights";

const colorByOccupancy = (pct) =>
  pct >= 85 ? "bg-danger" : pct >= 60 ? "bg-warning" : "bg-success";

const parseUtcDateTime = (value) => {
  if (!value) return Number.NaN;
  const raw = String(value).trim();
  const normalized = /z$/i.test(raw) ? raw : `${raw}Z`;
  return Date.parse(normalized);
};

const padDatePart = (value) => String(value).padStart(2, "0");

const formatLocalDateLabel = (utcLikeMs) => {
  const date = new Date(utcLikeMs);
  return `${padDatePart(date.getUTCDate())}/${padDatePart(date.getUTCMonth() + 1)}/${date.getUTCFullYear()}`;
};

const buildScheduledFlightTiming = (flightPlan, simTime) => {
  const utcMs = parseUtcDateTime(simTime);
  const gmtOrigin = Number(flightPlan?.gmtOrigin ?? 0);
  const depRaw = String(flightPlan?.depTime ?? "").trim();
  const [depHourRaw, depMinuteRaw] = depRaw.split(":");
  const depHour = Number(depHourRaw);
  const depMinute = Number(depMinuteRaw);

  if (!Number.isFinite(utcMs) || !Number.isFinite(depHour) || !Number.isFinite(depMinute)) {
    return {
      resolved: false,
      statusLabel: "Hora no disponible",
      effectiveDateLabel: "--",
      confirmMessage: "",
    };
  }

  const simAtOriginMs = utcMs + gmtOrigin * 60 * 60 * 1000;
  const simAtOrigin = new Date(simAtOriginMs);
  const departureTodayOriginMs = Date.UTC(
    simAtOrigin.getUTCFullYear(),
    simAtOrigin.getUTCMonth(),
    simAtOrigin.getUTCDate(),
    depHour,
    depMinute,
    0,
    0,
  );
  const cutoffOriginMs = departureTodayOriginMs - 60 * 60 * 1000;
  const appliesToday = simAtOriginMs <= cutoffOriginMs;
  const effectiveOriginMs = departureTodayOriginMs + (appliesToday ? 0 : 24 * 60 * 60 * 1000);
  const effectiveDateLabel = formatLocalDateLabel(effectiveOriginMs);
  const statusLabel = appliesToday
    ? `Aplica hoy · ${effectiveDateLabel}`
    : `Aplica manana · ${effectiveDateLabel}`;

  return {
    resolved: true,
    statusLabel,
    effectiveDateLabel,
    confirmMessage: `Se cancelara la salida ${flightPlan.origin} -> ${flightPlan.dest} de las ${depRaw} para el ${effectiveDateLabel}.`,
  };
};

function InfoBlock({ label, value }) {
  return (
    <div className="rounded-xl border border-slate-800/80 bg-slate-950/30 px-3 py-2">
      <div className="text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-500">{label}</div>
      <div className="mt-1 text-sm font-medium text-slate-200">{value}</div>
    </div>
  );
}

const ScheduledFlightPlanItem = memo(function ScheduledFlightPlanItem({
  flightPlan,
  simTime,
  onCancel,
  canceling,
}) {
  const timing = useMemo(() => buildScheduledFlightTiming(flightPlan, simTime), [flightPlan, simTime]);

  return (
    <div className="rounded-2xl border border-slate-800 bg-slate-900/40 p-4 transition-colors hover:border-slate-700">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <div className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500">
            Vuelo programado
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-lg font-semibold text-white">
            <span>{flightPlan.origin}</span>
            <span className="text-slate-500">{"->"}</span>
            <span>{flightPlan.dest}</span>
          </div>
          <div className="mt-2 text-sm font-medium text-blue-400">{flightPlan.idVueloProgramado ?? flightPlan.id}</div>
          <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <InfoBlock label="Salida" value={`${flightPlan.depTime ?? "--"} (GMT${Number(flightPlan.gmtOrigin) >= 0 ? "+" : ""}${flightPlan.gmtOrigin ?? 0})`} />
            <InfoBlock label="Llegada" value={`${flightPlan.arrTime ?? "--"} (GMT${Number(flightPlan.gmtDest) >= 0 ? "+" : ""}${flightPlan.gmtDest ?? 0})`} />
            <InfoBlock label="Capacidad" value={`${flightPlan.capacity ?? 0} maletas`} />
            <InfoBlock label="Aplicacion" value={timing.statusLabel} />
          </div>
        </div>

        <button
          type="button"
          onClick={() => onCancel?.(flightPlan, timing)}
          disabled={canceling || !timing.resolved}
          className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-xs font-bold uppercase tracking-wide text-danger transition-colors hover:bg-danger/20 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <Ban className="h-3.5 w-3.5" />
          {canceling ? "Cancelando" : "Cancelar"}
        </button>
      </div>
    </div>
  );
});

export default function AirportsPage() {
  const toast = useToast();
  const navigate = useNavigate();
  const { simulationPanelData, setSimulationPanelData } = useOutletContext();

  const airports = simulationPanelData?.airports ?? [];
  const loaded = simulationPanelData?.loaded;
  const loading = !loaded;
  const error = null;

  const [pedidoOpen, setPedidoOpen] = useState(false);
  const [pedidoAirport, setPedidoAirport] = useState(null);
  const [pedidoLoading, setPedidoLoading] = useState(false);

  const [bulkOpen, setBulkOpen] = useState(false);
  const [bulkAirport, setBulkAirport] = useState(null);
  const [bulkLoading, setBulkLoading] = useState(false);
  const [bulkFile, setBulkFile] = useState(null);

  const [formOpen, setFormOpen] = useState(false);
  const [formLoading, setFormLoading] = useState(false);
  const [flightPlansModalAirport, setFlightPlansModalAirport] = useState(null);
  const [airportFlightPlans, setAirportFlightPlans] = useState([]);
  const [airportFlightPlansStatus, setAirportFlightPlansStatus] = useState("idle");
  const [cancelingFlightPlanId, setCancelingFlightPlanId] = useState(null);
  const [flightPlanConfirmation, setFlightPlanConfirmation] = useState(null);
  const [modalReferenceTime, setModalReferenceTime] = useState(null);

  const handlePedidoSubmit = async (pedido) => {
    setPedidoLoading(true);
    try {
      await procesarPedidoOperacionesDiaADia(pedido);
      const [pedidosData, maletasData] = await Promise.all([
        obtenerPedidosOperacionesDiaADia().catch(() => []),
        obtenerMaletasOperacionesDiaADia().catch(() => []),
      ]);
      setSimulationPanelData((prev) => {
        const orders = new Map(prev.orders);
        for (const o of pedidosData ?? []) orders.set(o.id ?? o.idPedido, o);
        const bags = new Map(prev.bags);
        for (const b of maletasData ?? []) bags.set(b.idMaleta, { ...b, ticksAusente: 0 });
        return { ...prev, orders, bags };
      });
      setPedidoOpen(false);
      toast.push({ type: "success", title: "Pedido enviado", message: `${pedido.cantidadMaletas} maletas desde ${pedido.idAeropuertoOrigen} a ${pedido.idAeropuertoDestino}` });
    } catch (err) {
      toast.push({ type: "error", title: "Error al enviar pedido", message: err.message });
    } finally {
      setPedidoLoading(false);
    }
  };

  const handleBulkSubmit = async (e) => {
    e.preventDefault();
    if (!bulkFile) {
      toast.push({ type: "warning", title: "Falta archivo", message: "Selecciona un .txt con los pedidos." });
      return;
    }
    setBulkLoading(true);
    try {
      const text = await bulkFile.text();
      const result = await procesarPedidosBulkOperacionesDiaADia(bulkAirport.iata, text);
      const [pedidosData, maletasData] = await Promise.all([
        obtenerPedidosOperacionesDiaADia().catch(() => []),
        obtenerMaletasOperacionesDiaADia().catch(() => []),
      ]);
      setSimulationPanelData((prev) => {
        const orders = new Map(prev.orders);
        for (const o of pedidosData ?? []) orders.set(o.id ?? o.idPedido, o);
        const bags = new Map(prev.bags);
        for (const b of maletasData ?? []) bags.set(b.idMaleta, { ...b, ticksAusente: 0 });
        return { ...prev, orders, bags };
      });
      toast.push({
        type: "success",
        title: "Carga masiva procesada",
        message: `${result.accepted ?? 0} pedidos aceptados`,
      });
      setBulkFile(null);
      setBulkOpen(false);
    } catch (err) {
      toast.push({ type: "error", title: "Error en carga masiva", message: err.message });
    } finally {
      setBulkLoading(false);
    }
  };

  const openPedido = (airport) => {
    setPedidoAirport(airport);
    setPedidoOpen(true);
  };

  const openBulk = (airport) => {
    setBulkAirport(airport);
    setBulkFile(null);
    setBulkOpen(true);
  };

  const openCreate = () => {
    setFormOpen(true);
  };

  const handleFormSubmit = async (payload) => {
    setFormLoading(true);
    try {
      await crearAeropuertoOperacionesDiaADia(payload);
      const raw = await obtenerAeropuertosOperacionesDiaADia().catch(() => []);
      const aeropuertos = Array.isArray(raw) ? raw.map(adaptAirport) : [];
      setSimulationPanelData((prev) => ({
        ...prev,
        airports: aeropuertos,
      }));
      setFormOpen(false);
      toast.push({
        type: "success",
        title: "Aeropuerto creado",
        message: `${payload.idAeropuerto} creado correctamente`,
      });
    } catch (err) {
      toast.push({ type: "error", title: "Error", message: err.message });
    } finally {
      setFormLoading(false);
    }
  };

  const handleDelete = async (airport) => {
    if (!window.confirm(`Eliminar ${airport.iata} - ${airport.city}?`)) return;
    try {
      await eliminarAeropuertoOperacionesDiaADia(airport.iata);
      const raw = await obtenerAeropuertosOperacionesDiaADia().catch(() => []);
      const aeropuertos = Array.isArray(raw) ? raw.map(adaptAirport) : [];
      setSimulationPanelData((prev) => ({
        ...prev,
        airports: aeropuertos,
      }));
      toast.push({ type: "success", title: "Aeropuerto eliminado", message: airport.iata });
    } catch (err) {
      toast.push({ type: "error", title: "Error al eliminar", message: err.message });
    }
  };

  useEffect(() => {
    if (!flightPlansModalAirport) return undefined;
    let cancelled = false;
    setAirportFlightPlansStatus("loading");
    listFlightPlans(flightPlansModalAirport.iata)
      .then((data) => {
        if (cancelled) return;
        setAirportFlightPlans(Array.isArray(data) ? data : []);
        setAirportFlightPlansStatus("ready");
      })
      .catch(() => {
        if (cancelled) return;
        setAirportFlightPlans([]);
        setAirportFlightPlansStatus("error");
      });
    return () => {
      cancelled = true;
    };
  }, [flightPlansModalAirport]);

  const closeFlightPlansModal = () => {
    setFlightPlansModalAirport(null);
    setAirportFlightPlans([]);
    setAirportFlightPlansStatus("idle");
    setCancelingFlightPlanId(null);
    setFlightPlanConfirmation(null);
    setModalReferenceTime(null);
  };

  const openFlightPlansModal = (airport) => {
    setFlightPlansModalAirport(airport);
    setAirportFlightPlans([]);
    setAirportFlightPlansStatus("idle");
    setCancelingFlightPlanId(null);
    setFlightPlanConfirmation(null);
    setModalReferenceTime(new Date().toISOString());
  };

  const refreshAirportFlightPlans = async (airport) => {
    setAirportFlightPlansStatus("loading");
    try {
      const data = await listFlightPlans(airport.iata);
      setAirportFlightPlans(Array.isArray(data) ? data : []);
      setAirportFlightPlansStatus("ready");
    } catch {
      setAirportFlightPlans([]);
      setAirportFlightPlansStatus("error");
    }
  };

  const handleCancelFlightPlan = (flightPlan, timing) => {
    if (!timing?.resolved) {
      toast.push({
        type: "warning",
        title: "Hora no disponible",
        message: "No se pudo resolver la fecha operativa para esta cancelacion.",
      });
      return;
    }
    setFlightPlanConfirmation({ flightPlan, timing });
  };

  const confirmFlightPlanCancellation = async () => {
    if (!flightPlanConfirmation) return;
    const { flightPlan, timing } = flightPlanConfirmation;
    const flightPlanId = flightPlan?.idVueloProgramado ?? flightPlan?.id;
    if (!flightPlanId) return;

    setCancelingFlightPlanId(flightPlanId);
    try {
      await cancelarVueloProgramadoOperacionesDiaADia(flightPlanId);
      const [rutasData, maletasData] = await Promise.all([
        obtenerRutasOperacionesDiaADia().catch(() => []),
        obtenerMaletasOperacionesDiaADia().catch(() => []),
      ]);
      setSimulationPanelData((prev) => {
        const bagOrigen = new Map();
        const bagDestino = new Map();
        for (const r of rutasData) {
          const first = r?.vuelos?.[0];
          const last = r?.vuelos?.[r.vuelos.length - 1];
          if (r.idMaleta) {
            if (first?.aeropuertoOrigen) bagOrigen.set(r.idMaleta, first.aeropuertoOrigen);
            if (last?.aeropuertoDestino) bagDestino.set(r.idMaleta, last.aeropuertoDestino);
          }
        }
        const routes = new Map();
        for (const r of rutasData) routes.set(r.idRuta, { ...r, ticksAusente: 0 });
        const bags = new Map(prev.bags);
        for (const m of maletasData) {
          const existing = bags.get(m.idMaleta);
          bags.set(m.idMaleta, {
            ...m,
            origen: bagOrigen.get(m.idMaleta) ?? existing?.origen ?? null,
            destino: bagDestino.get(m.idMaleta) ?? existing?.destino ?? null,
            ticksAusente: 0,
          });
        }
        return { ...prev, routes, bags };
      });
      toast.push({
        type: "success",
        title: "Vuelo cancelado",
        message: `${flightPlan.origin} -> ${flightPlan.dest} · ${timing.effectiveDateLabel}`,
      });
      closeFlightPlansModal();
    } catch (err) {
      toast.push({ type: "error", title: "Error al cancelar", message: err.message });
      setFlightPlanConfirmation(null);
    } finally {
      setCancelingFlightPlanId(null);
    }
  };

  const flightPlansModal = (
    <Modal
      open={Boolean(flightPlansModalAirport)}
      onClose={closeFlightPlansModal}
      title={`Vuelos programados de ${flightPlansModalAirport?.iata ?? ""}`}
      maxWidth="max-w-4xl"
    >
      <div className="flex max-h-[80vh] flex-col">
        <div className="border-b border-slate-800 px-6 py-5 pr-16">
          <h3 className="text-lg font-semibold text-slate-100">
            Vuelos programados de {flightPlansModalAirport?.iata ?? "--"}
          </h3>
          <p className="mt-2 text-sm leading-relaxed text-slate-400">
            Revisa los vuelos programados de este aeropuerto y registra su cancelacion usando la logica de operaciones dia a dia.
          </p>
        </div>

        <div className="app-scrollbar overflow-y-auto px-6 py-5">
          {airportFlightPlansStatus === "loading" ? (
            <LoadingState label="Cargando vuelos programados..." />
          ) : airportFlightPlansStatus === "error" ? (
            <ErrorState
              error={{ message: "No se pudieron cargar los vuelos programados de este aeropuerto." }}
              onRetry={() => refreshAirportFlightPlans(flightPlansModalAirport)}
            />
          ) : airportFlightPlans.length === 0 ? (
            <EmptyState title="Sin vuelos programados" message="Este aeropuerto no tiene vuelos programados registrados." />
          ) : (
            <div className="space-y-3">
              {airportFlightPlans.map((flightPlan, index) => {
                const key = flightPlan.idVueloProgramado ?? flightPlan.id ?? `${flightPlan.origin}-${flightPlan.dest}-${index}`;
                return (
                  <ScheduledFlightPlanItem
                    key={key}
                    flightPlan={flightPlan}
                    simTime={modalReferenceTime}
                    onCancel={handleCancelFlightPlan}
                    canceling={cancelingFlightPlanId === (flightPlan.idVueloProgramado ?? flightPlan.id)}
                  />
                );
              })}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );

  const flightPlanConfirmationModal = (
    <Modal
      open={Boolean(flightPlanConfirmation)}
      onClose={() => {
        if (cancelingFlightPlanId) return;
        setFlightPlanConfirmation(null);
      }}
      title="Confirmar cancelacion de vuelo programado"
      maxWidth="max-w-2xl"
    >
      <div className="flex max-h-[80vh] flex-col">
        <div className="border-b border-slate-800 px-6 py-5 pr-16">
          <p className="text-xs font-semibold uppercase tracking-[0.24em] text-warning">Confirmacion</p>
          <h3 className="mt-2 text-2xl font-semibold text-slate-100">Cancelar vuelo programado</h3>
          <p className="mt-3 text-sm leading-relaxed text-slate-400">
            Se usara la logica de cancelacion de operaciones dia a dia para registrar la ocurrencia correspondiente.
          </p>
        </div>

        <div className="space-y-5 overflow-y-auto px-6 py-5">
          <div className="rounded-2xl border border-slate-700/80 bg-slate-900/40 p-5">
            <div className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">Vuelo seleccionado</div>
            <div className="mt-3 flex flex-wrap items-center gap-3 text-lg font-semibold text-white">
              <span>{flightPlanConfirmation?.flightPlan?.origin ?? "--"}</span>
              <span className="text-slate-500">{"->"}</span>
              <span>{flightPlanConfirmation?.flightPlan?.dest ?? "--"}</span>
            </div>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <InfoBlock label="Codigo" value={flightPlanConfirmation?.flightPlan?.idVueloProgramado ?? flightPlanConfirmation?.flightPlan?.id ?? "--"} />
              <InfoBlock label="Fecha operativa estimada" value={flightPlanConfirmation?.timing?.effectiveDateLabel ?? "--"} />
            </div>
            <p className="mt-4 text-sm leading-relaxed text-slate-400">
              {flightPlanConfirmation?.timing?.confirmMessage ?? ""}
            </p>
          </div>
        </div>

        <div className="flex justify-end gap-3 border-t border-slate-800 px-6 py-4">
          <button
            type="button"
            onClick={() => setFlightPlanConfirmation(null)}
            disabled={Boolean(cancelingFlightPlanId)}
            className="inline-flex items-center justify-center rounded-lg border border-slate-700 bg-surface-2 px-4 py-2.5 text-sm font-semibold text-slate-200 transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
          >
            Volver
          </button>
          <button
            type="button"
            onClick={confirmFlightPlanCancellation}
            disabled={Boolean(cancelingFlightPlanId)}
            className="inline-flex items-center justify-center rounded-lg border border-danger/50 bg-danger/15 px-4 py-2.5 text-sm font-semibold text-danger transition-colors hover:bg-danger/20 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {cancelingFlightPlanId ? "Cancelando..." : "Confirmar cancelacion"}
          </button>
        </div>
      </div>
    </Modal>
  );

  return (
    <div className="app-scrollbar flex-1 bg-surface-0 flex flex-col min-h-0 overflow-y-auto w-full h-full p-4 sm:p-8 text-slate-200">
      <div className="flex flex-col sm:flex-row justify-between sm:items-start gap-4 mb-8 pl-12 sm:pl-14">
        <div>
          <h1 className="text-2xl sm:text-4xl font-extrabold text-white mb-2">Tabla de Aeropuertos</h1>
          <p className="text-slate-400 text-base sm:text-lg">Gestiona la capacidad y estado de los nodos logisticos globales.</p>
        </div>
        <button
          type="button"
          onClick={openCreate}
          className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded-lg font-medium text-sm transition-colors"
        >
          <Plus className="w-4 h-4" /> Agregar Aeropuerto
        </button>
      </div>

      <div className="bg-surface-1 border border-slate-800 rounded-xl overflow-x-auto">
        {loading && <LoadingState label="Cargando aeropuertos..." />}
        {!loading && error && <ErrorState error={error} />}
        {!loading && !error && airports.length === 0 && (
          <EmptyState title="Sin aeropuertos" message="No hay aeropuertos disponibles en la sesion activa." />
        )}
        {!loading && !error && airports.length > 0 && (
          <table className="w-full text-left border-collapse min-w-[720px]">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400 text-sm">
                <th className="py-4 px-6 font-medium">IATA</th>
                <th className="py-4 px-6 font-medium">Ciudad</th>
                <th className="py-4 px-6 font-medium">Continente</th>
                <th className="py-4 px-6 font-medium min-w-[260px]">Ocupacion</th>
                <th className="py-4 px-6 font-medium text-right">Acciones</th>
                <th className="py-4 px-6 font-medium text-center w-24">Detalles</th>
              </tr>
            </thead>
            <tbody>
              {airports.map((apt) => {
                const capacity = apt.capacity ?? 1;
                const pct = Math.round((apt.used / capacity) * 100);
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
                          <div>
                            <div className={`h-full ${colorByOccupancy(pct)}`} style={{ width: `${pct}%` }}></div>
                            <span className="absolute right-0 text-[10px] text-slate-400">{apt.capacity}</span>
                          </div>
                        </div>
                      </div>
                    </td>
                    <td className="py-4 px-6 text-right">
                      <div className="flex justify-end gap-1 opacity-60 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
                        <button
                          type="button"
                          onClick={() => openFlightPlansModal(apt)}
                          title="Ver vuelos programados"
                          className="p-2 rounded-lg hover:bg-surface-2 hover:text-blue-400 transition-colors text-slate-400"
                        >
                          <Plane className="w-4 h-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => openBulk(apt)}
                          title="Carga masiva de pedidos"
                          className="p-2 rounded-lg hover:bg-surface-2 hover:text-blue-400 transition-colors text-slate-400"
                        >
                          <FileUp className="w-4 h-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => openPedido(apt)}
                          title="Agregar pedido"
                          className="p-2 rounded-lg hover:bg-surface-2 hover:text-blue-400 transition-colors text-slate-400"
                        >
                          <Plus className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                    <td className="py-4 px-6 text-center">
                      <button
                        type="button"
                        onClick={() => navigate(`/airports/${apt.iata}`)}
                        title="Mostrar detalles"
                        className="p-2 rounded-lg hover:bg-blue-600/20 hover:text-blue-400 transition-colors text-slate-400"
                      >
                        <Eye className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      <PedidoModal
        open={pedidoOpen}
        airports={airports}
        onClose={() => setPedidoOpen(false)}
        onSubmit={handlePedidoSubmit}
        loading={pedidoLoading}
        lockedOrigin={pedidoAirport?.iata ?? null}
      />

      <AirportFormModal
        open={formOpen}
        initialData={null}
        onClose={() => setFormOpen(false)}
        onSubmit={handleFormSubmit}
        loading={formLoading}
      />

      <BulkPedidoModal
        open={bulkOpen}
        airport={bulkAirport}
        file={bulkFile}
        loading={bulkLoading}
        onFileChange={setBulkFile}
        onClose={() => setBulkOpen(false)}
        onSubmit={handleBulkSubmit}
      />
      {flightPlansModal}
      {flightPlanConfirmationModal}
    </div>
  );
}
