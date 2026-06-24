import { useState, useEffect, useMemo } from "react";
import { useParams, useOutletContext } from "react-router-dom";
import { Plus, FileUp, ArrowLeft, Plane } from "lucide-react";
import PedidoModal from "../components/simulator/PedidoModal";
import BulkPedidoModal from "../components/simulator/BulkPedidoModal";
import FlightPlanFormModal from "../components/simulator/FlightPlanFormModal";
import { LoadingState, EmptyState, ErrorState } from "../components/ui/States";
import { useToast } from "../components/ui/Toast";
import { normalizeContinente } from "../api/airports";
import {
  procesarPedidoOperacionesDiaADia,
  procesarPedidosBulkOperacionesDiaADia,
  obtenerPedidosOperacionesDiaADia,
  obtenerMaletasOperacionesDiaADia,
  cancelarVueloProgramadoOperacionesDiaADia,
  obtenerRutasOperacionesDiaADia,
  obtenerVuelosOperacionesDiaADia,
} from "../api/simulator";
import { adaptFlightInstance } from "../api/flightInstances";
import { listFlightPlans, createFlightPlan } from "../api/flights";

const colorByOccupancy = (pct) =>
  pct >= 85 ? "bg-danger" : pct >= 60 ? "bg-warning" : "bg-success";

const formatGMT = (h) => (h >= 0 ? `GMT+${h}` : `GMT${h}`);

const extractTime = (iso) => {
  if (!iso) return "—";
  const parts = String(iso).split("T");
  return parts[1] ? parts[1].slice(0, 5) : "—";
};

