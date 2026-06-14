import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { Plus, Luggage, X } from "lucide-react";
import MapDashboard from "../components/simulator/MapDashboard";
import { usePolling } from "../hooks/usePolling";
import { useElapsedTimer } from "../hooks/useElapsedTimer";
import { useDefensivePerformanceCleanup } from "../hooks/useDefensivePerformanceCleanup";
import { useStompSubscribe } from "../hooks/useStomp";
import { isStompConnected, subscribeToReconnects } from "../api/stomp";
import { USE_MOCK } from "../api/client";
import { useToast } from "../components/ui/Toast";
import {
  iniciarSimulacionDiaADia,
  procesarPedidoDiaADia,
  obtenerAeropuertosDiaADia,
  obtenerVuelosDiaADia,
  obtenerPedidosDiaADia,
  obtenerMaletasDiaADia,
  obtenerRutasDiaADia,
  obtenerEstadoDiaADia,
  obtenerEstadoActualDiaADia,
  onSessionChange,
  confirmarConexionDiaADia,
  obtenerVuelosNuevosDiaADia,
} from "../api/simulator";
import { adaptAirport } from "../api/airports";
import { adaptFlightInstance } from "../api/flightInstances";
import { getMockDiaADiaState } from "../api/mock";
import { clearPerformanceTimeline } from "../utils/performanceCleanup";
import { formatElapsedHMS } from "../utils/formatting";

const ENUM_VUELO = ["PROGRAMADO", "CONFIRMADO", "EN_PROGRESO", "FINALIZADO", "CANCELADO"];
const ENUM_MALETA = ["EN_ALMACEN", "EN_TRANSITO", "ENTREGADA"];
const ENUM_RUTA = ["PLANIFICADA", "ACTIVA", "COMPLETADA", "REPLANIFICADA"];

const REFRESH_MS = 500;
const ESTADO_BACK_A_LOCAL = {
  INICIADA: "running",
  DETENIDA: "idle",
  PEDIDO_PROCESADO: "running",
};

const emptyMetrics = {
  bagsInTransit: 0,
  bagsDelivered: 0,
  bagsUnassigned: 0,
  activeFlights: 0,
  freeCapacityPct: 0,
};

const MAPBOX_STYLE = "mapbox://styles/mapbox/dark-v11";

function formatLimaTime(utcIsoString) {
  if (!utcIsoString) return { date: "--", time: "--:--:--" };
  const d = new Date(utcIsoString.endsWith("Z") ? utcIsoString : utcIsoString + "Z");
  if (Number.isNaN(d.getTime())) return { date: "--", time: "--:--:--" };
  return {
    date: d.toLocaleDateString("es-PE", { timeZone: "America/Lima", day: "2-digit", month: "2-digit", year: "numeric" }),
    time: d.toLocaleTimeString("es-PE", { timeZone: "America/Lima", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false }),
  };
}

const PedidoModal = ({ open, airports, onClose, onSubmit, loading }) => {
  const [origen, setOrigen] = useState("");
  const [destino, setDestino] = useState("");
  const [maletas, setMaletas] = useState("1");

  useEffect(() => {
    if (!open) { setOrigen(""); setDestino(""); setMaletas("1"); }
  }, [open]);

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
      idPedido: "PED-" + Date.now(),
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
            <select value={origen} onChange={(e) => setOrigen(e.target.value)} required className="w-full rounded-lg border border-slate-800 bg-surface-2 py-2 px-3 text-sm text-slate-200 focus:border-slate-600 focus:outline-none">
              <option value="">Seleccionar...</option>
              {airportOptions}
            </select>
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
};

