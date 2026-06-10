import { useEffect, useMemo, useRef, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { Play, Square, RotateCw } from "lucide-react";
import MapDashboard from "../components/simulator/MapDashboard";
import { usePolling } from "../hooks/usePolling";
import { useElapsedTimer } from "../hooks/useElapsedTimer";
import { useDefensivePerformanceCleanup } from "../hooks/useDefensivePerformanceCleanup";
import { useStompPublish, useStompSubscribe } from "../hooks/useStomp";
import { whenConnected } from "../api/stomp";
import { useToast } from "../components/ui/Toast";
import {
  iniciarSimulacionPeriodo,
  obtenerBaseSimulacion,
  obtenerVentanaSimulacion,
  obtenerVuelosSimulacion,
  stopPeriodSim,
  getPeriodSimState,
} from "../api/simulator";
import { adaptAirport } from "../api/airports";
import { adaptFlightInstance } from "../api/flightInstances";
import { USE_MOCK } from "../api/client";
import { clearPerformanceTimeline } from "../utils/performanceCleanup";
import {
  formatElapsedHMS,
  formatUtcDateTimeDisplay,
} from "../utils/formatting";

const ENUM_VUELO = ["PROGRAMADO", "CONFIRMADO", "EN_PROGRESO", "FINALIZADO", "CANCELADO"];
const ENUM_MALETA = ["EN_ALMACEN", "EN_TRANSITO", "ENTREGADA"];
const ENUM_RUTA = ["PLANIFICADA", "ACTIVA", "COMPLETADA", "REPLANIFICADA"];
const SIMULATED_TWO_HOURS_MS = 7200000;



const PERIOD_DAYS = 5;
const CLOCK_REFRESH_MS = 500;
const MAP_REFRESH_MS = 500;

const ESTADO_BACK_A_LOCAL = {
  INICIADA: "running",
  REANUDADA: "running",
  PAUSADA: "paused",
  DETENIDA: "idle",
  FINALIZADA: "done",
};

const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

const getSimulationProgress = (
  elapsedRealMs,
  totalDays,
  simulatedDayDurationMs,
) => {
  if (totalDays <= 0 || simulatedDayDurationMs <= 0) return 0;
  const totalRealDurationMs = totalDays * simulatedDayDurationMs;
  return Math.min(
    100,
    Math.max(0, (elapsedRealMs / totalRealDurationMs) * 100),
  );
};

const getDisplayedDay = (progress, hasActiveRun) => {
  if (!hasActiveRun) return 0;
  return clamp(Math.ceil((progress / 100) * PERIOD_DAYS), 1, PERIOD_DAYS);
};

const emptyMetrics = {
  bagsInTransit: 0,
  bagsDelivered: 0,
  bagsUnassigned: 0,
  activeFlights: 0,
  freeCapacityPct: 0,
};

export default function PeriodSimulatorPage() {
  const toast = useToast();
  const publish = useStompPublish();
  const {
    simulationPanelData,
    setSimulationPanelData,
    resetSimulationPanelData,
    cancelledFlightIds,
    setCancelledFlightIds,
  } = useOutletContext();

  const [startDate, setStartDate] = useState(() =>
    new Date().toISOString().slice(0, 10),
  );
  const [startTime, setStartTime] = useState("00:00");
  const [sessionId, setSessionId] = useState(null);
  const [simStatus, setSimStatus] = useState("idle");
  const [runId, setRunId] = useState(0);
  const [lastMockState, setLastMockState] = useState(null);
  const [mapAirports, setMapAirports] = useState([]);
  // mapFlights se maneja dentro de simulationPanelData.flights
  const [showRouteLines, setShowRouteLines] = useState(true);
  const [currentSimTimeUtc, setCurrentSimTimeUtc] = useState(null);
  const [simulatedDayDurationMs, setSimulatedDayDurationMs] = useState(null);
  const [windowSizeMinutes, setWindowSizeMinutes] = useState(120);
  const [windowSpacingMinutes, setWindowSpacingMinutes] = useState(120);
  const startSimMsRef = useRef(null);
  const ventanasCargadasRef = useRef(new Set());
  const lastMapFlightsRef = useRef([]);
  const completionTimesRef = useRef(new Map());
  const iniciarTickWatchdogRef = useRef(null);
  const iniciarTickRetriesRef = useRef(0);
  const tickReceivedRef = useRef(false);

  const clearSimulationData = () => {
    ventanasCargadasRef.current.clear();
    completionTimesRef.current.clear();
    setMapAirports([]);
    setSimulationPanelData({
      airports: [],
      flights: new Map(),
      orders: new Map(),
      bags: new Map(),
      routes: new Map(),
      loaded: false,
    });
    startSimMsRef.current = null;
    clearPerformanceTimeline();
  };

  const applyCancelledFlights = (flights) =>
    flights.map((flight) =>
      cancelledFlightIds?.has(flight.id)
        ? { ...flight, status: "CANCELADO", used: 0 }
        : flight,
    );

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

  const hasOccupiedCapacity = (flight) =>
    Number(flight?.used ?? flight?.capacidadUsada ?? 0) > 0;

  const getUpdatedFlightOccupancy = (st, flight) => {
    if (flight.capacity <= 0 || !Number.isFinite(Number(st.cap))) {
      return flight.used ?? 0;
    }
    return Math.max(0, flight.capacity - Number(st.cap));
  };

  const sessionIdRef = useRef(null);
  sessionIdRef.current = sessionId;

  useEffect(() => {
    resetSimulationPanelData();
    return () => {
      const sid = sessionIdRef.current;
      if (sid) {
        publish("/app/simulacion/periodo/detener", { sessionId: sid });
      }
      cancelarWatchdogIniciarTick();
    };
  }, []);

  const tickTopic =
    !USE_MOCK && sessionId ? `/topic/simulacion/${sessionId}` : null;
  const statusTopic =
    !USE_MOCK && sessionId ? `/topic/simulacion/${sessionId}/estado` : null;

  const { data: tick } = useStompSubscribe(tickTopic);
  const { data: estadoMessage } = useStompSubscribe(statusTopic);

  const { data: mockState } = usePolling(getPeriodSimState, {
    enabled: USE_MOCK && simStatus === "running",
    intervalMs: MAP_REFRESH_MS,
  });

  const executionElapsedMs = useElapsedTimer(
    simStatus,
    runId,
    CLOCK_REFRESH_MS,
  );
  const hasActiveRun =
    simStatus === "running" || simStatus === "paused" || simStatus === "done";
  const isStarting = simStatus === "starting";
  const isSimulationLocked =
    isStarting || simStatus === "running" || simStatus === "paused";

  useDefensivePerformanceCleanup(simStatus === "running");

  useEffect(() => {
    if (USE_MOCK) {
      if (!mockState || mockState.status === simStatus) return;
      setLastMockState(mockState);
      setSimStatus(mockState.status);
      if (mockState.status === "done") {
        toast.push({
          type: "success",
          title: "Simulación completada",
          message: `Periodo de ${PERIOD_DAYS} días procesado.`,
        });
      }
      return;
    }
    if (!estadoMessage) return;
    const local = ESTADO_BACK_A_LOCAL[estadoMessage.estado] ?? simStatus;
    if (local !== simStatus) setSimStatus(local);
    if (estadoMessage.estado === "FINALIZADA") {
      toast.push({
        type: "success",
        title: "Simulación completada",
        message: estadoMessage.mensaje,
      });
    }
    if (estadoMessage.estado === "DETENIDA" && simStatus !== "idle") {
      setSessionId(null);
      setCurrentSimTimeUtc(null);
      clearSimulationData();
    }
  }, [mockState, estadoMessage]);

  const progress = useMemo(() => {
    if (USE_MOCK) return mockState?.progress ?? lastMockState?.progress ?? 0;
    return getSimulationProgress(
      executionElapsedMs,
      PERIOD_DAYS,
      simulatedDayDurationMs,
    );
  }, [executionElapsedMs, mockState, lastMockState, simulatedDayDurationMs]);

  useEffect(() => {
    if (simStatus === "idle") return;
    if (!tick?.type) return;

    if (tick.type === "TICK" && !tickReceivedRef.current) {
      tickReceivedRef.current = true;
      cancelarWatchdogIniciarTick();
    }

    if (tick.type === "VENTANA_READY") {
      const windowId = tick.ventana;
      if (ventanasCargadasRef.current.has(windowId)) return;
      ventanasCargadasRef.current.add(windowId);
      (async () => {
        const [ventanaData, vuelosData] = await Promise.all([
          obtenerVentanaSimulacion(sessionId, windowId),
          tick.vuelosDesde
            ? obtenerVuelosSimulacion(sessionId, tick.vuelosDesde, tick.vuelosHasta)
            : Promise.resolve([]),
        ]);
        const adaptedFlights = (vuelosData ?? []).map(adaptFlightInstance);
        setSimulationPanelData((prev) => {
          const flights = new Map(prev.flights);
          for (const f of adaptedFlights) {
            flights.set(f.idVueloInstancia ?? f.id, { ...f, ticksAusente: 0 });
          }
          const bags = new Map(prev.bags);
          for (const b of ventanaData.maletas ?? []) {
            bags.set(b.idMaleta, { ...b, ticksAusente: 0 });
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
        const simTimeMs = currentSimTimeUtc
            ? Date.parse(`${currentSimTimeUtc}Z`)
            : null;
        if (Number.isFinite(simTimeMs)) {
          const completion = completionTimesRef.current;
          for (const b of ventanaData.maletas ?? []) {
            if (b.estado === "ENTREGADA" && !completion.has(b.idMaleta)) {
              completion.set(b.idMaleta, simTimeMs);
            }
          }
          for (const r of ventanaData.rutas ?? []) {
            if (r.estado === "COMPLETADA" && !completion.has(r.idRuta)) {
              completion.set(r.idRuta, simTimeMs);
            }
          }
        }
      })()
        .then(() => publish("/app/simulacion/periodo/ventana-lista", { sessionId }));
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

    try {
      if (Array.isArray(tick.estadosVuelos)) {
        vueloStateMap = Object.fromEntries(
          tick.estadosVuelos.map((v) => [v.id, v]),
        );
      }
    } catch (e) {
      // ignore
    }

    try {
      if (Array.isArray(tick.estadosMaletas)) {
        maletaStateMap = Object.fromEntries(
          tick.estadosMaletas.map((m) => [m.id, m]),
        );
      }
    } catch (e) {
      // ignore
    }

    try {
      if (Array.isArray(tick.estadosRutas)) {
        rutaStateMap = Object.fromEntries(
          tick.estadosRutas.map((r) => [r.id, r]),
        );
      }
    } catch (e) {
      // ignore
    }

    setSimulationPanelData((prev) => {
      const simTimeMs = Date.parse(`${tick.simTime}Z`);
      const cutoffMs = Number.isFinite(simTimeMs) ? simTimeMs - SIMULATED_TWO_HOURS_MS : null;
      const completion = completionTimesRef.current;

      for (const st of tick.estadosMaletas ?? []) {
        if (st.e === 2 && !completion.has(st.id)) {
          completion.set(st.id, simTimeMs);
        }
      }
      for (const st of tick.estadosVuelos ?? []) {
        if ((st.e === 3 || st.e === 4) && !completion.has(st.id)) {
          completion.set(st.id, simTimeMs);
        }
      }

      const prunedIds = new Set();
      if (cutoffMs != null) {
        for (const [id, completedAt] of completion) {
          if (completedAt < cutoffMs) {
            prunedIds.add(id);
            completion.delete(id);
          }
        }
      }

      const updatedBags = updateEstadosOnly(prev.bags, maletaStateMap, ENUM_MALETA, "estado",
        (st, bag) => (st.e === 2 ? { fechaLlegada: bag.fechaLlegada ?? tick.simTime } : {}));
      for (const id of prunedIds) updatedBags.delete(id);

      const updatedRoutes = updateEstadosOnly(prev.routes, rutaStateMap, ENUM_RUTA, "estado");

      const updatedFlights = updateEstadosOnly(prev.flights, vueloStateMap, ENUM_VUELO, "status",
        (st, flight) => ({ used: getUpdatedFlightOccupancy(st, flight) }));
      for (const id of prunedIds) updatedFlights.delete(id);

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

    return;
  }, [tick]);

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

    const ratio = 86400000 / simulatedDayDurationMs;
    return startMs + executionElapsedMs * ratio;
  }, [
    currentSimTimeUtc,
    simStatus,
    executionElapsedMs,
    simulatedDayDurationMs,
  ]);

  const mapVisibleFlights = useMemo(() => {
    if (simStatus === "running") {
      if (!simulationPanelData?.flights || !simulatedNowMs) return [];
      const nowMs = simulatedNowMs;
      const out = [];
      for (const flight of simulationPanelData.flights.values()) {
        const status = flight.status ?? flight.estado;
        if (normalizeFlightStatus(status) === "CANCELADO") continue;
        if (!hasOccupiedCapacity(flight)) continue;
        const salidaMs = Date.parse(`${flight.depTime}Z`);
        const llegadaMs = Date.parse(`${flight.arrTime}Z`);
        if (!Number.isFinite(salidaMs) || !Number.isFinite(llegadaMs)) continue;
        if (!(nowMs >= salidaMs && nowMs < llegadaMs)) continue;
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
  }, [simulationPanelData?.flights, simulatedNowMs, simStatus]);

  const simulationClock = useMemo(() => {
    if (simulatedNowMs === null || Number.isNaN(simulatedNowMs)) {
      return formatUtcDateTimeDisplay(null);
    }
    return formatUtcDateTimeDisplay(new Date(simulatedNowMs));
  }, [simulatedNowMs]);

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
        : undefined;
    }
    if (!tick || tick.type !== "TICK") return undefined;
    return {
      bagsInTransit: tick.maletasEnTransito ?? 0,
      bagsDelivered:
        tick.maletasEntregadas ?? tick.maletasEntregadasATiempo ?? 0,
      bagsUnassigned: tick.maletasNoAsignadas ?? tick.maletasSinRuta ?? 0,
      activeFlights: tick.vuelosActivos ?? 0,
      freeCapacityPct: tick.capacidadLibrePct ?? 0,
    };
  }, [tick, mockState, hasActiveRun]);

  const MAX_INICIAR_TICK_RETRIES = 3;
  const INICIAR_TICK_WATCHDOG_MS = 2000;

  const cancelarWatchdogIniciarTick = () => {
    if (iniciarTickWatchdogRef.current) {
      clearTimeout(iniciarTickWatchdogRef.current);
      iniciarTickWatchdogRef.current = null;
    }
  };

  const enviarIniciarTick = async (targetSessionId) => {
    cancelarWatchdogIniciarTick();
    try {
      await publish("/app/simulacion/periodo/iniciar-tick", {
        sessionId: targetSessionId,
      });
    } catch (err) {
      // fall through to retry logic
    }
    iniciarTickWatchdogRef.current = setTimeout(async () => {
      if (tickReceivedRef.current) return;
      if (iniciarTickRetriesRef.current < MAX_INICIAR_TICK_RETRIES) {
        iniciarTickRetriesRef.current += 1;
        enviarIniciarTick(targetSessionId);
      } else {
        cancelarWatchdogIniciarTick();
        toast.push({
          type: "error",
          title: "No se pudo iniciar la simulación",
          message:
            "El backend no respondió después de varios intentos. Intente de nuevo.",
        });
      }
    }, INICIAR_TICK_WATCHDOG_MS);
  };

  const handleStart = async () => {
    const startDateTime = `${startDate}T${startTime || "00:00"}:00`;
    setSimStatus("starting");
    setSessionId(null);
    setCurrentSimTimeUtc(null);
    clearSimulationData();
    toast.push({
      type: "info",
      title: "Iniciando simulación",
      message: `Preparando datos desde ${startDate} ${startTime || "00:00"}.`,
      duration: 2500,
    });

    try {
      const result = await iniciarSimulacionPeriodo({
        fechaInicio: startDate,
        horaInicio: startTime || "00:00",
        fechaHoraInicio: startDateTime,
        totalDias: PERIOD_DAYS,
      });
      const newSessionId = result.sessionId;
      setSessionId(newSessionId);
      setCancelledFlightIds(new Set());
      setLastMockState(null);
      setRunId((current) => current + 1);

      const base = await obtenerBaseSimulacion(newSessionId);
      const adaptedAirports = Array.isArray(base.aeropuertos)
        ? base.aeropuertos.map(adaptAirport)
        : [];
      setMapAirports(adaptedAirports);
      setSimulatedDayDurationMs(base.duracionDiaSimuladoMs);
      setWindowSizeMinutes(base.windowSizeMinutes);
      setWindowSpacingMinutes(base.windowSpacingMinutes);

      const primeraVentana = base.primeraVentana ?? "W0001";
      const bucketActual = parseInt(primeraVentana.substring(1), 10);
      const segundaVentana = "W" + String(bucketActual + 1).padStart(4, "0");
      const ultimaVentana = "W" + String(bucketActual + 24).padStart(4, "0");
      const [ventana1, ventana2, vuelosData] = await Promise.all([
        obtenerVentanaSimulacion(newSessionId, primeraVentana),
        obtenerVentanaSimulacion(newSessionId, segundaVentana),
        obtenerVuelosSimulacion(newSessionId, primeraVentana, ultimaVentana),
      ]);
      ventanasCargadasRef.current.add(primeraVentana);
      ventanasCargadasRef.current.add(segundaVentana);
      const adaptedFlights = (vuelosData ?? []).map(adaptFlightInstance);
      const initialFlights = new Map();
      for (const f of adaptedFlights) {
        initialFlights.set(f.idVueloInstancia ?? f.id, { ...f, ticksAusente: 0 });
      }
      const initialBags = new Map();
      for (const b of [...(ventana1.maletas ?? []), ...(ventana2.maletas ?? [])]) {
        initialBags.set(b.idMaleta, { ...b, ticksAusente: 0 });
      }
      const initialRoutes = new Map();
      for (const r of [...(ventana1.rutas ?? []), ...(ventana2.rutas ?? [])]) {
        initialRoutes.set(r.idRuta, { ...r, ticksAusente: 0 });
      }
      const initialOrders = new Map();
      for (const o of [...(ventana1.pedidos ?? []), ...(ventana2.pedidos ?? [])]) {
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
      setSimStatus("running");

      tickReceivedRef.current = false;
      iniciarTickRetriesRef.current = 0;
      enviarIniciarTick(newSessionId);

      toast.push({
        type: "info",
        title: "Simulación iniciada",
        message: `Inicio: ${startDate} ${startTime || "00:00"} · ${PERIOD_DAYS} días`,
      });
    } catch (err) {
      setSimStatus("idle");
      setSessionId(null);
      setCurrentSimTimeUtc(null);
      clearSimulationData();
      toast.push({
        type: "error",
        title: "No se pudo iniciar",
        message: err.message,
      });
    }
  };

  const handleResume = async () => {
    if (USE_MOCK) return;
    try {
      await publish("/app/simulacion/periodo/reanudar", { sessionId });
    } catch (err) {
      toast.push({
        type: "error",
        title: "No se pudo reanudar",
        message: err.message,
      });
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
        await stopPeriodSim();
      } else {
        await publish("/app/simulacion/periodo/detener", { sessionId });
      }
      toast.push({ type: "warning", title: "Simulación detenida" });
    } catch (err) {
      toast.push({
        type: "error",
        title: "No se pudo detener",
        message: err.message,
      });
    }
  };

  const displayedDay = getDisplayedDay(progress, hasActiveRun);

  const mapOverlay = hasActiveRun ? (
    <div className="bg-surface-2/85 backdrop-blur border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-4 py-3 flex items-center justify-center gap-6">
      <div>
        <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">
          Cronometro
        </div>
        <div className="text-lg font-bold text-slate-100 tabular-nums">
          {formatElapsedHMS(executionElapsedMs)}
        </div>
      </div>
      <div className="h-9 w-px bg-slate-700" />
      <div>
        <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">
          Fecha/hora simulada
        </div>
        <div className="text-lg font-bold text-info tabular-nums">
          {simulationClock.date} - {simulationClock.time} UTC
          {displayedDay
            ? ` - dia ${displayedDay}/${PERIOD_DAYS}`
            : ""}
        </div>
      </div>
    </div>
  ) : null;

  const header = (
    <div className="flex items-center gap-3 flex-wrap">
      <div className="flex flex-col">
        <label
          htmlFor="period-start"
          className="text-[10px] text-slate-400 uppercase tracking-wider font-medium"
        >
          Fecha de inicio
        </label>
        <input
          id="period-start"
          type="date"
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
          disabled={isSimulationLocked}
          className="bg-surface-2 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-slate-200 outline-none focus:border-blue-500 disabled:opacity-50"
        />
      </div>

      <div className="flex flex-col">
        <label
          htmlFor="period-start-time"
          className="text-[10px] text-slate-400 uppercase tracking-wider font-medium"
        >
          Hora de inicio
        </label>
        <input
          id="period-start-time"
          type="time"
          value={startTime}
          onChange={(e) => setStartTime(e.target.value)}
          disabled={isSimulationLocked}
          className="bg-surface-2 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-slate-200 outline-none focus:border-blue-500 disabled:opacity-50"
        />
      </div>

      <div className="flex flex-col">
        <span className="text-[10px] text-slate-400 uppercase tracking-wider font-medium">
          Duración
        </span>
        <span className="px-3 py-1.5 bg-surface-2 border border-slate-700 rounded-lg text-sm font-bold text-success">
          {PERIOD_DAYS} días
        </span>
      </div>

      <label className="flex items-center gap-2 self-end px-3 py-2 bg-surface-2 border border-slate-700 rounded-lg text-sm text-slate-200 cursor-pointer select-none">
        <input
          type="checkbox"
          checked={showRouteLines}
          onChange={(e) => setShowRouteLines(e.target.checked)}
          className="h-4 w-4 rounded border-slate-600 bg-surface-1 text-blue-500 focus:ring-blue-500"
        />
        <span>Mostrar líneas</span>
      </label>

      {simStatus === "idle" || simStatus === "done" ? (
        <button
          type="button"
          onClick={handleStart}
          className="self-end bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors"
        >
          <Play className="w-4 h-4" /> Ejecutar
        </button>
      ) : isStarting ? (
        <button
          type="button"
          disabled
          className="self-end bg-blue-600/60 text-white px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm cursor-wait"
        >
          <Play className="w-4 h-4" /> Iniciando...
        </button>
      ) : simStatus === "paused" ? (
        <>
          <button
            type="button"
            onClick={handleResume}
            className="self-end bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors"
          >
            <RotateCw className="w-4 h-4" /> Reanudar
          </button>
          <button
            type="button"
            onClick={handleStop}
            className="self-end bg-danger/10 hover:bg-danger/20 text-danger border border-danger/40 px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors"
          >
            <Square className="w-4 h-4" /> Detener
          </button>
        </>
      ) : (
        <button
          type="button"
          onClick={handleStop}
          className="self-end bg-danger/10 hover:bg-danger/20 text-danger border border-danger/40 px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors"
        >
          <Square className="w-4 h-4" /> Detener
        </button>
      )}

      {simStatus !== "idle" && (
        <div className="flex flex-col w-44 self-end">
          <div className="flex justify-between text-[10px] text-slate-400 mb-1">
            <span>Progreso</span>
            <span>{progress.toFixed(0)}%</span>
          </div>
          <div className="h-2 bg-surface-2 rounded-full overflow-hidden border border-slate-700">
            <div
              className={`h-full transition-all ${simStatus === "done" ? "bg-success" : simStatus === "paused" ? "bg-warning" : "bg-info"}`}
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
      )}
    </div>
  );

  return (
    <MapDashboard
      title={`Simulación de Periodo · ${PERIOD_DAYS} días`}
      header={header}
      mapOverlay={mapOverlay}
      showMapClock={false}
      showMapFlights={hasActiveRun}
      showMapRouteLines={showRouteLines}
      animateMapFlights={simStatus === "running"}
      mapAutoload={false}
      airports={mapAirports}
      flights={mapVisibleFlights}
      simulatedNowMs={simulatedNowMs}
      simulatedDayDurationMs={simulatedDayDurationMs}
      date={simulationClock.date}
      time={simulationClock.time}
      metrics={liveMetrics}
    />
  );
}