export default function AirportDetailPage() {
  const { iata } = useParams();
  const toast = useToast();
  const { simulationPanelData, setSimulationPanelData } = useOutletContext();

  const airports = simulationPanelData?.airports ?? [];
  const airport = useMemo(() => airports.find((a) => a.iata === iata), [airports, iata]);
  const bags = simulationPanelData?.bags;
  const orders = simulationPanelData?.orders;
  const routes = simulationPanelData?.routes;
  const loaded = simulationPanelData?.loaded;

  const [pedidoOpen, setPedidoOpen] = useState(false);
  const [pedidoLoading, setPedidoLoading] = useState(false);

  const [bulkOpen, setBulkOpen] = useState(false);
  const [bulkLoading, setBulkLoading] = useState(false);
  const [bulkFile, setBulkFile] = useState(null);

  const [flightPlans, setFlightPlans] = useState(null);
  const [plansLoading, setPlansLoading] = useState(true);
  const [plansError, setPlansError] = useState(null);
  const [viewMode, setViewMode] = useState("origin");

  const [planFormOpen, setPlanFormOpen] = useState(false);
  const [planFormLoading, setPlanFormLoading] = useState(false);

  const bagsAtAirport = useMemo(() => {
    if (!bags || !iata) return [];
    const result = [];
    for (const bag of bags.values()) {
      if (bag.ubicacionActual === iata) result.push(bag);
    }
    result.sort((a, b) => (a.idMaleta ?? "").localeCompare(b.idMaleta ?? ""));
    return result;
  }, [bags, iata]);

  const ordersAtAirport = useMemo(() => {
    if (!bags || !orders || !iata) return [];
    const orderIds = new Set();
    for (const bag of bags.values()) {
      if (bag.ubicacionActual === iata && bag.idPedido) {
        orderIds.add(bag.idPedido);
      }
    }
    const result = [];
    for (const id of orderIds) {
      const order = orders.get(id);
      if (order) result.push(order);
    }
    result.sort((a, b) => (a.id ?? "").localeCompare(b.id ?? ""));
    return result;
  }, [bags, orders, iata]);

  const nextFlightForBag = useMemo(() => {
    const map = {};
    if (!routes || !iata) return map;
    for (const route of routes.values()) {
      if (!route || route.idMaleta == null || !route.vuelos) continue;
      for (const vuelo of route.vuelos) {
        if (vuelo.aeropuertoOrigen === iata) {
          map[route.idMaleta] = vuelo;
          break;
        }
      }
    }
    return map;
  }, [routes, iata]);

  const loadFlightPlans = async () => {
    setPlansLoading(true);
    try {
      const data = await listFlightPlans();
      setFlightPlans(Array.isArray(data) ? data : []);
      setPlansError(null);
    } catch (err) {
      setPlansError(err.message);
    } finally {
      setPlansLoading(false);
    }
  };

  useEffect(() => { loadFlightPlans(); }, []);

  const filteredFlightPlans = useMemo(() => {
    if (!flightPlans || !iata) return [];
    return flightPlans.filter((f) =>
      viewMode === "origin" ? f.origin === iata : f.dest === iata
    );
  }, [flightPlans, iata, viewMode]);

  const handlePedidoSubmit = async (pedido) => {
    setPedidoLoading(true);
    try {
      await procesarPedidoOperacionesDiaADia(pedido);
      const [pedidosData, maletasData] = await Promise.all([
        obtenerPedidosOperacionesDiaADia().catch(() => []),
        obtenerMaletasOperacionesDiaADia().catch(() => []),
      ]);
      setSimulationPanelData((prev) => {
        const updatedOrders = new Map(prev.orders);
        for (const o of pedidosData ?? []) updatedOrders.set(o.id ?? o.idPedido, o);
        const updatedBags = new Map(prev.bags);
        for (const b of maletasData ?? []) updatedBags.set(b.idMaleta, { ...b, ticksAusente: 0 });
        return { ...prev, orders: updatedOrders, bags: updatedBags };
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
      const result = await procesarPedidosBulkOperacionesDiaADia(iata, text);
      const [pedidosData, maletasData] = await Promise.all([
        obtenerPedidosOperacionesDiaADia().catch(() => []),
        obtenerMaletasOperacionesDiaADia().catch(() => []),
      ]);
      setSimulationPanelData((prev) => {
        const updatedOrders = new Map(prev.orders);
        for (const o of pedidosData ?? []) updatedOrders.set(o.id ?? o.idPedido, o);
        const updatedBags = new Map(prev.bags);
        for (const b of maletasData ?? []) updatedBags.set(b.idMaleta, { ...b, ticksAusente: 0 });
        return { ...prev, orders: updatedOrders, bags: updatedBags };
      });
      toast.push({ type: "success", title: "Carga masiva procesada", message: `${result.accepted ?? 0} pedidos aceptados` });
      setBulkFile(null);
      setBulkOpen(false);
    } catch (err) {
      toast.push({ type: "error", title: "Error en carga masiva", message: err.message });
    } finally {
      setBulkLoading(false);
    }
  };

  const handlePlanSubmit = async (payload) => {
    setPlanFormLoading(true);
    try {
      await createFlightPlan(payload);
      setPlanFormOpen(false);
      await loadFlightPlans();
      toast.push({ type: "success", title: "Plan de vuelo creado", message: `${payload.id} creado correctamente` });
    } catch (err) {
      toast.push({ type: "error", title: "Error", message: err.message });
    } finally {
      setPlanFormLoading(false);
    }
  };

  const handleCancelPlan = async (plan) => {
    try {
      await cancelarVueloProgramadoOperacionesDiaADia(plan.id);
      const [rutasData, maletasData, vuelosData] = await Promise.all([
        obtenerRutasOperacionesDiaADia().catch(() => []),
        obtenerMaletasOperacionesDiaADia().catch(() => []),
        obtenerVuelosOperacionesDiaADia().catch(() => []),
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
        // Re-incluir la lista de vuelos (ya trae CANCELADO) para que el vuelo
        // cancelado aparezca al instante en la pestaña Vuelos.
        const flights = new Map(prev.flights ?? new Map());
        for (const raw of (vuelosData ?? [])) {
          const f = adaptFlightInstance(raw);
          const id = f.idVueloInstancia ?? f.id;
          flights.set(id, { ...flights.get(id), ...f, ticksAusente: 0 });
        }
        return { ...prev, routes, bags, flights };
      });
      await loadFlightPlans();
      toast.push({ type: "success", title: "Vuelo cancelado", message: `${plan.id} cancelado y maletas replanificadas` });
    } catch (err) {
      toast.push({ type: "error", title: "Error al cancelar", message: err.message });
    }
  };

  if (!loaded) {
    return (
      <div className="flex-1 bg-surface-0 flex flex-col min-h-0 w-full h-full p-4 sm:p-8 text-slate-200">
        <LoadingState label="Cargando datos del aeropuerto..." />
      </div>
    );
  }

  if (!airport) {
    return (
      <div className="flex-1 bg-surface-0 flex flex-col min-h-0 w-full h-full p-4 sm:p-8 text-slate-200">
        <ErrorState error={`Aeropuerto "${iata}" no encontrado`} />
      </div>
    );
  }

  const capacity = airport.capacity ?? 1;
  const pct = Math.round((airport.used / capacity) * 100);

  return (
    <div className="app-scrollbar flex-1 bg-surface-0 flex flex-col min-h-0 overflow-y-auto w-full min-h-full p-4 pb-8 sm:p-8 sm:pb-10 text-slate-200">
      <div className="mb-6 pl-12 sm:pl-14">
        <a href="/airports" className="inline-flex items-center gap-2 text-slate-400 hover:text-white text-sm transition-colors">
          <ArrowLeft className="w-4 h-4" /> Volver a Aeropuertos
        </a>
      </div>

      <div className="flex flex-col sm:flex-row justify-between sm:items-start gap-4 mb-8 pl-12 sm:pl-14">
        <div>
          <h1 className="text-2xl sm:text-4xl font-extrabold text-white mb-2">
            {airport.iata} — {airport.city}
          </h1>
          <p className="text-slate-400 text-base sm:text-lg">{airport.continent}</p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setBulkOpen(true)}
            className="flex items-center gap-2 bg-slate-700 hover:bg-slate-600 text-white px-4 py-2 rounded-lg font-medium text-sm transition-colors"
          >
            <FileUp className="w-4 h-4" /> Carga Masiva
          </button>
          <button
            type="button"
            onClick={() => setPedidoOpen(true)}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded-lg font-medium text-sm transition-colors"
          >
            <Plus className="w-4 h-4" /> Agregar Pedido
          </button>
        </div>
      </div>

      <div className="bg-surface-1 border border-slate-800 rounded-xl p-6 mb-8 mx-0 sm:mx-14">
        <h2 className="text-lg font-bold text-white mb-4">Ocupacion del Almacen</h2>
        <div className="flex flex-col gap-2">
          <div className="flex justify-between text-sm text-slate-400 font-medium">
            <span>{airport.used} maletas ocupadas</span>
            <span>{pct}%</span>
          </div>
          <div className="w-full h-3 bg-surface-2 border border-slate-800 rounded-full overflow-hidden flex items-center relative">
            <div className={`h-full ${colorByOccupancy(pct)}`} style={{ width: `${pct}%` }} />
          </div>
          <div className="text-xs text-slate-500 text-right">Capacidad total: {capacity} maletas</div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8 mx-0 sm:mx-14">
        <div className="bg-surface-1 border border-slate-800 rounded-xl overflow-hidden">
          <div className="px-6 py-4 border-b border-slate-800">
            <h2 className="text-lg font-bold text-white">Pedidos en {airport.iata}</h2>
            <p className="text-xs text-slate-400 mt-1">
              {ordersAtAirport.length} pedido{ordersAtAirport.length !== 1 ? "s" : ""} con maletas en este aeropuerto
            </p>
          </div>
          {ordersAtAirport.length === 0 ? (
            <div className="p-6 text-slate-500 text-sm">Sin pedidos en este aeropuerto</div>
          ) : (
            <div className="overflow-auto max-h-64">
              <table className="w-full text-left border-collapse">
                <thead className="sticky top-0 bg-surface-1 z-10">
                  <tr className="border-b border-slate-800 text-slate-400 text-sm">
                    <th className="py-3 px-6 font-medium">Codigo</th>
                    <th className="py-3 px-6 font-medium">Origen</th>
                    <th className="py-3 px-6 font-medium">Destino</th>
                  </tr>
                </thead>
                <tbody>
                  {ordersAtAirport.map((order) => (
                    <tr key={order.id} className="border-b border-slate-800/50 hover:bg-surface-2 transition-colors">
                      <td className="py-3 px-6 font-bold text-blue-400 text-sm">{order.id}</td>
                      <td className="py-3 px-6 text-slate-300 text-sm">{order.origin}</td>
                      <td className="py-3 px-6 text-slate-300 text-sm">{order.dest}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="bg-surface-1 border border-slate-800 rounded-xl overflow-hidden">
          <div className="px-6 py-4 border-b border-slate-800">
            <h2 className="text-lg font-bold text-white">Maletas en {airport.iata}</h2>
            <p className="text-xs text-slate-400 mt-1">
              {bagsAtAirport.length} maleta{bagsAtAirport.length !== 1 ? "s" : ""} en este aeropuerto
            </p>
          </div>
          {bagsAtAirport.length === 0 ? (
            <div className="p-6 text-slate-500 text-sm">Sin maletas en este aeropuerto</div>
          ) : (
            <div className="overflow-auto max-h-64">
              <table className="w-full text-left border-collapse">
                <thead className="sticky top-0 bg-surface-1 z-10">
                  <tr className="border-b border-slate-800 text-slate-400 text-sm">
                    <th className="py-3 px-6 font-medium">Codigo</th>
                    <th className="py-3 px-6 font-medium">Origen</th>
                    <th className="py-3 px-6 font-medium">Destino</th>
                    <th className="py-3 px-6 font-medium">Prox. Vuelo</th>
                  </tr>
                </thead>
                <tbody>
                  {bagsAtAirport.map((bag) => {
                    const next = nextFlightForBag[bag.idMaleta];
                    return (
                      <tr key={bag.idMaleta} className="border-b border-slate-800/50 hover:bg-surface-2 transition-colors">
                        <td className="py-3 px-6 font-bold text-blue-400 text-sm">{bag.idMaleta}</td>
                        <td className="py-3 px-6 text-slate-300 text-sm">{bag.origen ?? "—"}</td>
                        <td className="py-3 px-6 text-slate-300 text-sm">{bag.destino ?? "—"}</td>
                        <td className="py-3 px-6 text-sm">
                          {next ? (
                            <span className="text-emerald-400 font-medium">{extractTime(next.fechaSalida)}</span>
                          ) : (
                            <span className="text-slate-500">—</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      <div className="bg-surface-1 border border-slate-800 rounded-xl overflow-hidden mx-0 sm:mx-14">
        <div className="px-6 py-4 border-b border-slate-800 flex flex-col sm:flex-row justify-between sm:items-center gap-4">
          <div>
            <h2 className="text-lg font-bold text-white">Planes de Vuelo</h2>
            <p className="text-xs text-slate-400 mt-1">
              {filteredFlightPlans.length} plan{filteredFlightPlans.length !== 1 ? "es" : ""} encontrado{filteredFlightPlans.length !== 1 ? "s" : ""}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-4">
            <div className="flex bg-surface-2 rounded-lg p-0.5">
              <button
                type="button"
                onClick={() => setViewMode("origin")}
                className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                  viewMode === "origin"
                    ? "bg-blue-600 text-white"
                    : "text-slate-400 hover:text-slate-200"
                }`}
              >
                Como origen
              </button>
              <button
                type="button"
                onClick={() => setViewMode("dest")}
                className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                  viewMode === "dest"
                    ? "bg-blue-600 text-white"
                    : "text-slate-400 hover:text-slate-200"
                }`}
              >
                Como destino
              </button>
            </div>
            <button
              type="button"
              onClick={() => setPlanFormOpen(true)}
              className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white px-3 py-1.5 rounded-lg font-medium text-sm transition-colors"
            >
              <Plus className="w-4 h-4" /> Agregar Plan de Vuelo
            </button>
          </div>
        </div>

        {plansLoading && <LoadingState label="Cargando planes de vuelo..." />}
        {!plansLoading && plansError && <ErrorState error={plansError} />}
        {!plansLoading && !plansError && filteredFlightPlans.length === 0 && (
          <EmptyState title="Sin planes de vuelo" message={`No hay planes de vuelo con ${viewMode === "origin" ? "origen" : "destino"} en ${iata}.`} />
        )}
        {!plansLoading && !plansError && filteredFlightPlans.length > 0 && (
          <div className="app-scrollbar overflow-x-auto overflow-y-auto max-h-[56vh] min-h-[320px]">
            <table className="w-full text-left border-collapse min-w-[720px]">
              <thead className="sticky top-0 bg-surface-1 z-10">
                <tr className="border-b border-slate-800 text-slate-400 text-sm">
                  <th className="py-4 px-6 font-medium">Codigo</th>
                  <th className="py-4 px-6 font-medium">Origen</th>
                  <th className="py-4 px-6 font-medium">Destino</th>
                  <th className="py-4 px-6 font-medium">Salida</th>
                  <th className="py-4 px-6 font-medium">Llegada</th>
                  <th className="py-4 px-6 font-medium">Capacidad</th>
                  <th className="py-4 px-6 font-medium text-right">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {filteredFlightPlans.map((fl) => (
                  <tr key={fl.id} className="border-b border-slate-800/50 hover:bg-slate-800/30 transition-colors group">
                    <td className="py-4 px-6 font-bold text-blue-400">{fl.id}</td>
                    <td className="py-4 px-6 text-slate-300 font-medium">{fl.origin}</td>
                    <td className="py-4 px-6 text-slate-300">
                      <div className="font-medium">{fl.dest}</div>
                      {(fl.destCity || fl.destCont) && (
                        <div className="text-xs text-slate-400">{fl.destCity}{fl.destCity && fl.destCont ? ", " : ""}{normalizeContinente(fl.destCont)}</div>
                      )}
                    </td>
                    <td className="py-4 px-6 text-sm text-slate-400">
                      {fl.depTime} <span className="text-slate-500">({formatGMT(fl.gmtOrigin)})</span>
                    </td>
                    <td className="py-4 px-6 text-sm text-slate-400">
                      {fl.arrTime} <span className="text-slate-500">({formatGMT(fl.gmtDest)})</span>
                    </td>
                    <td className="py-4 px-6 text-sm text-slate-300">{fl.capacity}</td>
                    <td className="py-4 px-6 text-right">
                      <button
                        type="button"
                        onClick={() => handleCancelPlan(fl)}
                        title="Cancelar plan de vuelo"
                        className="p-2 rounded-lg hover:bg-red-600/20 hover:text-red-400 transition-colors text-slate-400 text-xs font-medium"
                      >
                        Cancelar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <PedidoModal
        open={pedidoOpen}
        airports={airports}
        onClose={() => setPedidoOpen(false)}
        onSubmit={handlePedidoSubmit}
        loading={pedidoLoading}
        lockedOrigin={airport.iata}
      />

      <BulkPedidoModal
        open={bulkOpen}
        airport={airport}
        file={bulkFile}
        loading={bulkLoading}
        onFileChange={setBulkFile}
        onClose={() => setBulkOpen(false)}
        onSubmit={handleBulkSubmit}
      />

      <FlightPlanFormModal
        open={planFormOpen}
        initialData={null}
        airports={airports}
        onClose={() => setPlanFormOpen(false)}
        onSubmit={handlePlanSubmit}
        loading={planFormLoading}
      />
    </div>
  );
}
