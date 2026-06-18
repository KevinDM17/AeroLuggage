import { useEffect, useMemo, useRef, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { Play, Square, RotateCw, AlertTriangle, X } from "lucide-react";
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



const PERIOD_DAYS = 5;
const CLOCK_REFRESH_MS = 500;
const MAP_REFRESH_MS = 500;

const METRICAS_COLAPSO = [
  { key: "aeropuertosColapsados", label: "Aeropuertos colapsados" },
  { key: "vuelosColapsados", label: "Vuelos colapsados" },
  { key: "maletasEvaluadasSinRuta", label: "Maletas sin ruta" },
];

const ESTADO_BACK_A_LOCAL = {
  INICIADA: "running",
  REANUDADA: "running",
  PAUSADA: "paused",
  DETENIDA: "idle",
  FINALIZADA: "done",
  COLAPSO: "collapsed",
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
  const [showColapsoModal, setShowColapsoModal] = useState(false);
  const [colapsoInfo, setColapsoInfo] = useState(null);
  const startSimMsRef = useRef(null);
  const ventanasCargadasRef = useRef(new Set());
  const lastMapFlightsRef = useRef([]);
  const flightMetadataRef = useRef(new Map());
  const iniciarTickWatchdogRef = useRef(null);
  const iniciarTickRetriesRef = useRef(0);
  const tickReceivedRef = useRef(false);
  const pendienteIniciarTickRef = useRef(null);
  const planCompletionTimeoutRef = useRef(null);

  const clearSimulationData = () => {
    ventanasCargadasRef.current.clear();
    flightMetadataRef.current.clear();
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
      if (planCompletionTimeoutRef.current) {
        clearTimeout(planCompletionTimeoutRef.current);
        planCompletionTimeoutRef.current = null;
      }
      pendienteIniciarTickRef.current = null;
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
    simStatus === "running" || simStatus === "paused" || simStatus === "done" || simStatus === "collapsed";

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
    if (estadoMessage.estado === "COLAPSO") {
      setSimStatus("collapsed");
      setColapsoInfo(estadoMessage);
      setShowColapsoModal(true);
    }
    if (estadoMessage.estado === "DETENIDA" && simStatus !== "idle") {
      setSessionId(null);
      setCurrentSimTimeUtc(null);
      clearSimulationData();
    }
    if (estadoMessage.estado === "PLANIFICACION_COMPLETADA"
        && pendienteIniciarTickRef.current) {
      const sid = pendienteIniciarTickRef.current;
      pendienteIniciarTickRef.current = null;
      if (planCompletionTimeoutRef.current) {
        clearTimeout(planCompletionTimeoutRef.current);
        planCompletionTimeoutRef.current = null;
      }
      enviarIniciarTick(sid);
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
        const vuelosDataRaw = tick.vuelosVentana
          ? await obtenerVuelosSimulacion(sessionId, tick.vuelosVentana, tick.vuelosVentana)
          : [];
        const [ventanaData] = await Promise.all([
          obtenerVentanaSimulacion(sessionId, windowId),
        ]);
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
      const updatedFlights = new Map(prev.flights);
      const metadata = new Map(flightMetadataRef.current);

      // FASE 1: Remover FINALIZADO y CANCELADO inmediatamente
      for (const st of tick.estadosVuelos ?? []) {
        if (st.e === 3 || st.e === 4) {
          updatedFlights.delete(st.id);
          metadata.delete(st.id);
        }
      }

      // FASE 2: Actualizar estados de vuelos existentes
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

      // FASE 3: Añadir nuevos CONFIRMADO (1) o EN_PROGRESO (2) desde metadata
      for (const st of tick.estadosVuelos ?? []) {
        if ((st.e === 1 || st.e === 2) && !updatedFlights.has(st.id)) {
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

      // Actualizar metadataRef
      flightMetadataRef.current = metadata;

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
      if (!simulationPanelData?.flights) return [];
      const out = [];
      for (const flight of simulationPanelData.flights.values()) {
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
    setSimStatus("running");
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
      if (planCompletionTimeoutRef.current) {
        clearTimeout(planCompletionTimeoutRef.current);
        planCompletionTimeoutRef.current = null;
      }
      pendienteIniciarTickRef.current = null;
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
      const statusFilter = (f) =>
        f.status === "EN_PROGRESO" || f.status === "CONFIRMADO";
      const initialFlights = new Map();
      for (const [id, f] of metadata) {
        if (statusFilter(f)) initialFlights.set(id, f);
      }
      const initialBags = new Map();
      for (const b of ventana1.maletas ?? []) {
        initialBags.set(b.idMaleta, { ...b, ticksAusente: 0 });
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

      tickReceivedRef.current = false;
      iniciarTickRetriesRef.current = 0;
      pendienteIniciarTickRef.current = newSessionId;
      cancelarWatchdogIniciarTick();
      if (planCompletionTimeoutRef.current) {
        clearTimeout(planCompletionTimeoutRef.current);
      }
      planCompletionTimeoutRef.current = setTimeout(() => {
        if (!pendienteIniciarTickRef.current) return;
        const sid = pendienteIniciarTickRef.current;
        pendienteIniciarTickRef.current = null;
        enviarIniciarTick(sid);
      }, 30000);

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
    setShowColapsoModal(false);
    setColapsoInfo(null);
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
    <div className="bg-surface-2/85 m-4 backdrop-blur border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-4 py-3 flex items-center justify-center gap-6">
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
          Fecha simulada
        </div>
        <div className="text-lg font-bold text-info tabular-nums">
          {simulationClock.date}
        </div>
      </div>
      <div className="h-9 w-px bg-slate-700" />
      <div>
        <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">
          Hora simulada
        </div>
        <div className="text-lg font-bold text-info tabular-nums">
          {simulationClock.time} UTC
        </div>
      </div>
      <div className="h-9 w-px bg-slate-700" />
      <div>
        <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">
          Día de simulación
        </div>
        <div className="text-lg font-bold text-info tabular-nums">
          {displayedDay
            ? `Dia ${displayedDay}/${PERIOD_DAYS}`
            : ""}
        </div>
      </div>
      <div className="h-9 w-px bg-slate-700" />
      <button
        type="button"
        onClick={() => setShowRouteLines((v) => !v)}
        className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
          showRouteLines
            ? "bg-blue-600/20 text-blue-400 border border-blue-500/40 hover:bg-blue-600/30"
            : "bg-surface-2 text-slate-400 border border-slate-700 hover:text-slate-200"
        }`}
      >
        Mostrar líneas
      </button>
      <div className="h-9 w-px bg-slate-700" />
      <button
        type="button"
        onClick={handleStop}
        className="bg-danger/10 hover:bg-danger/20 text-danger border border-danger/40 rounded-lg px-3 py-1.5 transition-colors"
        title="Detener"
      >
        <Square className="w-5 h-5" />
      </button>
    </div>
  ) : (
    <div className="bg-surface-2/85 m-4 backdrop-blur border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-4 py-3 flex items-center justify-center gap-6">
      <div className="flex flex-col">
        <label
          htmlFor="period-start"
          className="text-[10px] text-slate-400 font-medium uppercase tracking-wider"
        >
          Fecha de inicio
        </label>
        <input
          id="period-start"
          type="date"
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
          className="bg-surface-2 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-slate-200 outline-none focus:border-blue-500"
        />
      </div>
      <div className="h-9 w-px bg-slate-700" />
      <div className="flex flex-col">
        <label
          htmlFor="period-start-time"
          className="text-[10px] text-slate-400 font-medium uppercase tracking-wider"
        >
          Hora de inicio
        </label>
        <input
          id="period-start-time"
          type="time"
          value={startTime}
          onChange={(e) => setStartTime(e.target.value)}
          className="bg-surface-2 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-slate-200 outline-none focus:border-blue-500"
        />
      </div>
      <div className="h-9 w-px bg-slate-700" />
      <button
        type="button"
        onClick={handleStart}
        className="self-end bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors"
      >
        <Play className="w-4 h-4" /> Ejecutar
      </button>
    </div>
  );

  const loadingModal = simStatus === "starting" ? (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 backdrop-blur-[2px]">
      <div className="bg-surface-2 border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-8 py-6 flex flex-col items-center gap-4 max-w-sm mx-4">
        <div className="w-10 h-10 border-4 border-blue-500/30 border-t-blue-500 rounded-full animate-spin" />
        <div className="text-center">
          <div className="text-lg font-semibold text-slate-100">
            Preparando simulación…
          </div>
          <div className="text-sm text-slate-400 mt-1">
            Planificando rutas iniciales, esto puede tomar unos segundos
          </div>
        </div>
      </div>
    </div>
  ) : null;

  const colapsoModal = showColapsoModal ? (
    <div className="fixed inset-0 z-[10000] flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="bg-surface-2 border border-danger/40 shadow-[0_12px_35px_rgba(0,0,0,0.55)] rounded-xl px-8 py-6 max-w-md w-full mx-4">
        <div className="flex items-start justify-between gap-4 mb-4">
          <div className="flex items-center gap-3">
            <AlertTriangle className="w-8 h-8 text-danger flex-shrink-0" />
            <h2 className="text-xl font-bold text-danger">¡Colapso!</h2>
          </div>
          <button
            type="button"
            onClick={() => setShowColapsoModal(false)}
            className="text-slate-400 hover:text-slate-200 transition-colors flex-shrink-0"
            title="Cerrar"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
        {colapsoInfo?.mensaje && (
          <p className="text-slate-300 text-sm mb-4">{colapsoInfo.mensaje}</p>
        )}
        <ul className="text-sm text-slate-400 space-y-1.5 mb-4">
          {METRICAS_COLAPSO.map(({ key, label }) => {
            const valor = colapsoInfo?.[key];
            if (valor == null || valor <= 0) return null;
            return (
              <li key={key} className="flex justify-between">
                <span>{label}</span>
                <span className="text-slate-200 font-medium tabular-nums">{valor}</span>
              </li>
            );
          })}
        </ul>
        <p className="text-xs text-slate-500 leading-relaxed">
          La simulación se ha detenido. Puedes cerrar este panel o usar el botón{" "}
          <Square className="inline w-3 h-3 text-danger" /> en el panel inferior
          para finalizar la simulación.
        </p>
      </div>
    </div>
  ) : null;

  const header = (
    <div className="flex items-center gap-3 flex-wrap">
      {simStatus === "paused" ? (
        <button
          type="button"
          onClick={handleResume}
          className="self-end bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors"
        >
          <RotateCw className="w-4 h-4" /> Reanudar
        </button>
      ) : null}
    </div>
  );

  return (
    <>
      {loadingModal}
      {colapsoModal}
      <MapDashboard
      title=""
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
      progress={progress}
      simStatus={simStatus}
    />
    </>
  );
}
