import { useEffect, useMemo, useRef, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { Play, Square, AlertTriangle } from "lucide-react";
import MapDashboard from "../components/simulator/MapDashboard";
import { usePolling } from "../hooks/usePolling";
import { useElapsedTimer } from "../hooks/useElapsedTimer";
import { useDefensivePerformanceCleanup } from "../hooks/useDefensivePerformanceCleanup";
import { useStompPublish, useStompSubscribe } from "../hooks/useStomp";
import { useToast } from "../components/ui/Toast";
import {
  iniciarSimulacionColapso,
  stopCollapseSim,
  getCollapseSimState,
  obtenerBaseSimulacion,
  obtenerVentanaSimulacion,
  obtenerVuelosSimulacion,
} from "../api/simulator";
import { adaptAirport } from "../api/airports";
import { adaptFlightInstance } from "../api/flightInstances";
import { USE_MOCK } from "../api/client";
import { clearPerformanceTimeline } from "../utils/performanceCleanup";
import { formatElapsedHMS, formatUtcDateTimeDisplay } from "../utils/formatting";

const ENUM_VUELO = ["PROGRAMADO", "CONFIRMADO", "EN_PROGRESO", "FINALIZADO", "CANCELADO"];
const ENUM_MALETA = ["EN_ALMACEN", "EN_TRANSITO", "ENTREGADA"];
const ENUM_RUTA = ["PLANIFICADA", "ACTIVA", "COMPLETADA", "REPLANIFICADA"];

const WARNING_AT_MS = 60_000;
const CLOCK_REFRESH_MS = 500;
const MAP_REFRESH_MS = 500;

const ESTADO_BACK_A_LOCAL = {
  INICIADA: "running",
  REANUDADA: "running",
  PAUSADA: "paused",
  DETENIDA: "idle",
  COLAPSO: "collapsed",
  FINALIZADA: "done",
};

const emptyMetrics = {
  bagsInTransit: 0,
  bagsDelivered: 0,
  activeFlights: 0,
  airportCapacityPct: 0,
  flightCapacityPct: 0,
};

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

const normalizeFlightStatus = (status) =>
  String(status ?? "").trim().toUpperCase().replace(/\s+/g, "_");

const getUpdatedFlightOccupancy = (st, flight) => {
  if (flight.capacity <= 0 || !Number.isFinite(Number(st.cap))) {
    return flight.used ?? 0;
  }
  return Math.max(0, flight.capacity - Number(st.cap));
};

export default function CollapseSimulatorPage() {
  const toast = useToast();
  const publish = useStompPublish();
  const {
    simulationPanelData,
    setSimulationPanelData,
    resetSimulationPanelData,
    collapseSidebars,
    setCancelledFlightIds,
  } = useOutletContext();

  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [sessionId, setSessionId] = useState(null);
  const [simStatus, setSimStatus] = useState("idle");
  const [runId, setRunId] = useState(0);
  const [mapAirports, setMapAirports] = useState([]);
  const [showRouteLines, setShowRouteLines] = useState(true);
  const [currentSimTimeUtc, setCurrentSimTimeUtc] = useState(null);
  const [simulatedDayDurationMs, setSimulatedDayDurationMs] = useState(null);
  const [lastMockState, setLastMockState] = useState(null);

  const ventanasCargadasRef = useRef(new Set());
  const lastMapFlightsRef = useRef([]);
  const flightMetadataRef = useRef(new Map());
  const startSimMsRef = useRef(null);
  const sessionIdRef = useRef(null);
  sessionIdRef.current = sessionId;

  const clearSimulationData = () => {
    ventanasCargadasRef.current.clear();
    flightMetadataRef.current.clear();
    lastMapFlightsRef.current = [];
    startSimMsRef.current = null;
    setMapAirports([]);
    setSimulationPanelData({
      airports: [],
      flights: new Map(),
      orders: new Map(),
      bags: new Map(),
      routes: new Map(),
      loaded: false,
    });
    clearPerformanceTimeline();
  };

  useDefensivePerformanceCleanup(simStatus === "running");

  useEffect(() => {
    resetSimulationPanelData();
    return () => {
      const sid = sessionIdRef.current;
      if (sid && !USE_MOCK) {
        publish("/app/simulacion/periodo/detener", { sessionId: sid });
      }
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const tickTopic = !USE_MOCK && sessionId ? `/topic/simulacion/${sessionId}` : null;
  const statusTopic = !USE_MOCK && sessionId ? `/topic/simulacion/${sessionId}/estado` : null;

  const { data: tick } = useStompSubscribe(tickTopic);
  const { data: estadoMessage } = useStompSubscribe(statusTopic);

  const { data: mockState } = usePolling(getCollapseSimState, {
    enabled: USE_MOCK && simStatus === "running",
    intervalMs: MAP_REFRESH_MS,
  });

  const executionElapsedMs = useElapsedTimer(simStatus, runId, CLOCK_REFRESH_MS);
  const hasActiveRun =
    simStatus === "running" || simStatus === "paused" || simStatus === "collapsed" || simStatus === "done";

  useEffect(() => {
    if (USE_MOCK) {
      if (!mockState || mockState.status === simStatus) return;
      setLastMockState(mockState);
      setSimStatus(mockState.status);
      if (mockState.status === "collapsed") {
        toast.push({ type: "error", title: "Colapso detectado", message: "Las operaciones colapsaron." });
      }
      return;
    }

    if (!estadoMessage) return;
    const local = ESTADO_BACK_A_LOCAL[estadoMessage.estado] ?? simStatus;
    if (local !== simStatus) setSimStatus(local);

    if (estadoMessage.estado === "COLAPSO") {
      toast.push({ type: "error", title: "Colapso detectado", message: estadoMessage.mensaje });
    }

    if (estadoMessage.estado === "DETENIDA" && simStatus !== "idle") {
      setSessionId(null);
      setCurrentSimTimeUtc(null);
      clearSimulationData();
    }
  }, [mockState, estadoMessage]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (simStatus === "idle" || !tick?.type) return;

    if (tick.type === "VENTANA_READY") {
      const windowId = tick.ventana;
      if (ventanasCargadasRef.current.has(windowId)) return;
      ventanasCargadasRef.current.add(windowId);

      (async () => {
        const vuelosDataRaw = tick.vuelosVentana
          ? await obtenerVuelosSimulacion(sessionId, tick.vuelosVentana, tick.vuelosVentana)
          : [];
        const ventanaData = await obtenerVentanaSimulacion(sessionId, windowId);
        const adaptedFlights = (vuelosDataRaw ?? []).map(adaptFlightInstance);
        const metadata = new Map(flightMetadataRef.current);
        for (const f of adaptedFlights) {
          metadata.set(f.idVueloInstancia ?? f.id, { ...f, ticksAusente: 0 });
        }
        flightMetadataRef.current = metadata;

        setSimulationPanelData((prev) => {
          const flights = new Map(prev.flights);
          for (const f of adaptedFlights) {
            const isActive = f.status === "EN_PROGRESO" || f.status === "CONFIRMADO";
            if (isActive && !flights.has(f.idVueloInstancia ?? f.id)) {
              flights.set(f.idVueloInstancia ?? f.id, { ...f, ticksAusente: 0 });
            }
          }
          const bags = new Map(prev.bags);
          const bagOrigen = new Map();
          const bagDestino = new Map();
          const pedidoMap = new Map();
          for (const p of ventanaData.pedidos ?? []) {
            pedidoMap.set(p.id ?? p.idPedido, p);
          }
          for (const r of ventanaData.rutas ?? []) {
            const first = r?.vuelos?.[0];
            const last = r?.vuelos?.[r.vuelos.length - 1];
            if (r.idMaleta) {
              if (first?.aeropuertoOrigen) bagOrigen.set(r.idMaleta, first.aeropuertoOrigen);
              if (last?.aeropuertoDestino) bagDestino.set(r.idMaleta, last.aeropuertoDestino);
            }
          }
          for (const b of ventanaData.maletas ?? []) {
            const pedido = pedidoMap.get(b.idPedido);
            bags.set(b.idMaleta, {
              ...b,
              origen: bagOrigen.get(b.idMaleta) ?? pedido?.origin ?? null,
              destino: bagDestino.get(b.idMaleta) ?? pedido?.dest ?? null,
              ticksAusente: 0,
            });
          }
          const routes = new Map(prev.routes);
          for (const r of ventanaData.rutas ?? []) {
            routes.set(r.idRuta, { ...r, ticksAusente: 0 });
          }
          const orders = new Map(prev.orders);
          for (const o of ventanaData.pedidos ?? []) {
            orders.set(o.id ?? o.idPedido, o);
          }
          return { ...prev, flights, bags, routes, orders };
        });
      })().then(() => publish("/app/simulacion/periodo/ventana-lista", { sessionId }));
      return;
    }

    const esReconciliacion = tick.type === "RECONCILIAR";
    const esTick = tick.type === "TICK";
    if (!esTick && !esReconciliacion) return;

    if (esTick && startSimMsRef.current == null) {
      const ms = Date.parse(`${tick.simTime}Z`);
      if (Number.isFinite(ms)) startSimMsRef.current = ms;
    }

    if (esTick) {
      setCurrentSimTimeUtc(tick.simTime);
    }

    const occMap = {};
    if (Array.isArray(tick.aeropuertos)) {
      for (const a of tick.aeropuertos) occMap[a.id] = a.occ;
    }

    setMapAirports((prev) =>
      prev.map((ap) => ({ ...ap, used: occMap[ap.iata] ?? ap.used })),
    );

    let vueloStateMap = {};
    let maletaStateMap = {};
    let rutaStateMap = {};

    if (Array.isArray(tick.estadosVuelos)) {
      vueloStateMap = Object.fromEntries(tick.estadosVuelos.map((v) => [v.id, v]));
    }
    if (Array.isArray(tick.estadosMaletas)) {
      maletaStateMap = Object.fromEntries(tick.estadosMaletas.map((m) => [m.id, m]));
    }
    if (Array.isArray(tick.estadosRutas)) {
      rutaStateMap = Object.fromEntries(tick.estadosRutas.map((r) => [r.id, r]));
    }

    setSimulationPanelData((prev) => {
      const updatedFlights = new Map(prev.flights);
      const metadata = new Map(flightMetadataRef.current);

      for (const st of tick.estadosVuelos ?? []) {
        if (st.e === 3) {
          updatedFlights.delete(st.id);
          metadata.delete(st.id);
        }
      }

      for (const [id, flight] of updatedFlights) {
        const st = vueloStateMap[id];
        if (st) {
          updatedFlights.set(id, {
            ...flight,
            status: ENUM_VUELO[st.e],
            used: getUpdatedFlightOccupancy(st, flight),
          });
        }
      }

      for (const st of tick.estadosVuelos ?? []) {
        if ((st.e === 1 || st.e === 2 || st.e === 4) && !updatedFlights.has(st.id)) {
          const meta = metadata.get(st.id);
          if (meta) {
            updatedFlights.set(st.id, {
              ...meta,
              status: ENUM_VUELO[st.e],
              used: Math.max(0, meta.capacity - Number(st.cap ?? meta.capacity)),
              ticksAusente: 0,
            });
          }
        }
      }

      flightMetadataRef.current = metadata;

      const updatedBags = updateEstadosOnly(prev.bags, maletaStateMap, ENUM_MALETA, "estado",
        (st, bag) => ({
          ...(st.e === 2 ? { fechaLlegada: bag.fechaLlegada ?? tick.simTime } : {}),
          ubicacionActual: st.u ?? bag.ubicacionActual ?? null,
        }));

      const updatedRoutes = updateEstadosOnly(prev.routes, rutaStateMap, ENUM_RUTA, "estado");

      for (const [id, bag] of updatedBags) {
        if (bag.estado === "ENTREGADA") updatedBags.delete(id);
      }
      for (const [id, route] of updatedRoutes) {
        if (route.estado === "COMPLETADA") updatedRoutes.delete(id);
      }

      return {
        ...prev,
        simTime: tick.simTime,
        airports: prev.airports.map((ap) => ({ ...ap, used: occMap[ap.iata] ?? ap.used })),
        flights: updatedFlights,
        bags: updatedBags,
        routes: updatedRoutes,
        orders: prev.orders,
      };
    });
  }, [tick, sessionId, publish, setSimulationPanelData, simStatus]);

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
    if (!simulatedDayDurationMs) return startMs;
    const ratio = 86400000 / simulatedDayDurationMs;
    return startMs + executionElapsedMs * ratio;
  }, [currentSimTimeUtc, simStatus, executionElapsedMs, simulatedDayDurationMs]);

  const panelFlights = useMemo(
    () => [...(simulationPanelData?.flights ?? new Map()).values()],
    [simulationPanelData?.flights],
  );

  const visibleFlights = useMemo(() => {
    if (simStatus === "running") {
      const out = [];
      for (const flight of panelFlights) {
        const status = flight.status ?? flight.estado;
        if (normalizeFlightStatus(status) !== "EN_PROGRESO") continue;
        const salidaMs = Date.parse(`${flight.depTime}Z`);
        const llegadaMs = Date.parse(`${flight.arrTime}Z`);
        if (!Number.isFinite(salidaMs) || !Number.isFinite(llegadaMs)) continue;
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
    }
    return lastMapFlightsRef.current;
  }, [panelFlights, simStatus]);

  const simulationClock = useMemo(() => {
    if (USE_MOCK) {
      const baseDate = mockState?.startDate ?? startDate;
      const start = new Date(`${baseDate}T00:00:00`);
      if (Number.isNaN(start.getTime())) return formatUtcDateTimeDisplay(null);
      return formatUtcDateTimeDisplay(new Date(start.getTime() + (mockState?.elapsedMs ?? lastMockState?.elapsedMs ?? 0)));
    }
    if (simulatedNowMs === null || Number.isNaN(simulatedNowMs)) {
      return formatUtcDateTimeDisplay(null);
    }
    return formatUtcDateTimeDisplay(new Date(simulatedNowMs));
  }, [USE_MOCK, mockState, lastMockState, startDate, simulatedNowMs]);

  const liveMetrics = useMemo(() => {
    if (!hasActiveRun) return emptyMetrics;
    if (USE_MOCK) {
      return mockState
        ? {
            bagsInTransit: mockState.bagsInTransit ?? 0,
            bagsDelivered: mockState.bagsDelivered ?? 0,
            activeFlights: mockState.activeFlights ?? 0,
            airportCapacityPct: mockState.airportCapacityPct ?? 0,
            flightCapacityPct: mockState.flightCapacityPct ?? 0,
          }
        : undefined;
    }
    if (!tick || tick.type !== "TICK") return undefined;
    const apTotalCap = mapAirports.reduce((s, a) => s + (a.capacity ?? 0), 0);
    const apTotalUsed = mapAirports.reduce((s, a) => s + (a.used ?? 0), 0);
    const airportCapacityPct = apTotalCap > 0 ? Math.round((apTotalUsed / apTotalCap) * 100) : 0;
    let flightTotalCap = 0;
    let flightTotalUsed = 0;
    for (const f of panelFlights) {
      const status = normalizeFlightStatus(f.status);
      if (status === "EN_PROGRESO") {
        flightTotalCap += f.capacity ?? 0;
        flightTotalUsed += f.used ?? 0;
      }
    }
    const flightCapacityPct = flightTotalCap > 0 ? Math.round((flightTotalUsed / flightTotalCap) * 100) : 0;
    return {
      bagsInTransit: tick.maletasEnTransito ?? 0,
      bagsDelivered: tick.maletasEntregadas ?? tick.maletasEntregadasATiempo ?? 0,
      activeFlights: tick.vuelosActivos ?? 0,
      airportCapacityPct,
      flightCapacityPct,
    };
  }, [tick, mockState, hasActiveRun, mapAirports, panelFlights]);

  const handleStart = async () => {
    setSimStatus("starting");
    setSessionId(null);
    setCurrentSimTimeUtc(null);
    setCancelledFlightIds(new Set());
    clearSimulationData();

    try {
      const result = await iniciarSimulacionColapso({
        fechaInicio: startDate,
        horaInicio: "00:00",
      });
      const newSessionId = result.sessionId;
      setSessionId(newSessionId);
      setLastMockState(null);
      setRunId((current) => current + 1);

      if (USE_MOCK) {
        setSimStatus(result.status ?? "running");
        toast.push({ type: "info", title: "Simulación iniciada", message: `Inicio: ${startDate} · sin límite` });
        collapseSidebars();
        return;
      }

      const base = await obtenerBaseSimulacion(newSessionId);
      const adaptedAirports = Array.isArray(base.aeropuertos)
        ? base.aeropuertos.map(adaptAirport)
        : [];
      setMapAirports(adaptedAirports);
      setSimulatedDayDurationMs(base.duracionDiaSimuladoMs);

      const primeraVentana = base.primeraVentana ?? "W0001";
      const [ventana1, vuelosData] = await Promise.all([
        obtenerVentanaSimulacion(newSessionId, primeraVentana),
        obtenerVuelosSimulacion(newSessionId, primeraVentana, primeraVentana),
      ]);

      ventanasCargadasRef.current.add(primeraVentana);
      const adaptedFlights = (vuelosData ?? []).map(adaptFlightInstance);
      const metadata = new Map();
      for (const f of adaptedFlights) {
        metadata.set(f.idVueloInstancia ?? f.id, { ...f, ticksAusente: 0 });
      }
      flightMetadataRef.current = metadata;

      const initialFlights = new Map();
      for (const [id, f] of metadata) {
        if (f.status === "EN_PROGRESO" || f.status === "CONFIRMADO") initialFlights.set(id, f);
      }

      const initialBags = new Map();
      const bagOrigen = new Map();
      const bagDestino = new Map();
      const pedidoMapInit = new Map();
      for (const p of ventana1.pedidos ?? []) {
        pedidoMapInit.set(p.id ?? p.idPedido, p);
      }
      for (const r of ventana1.rutas ?? []) {
        const first = r?.vuelos?.[0];
        const last = r?.vuelos?.[r.vuelos.length - 1];
        if (r.idMaleta) {
          if (first?.aeropuertoOrigen) bagOrigen.set(r.idMaleta, first.aeropuertoOrigen);
          if (last?.aeropuertoDestino) bagDestino.set(r.idMaleta, last.aeropuertoDestino);
        }
      }
      for (const b of ventana1.maletas ?? []) {
        const pedido = pedidoMapInit.get(b.idPedido);
        initialBags.set(b.idMaleta, {
          ...b,
          origen: bagOrigen.get(b.idMaleta) ?? pedido?.origin ?? null,
          destino: bagDestino.get(b.idMaleta) ?? pedido?.dest ?? null,
          ticksAusente: 0,
        });
      }

      const initialRoutes = new Map();
      for (const r of ventana1.rutas ?? []) {
        initialRoutes.set(r.idRuta, { ...r, ticksAusente: 0 });
      }

      const initialOrders = new Map();
      for (const o of ventana1.pedidos ?? []) {
        initialOrders.set(o.id ?? o.idPedido, o);
      }

      setSimulationPanelData({
        airports: adaptedAirports,
        flights: initialFlights,
        orders: initialOrders,
        bags: initialBags,
        routes: initialRoutes,
        sessionId: newSessionId,
        loaded: true,
      });

      await publish("/app/simulacion/periodo/iniciar-tick", { sessionId: newSessionId });
      setSimStatus("running");
      toast.push({ type: "info", title: "Simulación iniciada", message: `Inicio: ${startDate} · sin límite hasta colapso` });
      collapseSidebars();
    } catch (err) {
      setSimStatus("idle");
      setSessionId(null);
      setCurrentSimTimeUtc(null);
      clearSimulationData();
      toast.push({ type: "error", title: "No se pudo iniciar", message: err.message });
    }
  };

  const handleStop = async () => {
    setSimStatus("idle");
    setSessionId(null);
    setCurrentSimTimeUtc(null);
    setCancelledFlightIds(new Set());
    clearSimulationData();
    try {
      if (USE_MOCK) {
        await stopCollapseSim();
      } else {
        await publish("/app/simulacion/periodo/detener", { sessionId });
      }
      toast.push({ type: "warning", title: "Simulación detenida" });
    } catch (err) {
      toast.push({ type: "error", title: "No se pudo detener", message: err.message });
    }
  };

  const operationalState =
    simStatus === "collapsed"
      ? { label: "Colapso", color: "text-danger", bg: "bg-danger/15 border-danger/40" }
      : executionElapsedMs >= WARNING_AT_MS
      ? { label: "Próximo al colapso", color: "text-warning", bg: "bg-warning/15 border-warning/40" }
      : simStatus === "running"
      ? { label: "Operativo", color: "text-success", bg: "bg-success/15 border-success/40" }
      : { label: "En espera", color: "text-slate-300", bg: "bg-surface-2 border-slate-700" };

  const mapOverlays = hasActiveRun
    ? [
        {
          id: "collapse-time-panel",
          content: (
            <div className="bg-surface-2/85 backdrop-blur border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-4 py-3 flex items-center gap-5">
              <div>
                <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">Cronometro</div>
                <div className="text-lg font-bold text-slate-100 tabular-nums">{formatElapsedHMS(executionElapsedMs)}</div>
              </div>
              <div className="h-10 w-px bg-slate-700 shrink-0" />
              <div>
                <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">Fecha simulada</div>
                <div className="text-lg font-bold text-info tabular-nums">{simulationClock.date}</div>
              </div>
              <div className="h-10 w-px bg-slate-700 shrink-0" />
              <div>
                <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">Hora simulada</div>
                <div className="text-lg font-bold text-info tabular-nums">{simulationClock.time} UTC</div>
              </div>
            </div>
          ),
        },
        {
          id: "collapse-actions-panel",
          content: (
            <div className="bg-surface-2/85 backdrop-blur border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-4 py-3 flex items-center gap-4">
              <button
                type="button"
                onClick={() => setShowRouteLines((v) => !v)}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium whitespace-nowrap transition-colors ${
                  showRouteLines
                    ? "bg-blue-600/20 text-blue-400 border border-blue-500/40 hover:bg-blue-600/30"
                    : "bg-surface-2 text-slate-400 border border-slate-700 hover:text-slate-200"
                }`}
              >
                Mostrar lineas
              </button>
              <div className="h-10 w-px bg-slate-700 shrink-0" />
              <button
                type="button"
                onClick={handleStop}
                className="shrink-0 bg-danger/10 hover:bg-danger/20 text-danger border border-danger/40 rounded-lg px-2 py-1 transition-colors"
                title="Detener"
              >
                <Square className="w-5 h-5" />
              </button>
            </div>
          ),
        },
      ]
    : [];

  const header = hasActiveRun ? null : (
    <div className="flex items-center gap-3 flex-wrap">
      <div className="flex flex-col">
        <label htmlFor="collapse-start" className="text-[10px] text-slate-400 uppercase tracking-wider font-medium">
          Fecha de inicio
        </label>
        <input
          id="collapse-start"
          type="date"
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
          disabled={simStatus === "running"}
          className="bg-surface-2 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-slate-200 outline-none focus:border-blue-500 disabled:opacity-50"
        />
      </div>

      <div className="flex flex-col">
        <span className="text-[10px] text-slate-400 uppercase tracking-wider font-medium">Estado</span>
        <span className={`px-3 py-1.5 border rounded-lg text-sm font-bold flex items-center gap-2 ${operationalState.bg} ${operationalState.color}`}>
          {simStatus === "collapsed" && <AlertTriangle className="w-4 h-4" />}
          {operationalState.label}
        </span>
      </div>

      {simStatus !== "running" ? (
        <button
          type="button"
          onClick={handleStart}
          className="self-end bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors"
        >
          <Play className="w-4 h-4" /> Ejecutar
        </button>
      ) : null}
    </div>
  );

  return (
    <MapDashboard
      title="Simulación hasta Colapso"
      header={header}
      mapOverlays={mapOverlays}
      showMapFlights={hasActiveRun}
      showMapRouteLines={hasActiveRun && showRouteLines}
      animateMapFlights={simStatus === "running"}
      mapAutoload={false}
      airports={mapAirports}
      flights={visibleFlights}
      simulatedNowMs={simulatedNowMs}
      simulatedDayDurationMs={simulatedDayDurationMs}
      metrics={liveMetrics}
      draggable={hasActiveRun}
      simStatus={simStatus}
    />
  );
}