export default function SimulatorPage() {
  const toast = useToast();
  const { simulationPanelData, setSimulationPanelData, resetSimulationPanelData } = useOutletContext();

  const [sessionId, setSessionId] = useState(null);
  const [simStatus, setSimStatus] = useState("idle");
  const [runId, setRunId] = useState(0);
  const [currentSimTimeUtc, setCurrentSimTimeUtc] = useState(null);
  const [pedidoOpen, setPedidoOpen] = useState(false);
  const [pedidoLoading, setPedidoLoading] = useState(false);
  const [mapAirports, setMapAirports] = useState([]);
  const [datosBaseCargados, setDatosBaseCargados] = useState(false);
  const sessionIdRef = useRef(null);
  sessionIdRef.current = sessionId;
  const ultimoTickRef = useRef(0);
  const tickCountRef = useRef(0);
  const reconnectingRef = useRef(false);
  const reiniciarTokenRef = useRef(0);
  const lastMapFlightsRef = useRef([]);
  const startSimMsRef = useRef(null);

  const tickTopic = !USE_MOCK && sessionId ? `/topic/operations/${sessionId}` : null;
  const statusTopic = !USE_MOCK && sessionId ? `/topic/operations/${sessionId}/estado` : null;

  const { data: tick } = useStompSubscribe(tickTopic);
  const { data: estadoMessage } = useStompSubscribe(statusTopic);

  const { data: mockState } = usePolling(getMockDiaADiaState, {
    enabled: USE_MOCK && simStatus === "running",
    intervalMs: REFRESH_MS,
  });

  const executionElapsedMs = useElapsedTimer(simStatus, runId, REFRESH_MS);
  const hasActiveRun = simStatus === "running";

  const simulatedNowMs = useMemo(() => {
    const startMs = startSimMsRef.current;
    if (startMs == null) {
      if (!currentSimTimeUtc) return null;
      const parsed = Date.parse(`${currentSimTimeUtc}Z`);
      return Number.isFinite(parsed) ? parsed : null;
    }
    if (simStatus !== "running") {
      if (!currentSimTimeUtc) return startMs;
      const parsed = Date.parse(`${currentSimTimeUtc}Z`);
      return Number.isFinite(parsed) ? parsed : startMs;
    }
    return startMs + executionElapsedMs;
  }, [currentSimTimeUtc, simStatus, executionElapsedMs]);

  useDefensivePerformanceCleanup(hasActiveRun);

  useEffect(() => {
    return subscribeToReconnects(() => {
      reiniciarTokenRef.current++;
    });
  }, []);

  const inicializarSesion = useCallback(async (esInicial) => {
    try {
      const result = await iniciarSimulacionDiaADia();
      const newSessionId = result.sessionId;
      setSessionId(newSessionId);
      setRunId((c) => c + 1);
      let aeropuertosData, vuelosData;
      try {
        const snapshot = await obtenerEstadoActualDiaADia();
        if (snapshot && snapshot.activa) {
          aeropuertosData = snapshot.aeropuertos;
          vuelosData = snapshot.vuelos;
        }
      } catch {
        // fallback to individual endpoints
      }
      if (!aeropuertosData || !vuelosData) {
        [aeropuertosData, vuelosData] = await Promise.all([
          obtenerAeropuertosDiaADia(),
          obtenerVuelosDiaADia(),
        ]);
      }
      const adaptedAirports = Array.isArray(aeropuertosData) ? aeropuertosData.map(adaptAirport) : [];
      setMapAirports(adaptedAirports);
      const adaptedFlights = (vuelosData ?? []).map(adaptFlightInstance);
      const flights = new Map();
      for (const f of adaptedFlights) flights.set(f.idVueloInstancia ?? f.id, { ...f, ticksAusente: 0 });

      const [rutasData, maletasData] = await Promise.all([
        obtenerRutasDiaADia().catch(() => []),
        obtenerMaletasDiaADia().catch(() => []),
      ]);
      const routes = new Map();
      for (const r of rutasData) routes.set(r.idRuta, { ...r, ticksAusente: 0 });
      const bags = new Map();
      for (const m of maletasData) bags.set(m.idMaleta, { ...m, ticksAusente: 0 });
      if (!esInicial) {
        setSimulationPanelData((prev) => ({
          ...prev,
          airports: adaptedAirports,
          flights,
          routes,
          bags,
          sessionId: newSessionId,
        }));
      } else {
        setSimulationPanelData({
          airports: adaptedAirports,
          flights,
          orders: new Map(),
          bags,
          routes,
          sessionId: newSessionId,
          loaded: true,
        });
      }
      setSimStatus("running");
      setDatosBaseCargados(true);
      ultimoTickRef.current = Date.now();
      if (!USE_MOCK) {
        await confirmarConexionDiaADia();
      }
      if (esInicial) {
        toast.push({ type: "info", title: "Operaciones dia a dia", message: "Simulacion iniciada en tiempo real" });
      }
    } catch (err) {
      if (esInicial) toast.push({ type: "error", title: "No se pudo iniciar", message: err.message });
    }
  }, [setSimulationPanelData, toast]);

  useEffect(() => {
    resetSimulationPanelData();
    clearPerformanceTimeline();
    let cancelled = false;
    (async () => {
      if (cancelled) return;
      await inicializarSesion(true);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (USE_MOCK) return;
    const interval = setInterval(async () => {
      if (!hasActiveRun || !sessionId) return;
      if (Date.now() - ultimoTickRef.current < 5000) return;
      if (!isStompConnected()) return;
      try {
        await obtenerEstadoDiaADia();
      } catch {
        // withReconnect handles session renewal transparently
      }
    }, 3000);
    return () => clearInterval(interval);
  }, [hasActiveRun, sessionId]);

  useEffect(() => {
    return onSessionChange((newSessionId) => {
      setSessionId(newSessionId);
      setRunId((c) => c + 1);
    });
  }, []);

  useEffect(() => {
    if (!hasActiveRun) return;
    const interval = setInterval(() => {
      console.log(
        `[DBG] flights=${simulationPanelData.flights?.size ?? 0}` +
        ` bags=${simulationPanelData.bags?.size ?? 0}` +
        ` routes=${simulationPanelData.routes?.size ?? 0}` +
        ` orders=${simulationPanelData.orders?.size ?? 0}` +
        ` airports=${simulationPanelData.airports?.length ?? 0}` +
        ` tick=${tickCountRef.current}` +
        ` status=${simStatus}` +
        ` sessionId=${sessionId?.slice(0, 8)}...`
      );
    }, 5000);
    return () => clearInterval(interval);
  }, [hasActiveRun, simStatus, simulationPanelData, sessionId]);

  useEffect(() => {
    if (USE_MOCK) {
      if (!mockState) return;
      const local = ESTADO_BACK_A_LOCAL[mockState.status] ?? simStatus;
      if (local !== simStatus) setSimStatus(local);
      return;
    }
    if (!estadoMessage) return;
    const local = ESTADO_BACK_A_LOCAL[estadoMessage.estado] ?? simStatus;
    if (local !== simStatus) setSimStatus(local);
    if (estadoMessage.estado === "DETENIDA" && simStatus !== "idle") {
      setSessionId(null);
      setCurrentSimTimeUtc(null);
    }
  }, [mockState, estadoMessage]);

  const updateEstadosOnly = (oldMap, stateMap, enumArr, statusField = "estado", extraFields) => {
    if (Object.keys(stateMap).length === 0) return oldMap;
    const updated = new Map(oldMap);
    for (const [id, entity] of updated) {
      const st = stateMap[id];
      if (st) {
        updated.set(id, { ...entity, [statusField]: enumArr[st.e], ...(extraFields ? extraFields(st, entity) : {}) });
      }
    }
    return updated;
  };

  const getUpdatedFlightOccupancy = (st, flight) => {
    if (flight.capacity <= 0 || !Number.isFinite(Number(st.cap))) return flight.used ?? 0;
    return Math.max(0, flight.capacity - Number(st.cap));
  };

  useEffect(() => {
    if (!tick?.type) return;
    if (tick.type !== "TICK_DIAADIA") return;
    if (!datosBaseCargados) return;
    ultimoTickRef.current = Date.now();
    tickCountRef.current++;
    setCurrentSimTimeUtc(tick.simTime);

    if (startSimMsRef.current == null) {
      const ms = Date.parse(`${tick.simTime}Z`);
      if (Number.isFinite(ms)) startSimMsRef.current = ms;
    }

    const occMap = {};
    if (Array.isArray(tick.aeropuertos)) {
      for (const a of tick.aeropuertos) occMap[a.id] = a.occ;
    }

    let vueloStateMap = {};
    let maletaStateMap = {};
    let rutaStateMap = {};
    if (Array.isArray(tick.estadosVuelos)) vueloStateMap = Object.fromEntries(tick.estadosVuelos.map((v) => [v.id, v]));
    if (Array.isArray(tick.estadosMaletas)) maletaStateMap = Object.fromEntries(tick.estadosMaletas.map((m) => [m.id, m]));
    if (Array.isArray(tick.estadosRutas)) rutaStateMap = Object.fromEntries(tick.estadosRutas.map((r) => [r.id, r]));

    setMapAirports((prev) => prev.map((ap) => ({ ...ap, used: occMap[ap.iata] ?? ap.used })));

    setSimulationPanelData((prev) => {
      const updatedFlights = updateEstadosOnly(prev.flights, vueloStateMap, ENUM_VUELO, "status",
        (st, flight) => ({ used: getUpdatedFlightOccupancy(st, flight) }));
      for (const [id, flight] of updatedFlights) {
        const status = flight?.status;
        if (status === "FINALIZADO" || status === "CANCELADO") {
          updatedFlights.delete(id);
        }
      }
      const updatedBags = updateEstadosOnly(prev.bags, maletaStateMap, ENUM_MALETA, "estado",
        (st, bag) => (st.e === 2 ? { fechaLlegada: bag.fechaLlegada ?? tick.simTime } : {}));
      const updatedRoutes = updateEstadosOnly(prev.routes, rutaStateMap, ENUM_RUTA, "estado");
      return {
        ...prev,
        simTime: tick.simTime,
        airports: prev.airports.map((ap) => ({ ...ap, used: occMap[ap.iata] ?? ap.used })),
        flights: updatedFlights,
        bags: updatedBags,
        routes: updatedRoutes,
      };
    });
  }, [tick]);

  useEffect(() => {
    if (USE_MOCK || !hasActiveRun) return;
    if (tickCountRef.current % 60 !== 0) return;
    obtenerVuelosNuevosDiaADia().then((nuevos) => {
      if (!nuevos?.length) return;
      const adapted = nuevos.map(adaptFlightInstance);
      setSimulationPanelData((prev) => {
        const flights = new Map(prev.flights);
        for (const f of adapted) {
          flights.set(f.idVueloInstancia ?? f.id, { ...f, ticksAusente: 0 });
        }
        return { ...prev, flights };
      });
    }).catch(() => {});
  }, [tick, hasActiveRun]);

  const liveMetrics = useMemo(() => {
    if (!hasActiveRun) return emptyMetrics;
    if (USE_MOCK) {
      return mockState
        ? {
            bagsInTransit: mockState.bagsInTransit ?? 0,
            bagsDelivered: mockState.bagsDelivered ?? 0,
            bagsUnassigned: mockState.bagsUnassigned ?? 0,
            activeFlights: mockState.activeFlights ?? 0,
            freeCapacityPct: mockState.freeCapacityPct ?? 0,
          }
        : emptyMetrics;
    }
    if (!tick || tick.type !== "TICK_DIAADIA") return emptyMetrics;
    return {
      bagsInTransit: tick.maletasEnTransito ?? 0,
      bagsDelivered: tick.maletasEntregadas ?? 0,
      bagsUnassigned: tick.maletasNoAsignadas ?? tick.maletasSinRuta ?? 0,
      activeFlights: tick.vuelosActivos ?? 0,
      freeCapacityPct: tick.capacidadLibrePct ?? 0,
    };
  }, [tick, mockState, hasActiveRun]);

  const normalizeFlightStatus = (status) =>
    String(status ?? "").trim().toUpperCase().replace(/\s+/g, "_");

  const mapVisibleFlights = useMemo(() => {
    if (simStatus !== "running") return lastMapFlightsRef.current;
    if (!simulationPanelData?.flights) return [];
    const out = [];
    for (const flight of simulationPanelData.flights.values()) {
      const status = flight.status ?? flight.estado;
      if (normalizeFlightStatus(status) === "CANCELADO") continue;
      if (normalizeFlightStatus(status) === "FINALIZADO") continue;
      out.push({
        id: flight.idVueloInstancia ?? flight.id,
        origin: flight.origin,
        dest: flight.dest,
        depTime: flight.depTime,
        arrTime: flight.arrTime,
        used: flight.used,
        capacity: flight.capacity,
      });
    }
    lastMapFlightsRef.current = out;
    return out;
  }, [simulationPanelData?.flights, simStatus]);

  const handlePedidoSubmit = async (pedido) => {
    setPedidoLoading(true);
    try {
      await procesarPedidoDiaADia(pedido);
      const [pedidosData, maletasData] = await Promise.all([
        obtenerPedidosDiaADia().catch(() => []),
        obtenerMaletasDiaADia().catch(() => []),
      ]);
      setSimulationPanelData((prev) => {
        const orders = new Map(prev.orders);
        for (const o of pedidosData ?? []) orders.set(o.id ?? o.idPedido, o);
        const bags = new Map(prev.bags);
        for (const b of maletasData ?? []) bags.set(b.idMaleta, { ...b, ticksAusente: 0 });
        return { ...prev, orders, bags };
      });
      setPedidoOpen(false);
      toast.push({ type: "success", title: "Pedido enviado", message: `${pedido.idPedido} · ${pedido.cantidadMaletas} maletas` });
    } catch (err) {
      toast.push({ type: "error", title: "Error al enviar pedido", message: err.message });
    } finally {
      setPedidoLoading(false);
    }
  };

  const limaTime = formatLimaTime(currentSimTimeUtc);

  const mapOverlay = hasActiveRun ? (
    <div className="bg-surface-2/85 backdrop-blur border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-4 py-3 flex items-center justify-center gap-5">
      <div>
        <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">Fecha Lima, Perú (GMT-5)</div>
        <div className="text-lg font-bold text-info tabular-nums">{limaTime.date}</div>
      </div>
      <div className="h-9 w-px bg-slate-700 shrink-0" />
      <div>
        <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">Hora Lima, Perú (GMT-5)</div>
        <div className="text-lg font-bold text-info tabular-nums">{limaTime.time}</div>
      </div>
      <div className="h-9 w-px bg-slate-700 shrink-0" />
      <button type="button" onClick={() => setPedidoOpen(true)} className="self-center bg-blue-600 hover:bg-blue-500 text-white px-5 py-2.5 rounded-lg flex items-center gap-2 font-medium text-sm leading-none transition-colors shrink-0">
        <Plus className="w-4 h-4" /> Agregar Pedido
      </button>
    </div>
  ) : null;

  const header = null;

  return (
    <>
      <MapDashboard
        title="Operaciones dia a dia · Tiempo real"
        header={header}
        mapOverlay={mapOverlay}
        showMapClock={false}
        showMapFlights={hasActiveRun}
        showMapRouteLines={hasActiveRun}
        animateMapFlights={simStatus === "running"}
        mapAutoload={false}
        airports={mapAirports}
        flights={mapVisibleFlights}
        simulatedNowMs={simulatedNowMs}
        simulatedDayDurationMs={86400000}
        metrics={liveMetrics}
      />
      <PedidoModal
        open={pedidoOpen}
        airports={mapAirports}
        onClose={() => setPedidoOpen(false)}
        onSubmit={handlePedidoSubmit}
        loading={pedidoLoading}
      />
    </>
  );
}
