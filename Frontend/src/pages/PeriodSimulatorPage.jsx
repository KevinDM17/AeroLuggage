import { useEffect, useMemo, useRef, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { Clock, Play, SlidersHorizontal, Square, RotateCw, AlertTriangle, X, CheckCircle2 } from "lucide-react";
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

const RESUMEN_METRICAS_COLAPSO = [
  { key: "aeropuertosColapsados", label: "Aeropuertos colapsados" },
  { key: "vuelosColapsados", label: "Vuelos afectados" },
  { key: "maletasEvaluadasSinRuta", label: "Maletas sin ruta" },
  { key: "bagsInTransit", label: "Maletas en transito" },
  { key: "bagsDelivered", label: "Maletas entregadas" },
  { key: "activeFlights", label: "Vuelos activos" },
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
  activeFlights: 0,
  airportCapacityPct: 0,
  flightCapacityPct: 0,
};

const formatSummaryValue = (value, suffix = "") => {
  if (value == null || Number.isNaN(Number(value))) return null;
  return `${Number(value).toLocaleString("es-PE")}${suffix}`;
};

const getCollapseSummary = (info) => {
  const rawMessage = String(info?.mensaje ?? "").trim();
  const compactMessage = rawMessage
    .replace(/\s*\|\s*/g, " | ")
    .replace(/\s+/g, " ")
    .trim();

  if (/sin ruta asignada/i.test(compactMessage)) {
    return {
      title: "Saturacion por falta de rutas disponibles",
      description:
        "La simulacion llego al colapso porque se acumularon maletas evaluadas dentro de la ventana actual sin una ruta valida para continuar su traslado.",
    };
  }

  if (/aeropuerto/i.test(compactMessage) && /colaps/i.test(compactMessage)) {
    return {
      title: "Capacidad aeroportuaria superada",
      description:
        "La red dejo de absorber la demanda operativa y uno o mas aeropuertos superaron su capacidad de manejo durante la simulacion.",
    };
  }

  if (/vuelo/i.test(compactMessage) && /colaps|satur/i.test(compactMessage)) {
    return {
      title: "Red de vuelos saturada",
      description:
        "La operacion llego a un punto de saturacion en el que los vuelos disponibles ya no pudieron sostener el flujo de maletas planificado.",
    };
  }

  return {
    title: "Operacion colapsada",
    description:
      "La simulacion detecto una condicion critica y detuvo la ejecucion para evitar seguir acumulando operaciones inconsistentes.",
  };
};

const buildStablePlanningSnapshot = ({
  windowId,
  simTime,
  flights,
  orders,
  routes,
}) => {
  const clock = formatUtcDateTimeDisplay(simTime ? new Date(`${simTime}Z`) : null);

  return {
    windowId,
    dateLabel: clock.date,
    timeLabel: clock.time,
    flightsCount: Array.isArray(flights) ? flights.length : 0,
    ordersCount: Array.isArray(orders) ? orders.length : 0,
    routesCount: Array.isArray(routes) ? routes.length : 0,
  };
};

const buildFinalSummaryMetrics = ({ liveMetrics, progress, periodDays }) => [
  {
    key: "periodoProcesado",
    label: "Periodo procesado",
    value: `${Math.round(progress ?? 100)}%`,
  },
  {
    key: "diasSimulados",
    label: "Dias simulados",
    value: `${periodDays}`,
  },
  {
    key: "bagsDelivered",
    label: "Maletas entregadas",
    value: formatSummaryValue(liveMetrics?.bagsDelivered ?? 0),
  },
  {
    key: "bagsInTransit",
    label: "Maletas en transito",
    value: formatSummaryValue(liveMetrics?.bagsInTransit ?? 0),
  },
  {
    key: "activeFlights",
    label: "Vuelos activos al cierre",
    value: formatSummaryValue(liveMetrics?.activeFlights ?? 0),
  },
];

export default function PeriodSimulatorPage() {
  const toast = useToast();
  const publish = useStompPublish();
  const {
    simulationPanelData,
    setSimulationPanelData,
    resetSimulationPanelData,
    collapseSidebars,
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
  const [showFinalSummaryModal, setShowFinalSummaryModal] = useState(false);
  const [colapsoInfo, setColapsoInfo] = useState(null);
  const [finalSummaryInfo, setFinalSummaryInfo] = useState(null);
  const [lastStablePlanning, setLastStablePlanning] = useState(null);
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
    setShowColapsoModal(false);
    setShowFinalSummaryModal(false);
    setColapsoInfo(null);
    setFinalSummaryInfo(null);
    setLastStablePlanning(null);
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
        setShowColapsoModal(false);
        setColapsoInfo(null);
        setFinalSummaryInfo({
          mensaje: `Periodo de ${PERIOD_DAYS} dias procesado.`,
        });
        setShowFinalSummaryModal(true);
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
      setShowColapsoModal(false);
      setColapsoInfo(null);
      setFinalSummaryInfo(estadoMessage);
      setShowFinalSummaryModal(true);
      toast.push({
        type: "success",
        title: "Simulación completada",
        message: estadoMessage.mensaje,
      });
    }
    if (estadoMessage.estado === "COLAPSO") {
      setSimStatus("collapsed");
      setShowFinalSummaryModal(false);
      setFinalSummaryInfo(null);
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
        setLastStablePlanning(
          buildStablePlanningSnapshot({
            windowId,
            simTime:
              ventanaData.fechaSimulada ??
              tick.simTime ??
              currentSimTimeUtc,
            flights: adaptedFlights,
            orders: ventanaData.pedidos ?? [],
            routes: ventanaData.rutas ?? [],
          }),
        );
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

      // FASE 1: Remover FINALIZADO (e===3) inmediatamente. Los CANCELADO (e===4)
      // se CONSERVAN para poder filtrarlos por estado en la lista; FASE 2 les
      // pondra status "CANCELADO" y no se dibujan en el mapa (solo EN_PROGRESO).
      for (const st of tick.estadosVuelos ?? []) {
        if (st.e === 3) {
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

      // FASE 3: Añadir desde metadata los vuelos que aún no están en la lista:
      // CONFIRMADO (1), EN_PROGRESO (2) o CANCELADO (4). El CANCELADO es clave
      // porque en periodo solo se pueden cancelar vuelos PROGRAMADO, que nunca
      // estuvieron en la lista; así aparecen para poder filtrarlos por estado.
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

      // Actualizar metadataRef
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
      for (const [id, order] of prev.orders) {
        const s = String(order.status ?? "").toUpperCase();
        if (s === "ENTREGADO" || s === "FINALIZADO" || s === "ENVIADO") {
          prev.orders.delete(id);
        }
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

  const formattedStartDate = useMemo(() => {
    if (!startDate) return "--";
    const [y, m, d] = startDate.split("-");
    return `${d}/${m}/${y}`;
  }, [startDate]);

  const simulatedElapsedLabel = useMemo(() => {
    const startMs = startSimMsRef.current;
    if (simulatedNowMs == null || startMs == null) return "--";
    const totalMinutes = Math.max(0, Math.round((simulatedNowMs - startMs) / 60000));
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return `${hours}h ${minutes}m`;
  }, [simulatedNowMs]);

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
    for (const f of simulationPanelData.flights.values()) {
      const status = normalizeFlightStatus(f.status);
      if (status === "EN_PROGRESO") {
        flightTotalCap += f.capacity ?? 0;
        flightTotalUsed += f.used ?? 0;
      }
    }
    const flightCapacityPct = flightTotalCap > 0 ? Math.round((flightTotalUsed / flightTotalCap) * 100) : 0;
    return {
      bagsInTransit: tick.maletasEnTransito ?? 0,
      bagsDelivered:
        tick.maletasEntregadas ?? tick.maletasEntregadasATiempo ?? 0,
      activeFlights: tick.vuelosActivos ?? 0,
      airportCapacityPct,
      flightCapacityPct,
    };
  }, [tick, mockState, hasActiveRun, mapAirports, simulationPanelData]);

  const collapseSummary = useMemo(
    () => getCollapseSummary(colapsoInfo),
    [colapsoInfo],
  );

  const collapseStats = useMemo(() => {
    if (!showColapsoModal) return [];

    const combined = {
      aeropuertosColapsados: colapsoInfo?.aeropuertosColapsados,
      vuelosColapsados: colapsoInfo?.vuelosColapsados,
      maletasEvaluadasSinRuta: colapsoInfo?.maletasEvaluadasSinRuta,
      bagsInTransit: liveMetrics?.bagsInTransit,
      bagsDelivered: liveMetrics?.bagsDelivered,
      activeFlights: liveMetrics?.activeFlights,
    };

    return RESUMEN_METRICAS_COLAPSO
      .map(({ key, label }) => {
        const value = combined[key];
        if (value == null) return null;
        return { key, label, value: formatSummaryValue(value) };
      })
      .filter(Boolean);
  }, [colapsoInfo, liveMetrics, showColapsoModal]);

  const collapseEntityDetails = useMemo(() => {
    if (!showColapsoModal) return [];

    const details = [];
    if (colapsoInfo?.aeropuertosColapsados > 0 && colapsoInfo?.aeropuertoColapsadoDetalle) {
      details.push({
        key: "aeropuertoColapsadoDetalle",
        label: "Aeropuerto colapsado",
        value: colapsoInfo.aeropuertoColapsadoDetalle,
      });
    }
    if (colapsoInfo?.vuelosColapsados > 0 && colapsoInfo?.vueloColapsadoDetalle) {
      details.push({
        key: "vueloColapsadoDetalle",
        label: "Vuelo colapsado",
        value: colapsoInfo.vueloColapsadoDetalle,
      });
    }

    return details;
  }, [colapsoInfo, showColapsoModal]);

  const collapseStablePlanningSummary = useMemo(() => {
    if (!lastStablePlanning) return null;

    return [
      {
        label: "Ventana estable",
        value: lastStablePlanning.windowId ?? "-",
      },
      {
        label: "Fecha simulada",
        value: lastStablePlanning.dateLabel ?? "-",
      },
      {
        label: "Hora simulada",
        value: lastStablePlanning.timeLabel
          ? `${lastStablePlanning.timeLabel} UTC`
          : "-",
      },
      {
        label: "Vuelos planificados",
        value: formatSummaryValue(lastStablePlanning.flightsCount ?? 0),
      },
      {
        label: "Pedidos considerados",
        value: formatSummaryValue(lastStablePlanning.ordersCount ?? 0),
      },
      {
        label: "Rutas generadas",
        value: formatSummaryValue(lastStablePlanning.routesCount ?? 0),
      },
    ];
  }, [lastStablePlanning]);

  const finalSummaryMetrics = useMemo(
    () => buildFinalSummaryMetrics({ liveMetrics, progress, periodDays: PERIOD_DAYS }),
    [liveMetrics, progress],
  );

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
      setLastStablePlanning(
        buildStablePlanningSnapshot({
          windowId: primeraVentana,
          simTime: ventana1.fechaSimulada ?? result.fechaHoraInicio ?? startDateTime,
          flights: adaptedFlights,
          orders: ventana1.pedidos ?? [],
          routes: ventana1.rutas ?? [],
        }),
      );
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
      pendienteIniciarTickRef.current = null;
      cancelarWatchdogIniciarTick();
      if (planCompletionTimeoutRef.current) {
        clearTimeout(planCompletionTimeoutRef.current);
        planCompletionTimeoutRef.current = null;
      }
      enviarIniciarTick(newSessionId);

      toast.push({
        type: "info",
        title: "Simulación iniciada",
        message: `Inicio: ${startDate} ${startTime || "00:00"} · ${PERIOD_DAYS} días`,
      });
      collapseSidebars();
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

  const mapOverlays = hasActiveRun ? [
    {
      id: "period-start-panel",
      icon: <Clock className="w-4 h-4" />,
      content: (
        <div className="bg-surface-2/85 backdrop-blur border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-4 py-3 flex items-center gap-4">
          <div className="shrink-0">
            <div className="text-[9px] text-slate-400 font-medium uppercase tracking-wider">
              Inicio sim.
            </div>
            <div className="text-sm font-bold text-slate-100 tabular-nums whitespace-nowrap">
              {formattedStartDate}  {startTime || "--:--"} UTC
            </div>
          </div>
          <div className="h-9 w-px bg-slate-700 shrink-0" />
          <div className="shrink-0">
            <div className="text-[9px] text-slate-400 font-medium uppercase tracking-wider">
              Cronometro
            </div>
            <div className="text-base font-bold text-slate-100 tabular-nums">
              {formatElapsedHMS(executionElapsedMs)}
            </div>
          </div>
        </div>
      ),
    },
    {
      id: "period-simulated-panel",
      buttonSide: "left",
      icon: <Clock className="w-4 h-4" />,
      content: (
        <div className="bg-surface-2/85 backdrop-blur border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-4 py-3 flex items-center gap-4">
          <div className="shrink-0">
            <div className="text-[9px] text-slate-400 font-medium uppercase tracking-wider">
              Fecha simulada
            </div>
            <div className="text-base font-bold text-info tabular-nums">
              {simulationClock.date}
            </div>
          </div>
          <div className="h-9 w-px bg-slate-700 shrink-0" />
          <div className="shrink-0">
            <div className="text-[9px] text-slate-400 font-medium uppercase tracking-wider">
              Hora simulada
            </div>
            <div className="text-base font-bold text-info tabular-nums whitespace-nowrap">
              {simulationClock.time} UTC
            </div>
          </div>
          <div className="h-9 w-px bg-slate-700 shrink-0" />
          <div className="shrink-0">
            <div className="text-[9px] text-slate-400 font-medium uppercase tracking-wider">
              Tiempo transcurrido
            </div>
            <div className="text-base font-bold text-info tabular-nums">
              {simulatedElapsedLabel}
            </div>
          </div>
          <div className="h-9 w-px bg-slate-700 shrink-0" />
          <div className="shrink-0">
            <div className="text-[9px] text-slate-400 font-medium uppercase tracking-wider">
              Dia de simulacion
            </div>
            <div className="text-base font-bold text-info tabular-nums">
              {displayedDay
                ? `Dia ${displayedDay}/${PERIOD_DAYS}`
                : ""}
            </div>
          </div>
        </div>
      ),
    },
    {
      id: "period-actions-panel",
      icon: <SlidersHorizontal className="w-4 h-4" />,
      content: (
        <div className="bg-surface-2/85 backdrop-blur border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-4 py-3 flex items-center gap-4">
          <button
            type="button"
            onClick={() => setShowRouteLines((v) => !v)}
            className={`shrink-0 rounded-lg px-2 py-1 text-xs font-medium whitespace-nowrap transition-colors ${
              showRouteLines
                ? "bg-blue-600/20 text-blue-400 border border-blue-500/40 hover:bg-blue-600/30"
                : "bg-surface-2 text-slate-400 border border-slate-700 hover:text-slate-200"
            }`}
          >
            Mostrar lineas
          </button>
          <div className="h-9 w-px bg-slate-700 shrink-0" />
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
  ] : [];

  const mapOverlay = hasActiveRun ? null : (
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

  const finalSummaryModal = showFinalSummaryModal ? (
    <div className="fixed inset-0 z-[10001] overflow-y-auto bg-black/60 backdrop-blur-sm">
      <div className="min-h-full flex items-start justify-center px-4 py-6 sm:py-10">
        <div className="bg-surface-2 border border-success/40 shadow-[0_12px_35px_rgba(0,0,0,0.55)] rounded-2xl max-w-2xl w-full max-h-[calc(100vh-3rem)] overflow-y-auto">
        <div className="px-8 py-6 border-b border-success/20 bg-success/10 sticky top-0 z-10">
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-start gap-4">
              <div className="rounded-xl bg-success/15 border border-success/30 p-3">
                <CheckCircle2 className="w-7 h-7 text-success flex-shrink-0" />
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-success/80 mb-2">
                  Resumen final
                </p>
                <h2 className="text-2xl font-bold text-slate-50">
                  Simulacion completada
                </h2>
                <p className="text-sm text-slate-300 mt-2 max-w-xl leading-relaxed">
                  {finalSummaryInfo?.mensaje ?? `La simulacion por periodo concluyo correctamente tras procesar los ${PERIOD_DAYS} dias planificados.`}
                </p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => setShowFinalSummaryModal(false)}
              className="text-slate-400 hover:text-slate-200 transition-colors flex-shrink-0 mt-1"
              title="Cerrar"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        <div className="px-8 py-6">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-6">
            <div className="rounded-xl border border-slate-700 bg-slate-900/50 px-4 py-3">
              <div className="text-[11px] uppercase tracking-wider text-slate-400 mb-1">
                Estado final
              </div>
              <div className="text-base font-semibold text-success">
                Ejecucion completada
              </div>
            </div>
            <div className="rounded-xl border border-slate-700 bg-slate-900/50 px-4 py-3">
              <div className="text-[11px] uppercase tracking-wider text-slate-400 mb-1">
                Tiempo ejecutado
              </div>
              <div className="text-base font-semibold text-slate-100">
                {formatElapsedHMS(executionElapsedMs)}
              </div>
            </div>
            <div className="rounded-xl border border-slate-700 bg-slate-900/50 px-4 py-3">
              <div className="text-[11px] uppercase tracking-wider text-slate-400 mb-1">
                Cierre de simulacion
              </div>
              <div className="text-base font-semibold text-slate-100">
                {simulationClock.date}
              </div>
              <div className="text-sm text-info tabular-nums">
                {simulationClock.time} UTC
              </div>
            </div>
          </div>

          {collapseStablePlanningSummary ? (
            <div className="mb-6">
              <h3 className="text-sm font-semibold text-slate-200 mb-2">
                Ultima planificacion estable
              </h3>
              <p className="text-sm text-slate-400 mb-3">
                Se muestra como referencia el ultimo cierre operativo consistente antes de finalizar la simulacion.
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {collapseStablePlanningSummary.map(({ label, value }) => (
                  <div
                    key={label}
                    className="rounded-xl border border-slate-700 bg-slate-900/40 px-4 py-3"
                  >
                    <div className="text-sm text-slate-400 mb-1">{label}</div>
                    <div className="text-base font-semibold text-slate-100 tabular-nums">
                      {value}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : null}

          <div className="mb-6">
            <h3 className="text-sm font-semibold text-slate-200 mb-3">
              Estadisticas generales del periodo
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {finalSummaryMetrics.map(({ key, label, value }) => (
                <div
                  key={key}
                  className="rounded-xl border border-slate-700 bg-slate-900/40 px-4 py-3"
                >
                  <div className="text-sm text-slate-400 mb-1">{label}</div>
                  <div className="text-xl font-bold text-slate-50 tabular-nums">
                    {value}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="rounded-xl border border-info/20 bg-info/10 px-4 py-3 text-sm text-slate-300 leading-relaxed">
            La corrida termino sin colapso. Puedes cerrar este resumen para revisar el tablero final o iniciar una nueva simulacion.
          </div>
        </div>
      </div>
      </div>
    </div>
  ) : null;

  const resumenColapsoModal = showColapsoModal ? (
    <div className="fixed inset-0 z-[10001] overflow-y-auto bg-black/60 backdrop-blur-sm">
      <div className="min-h-full flex items-start justify-center px-4 py-6 sm:py-10">
        <div className="bg-surface-2 border border-danger/40 shadow-[0_12px_35px_rgba(0,0,0,0.55)] rounded-2xl max-w-2xl w-full max-h-[calc(100vh-3rem)] overflow-y-auto">
        <div className="px-8 py-6 border-b border-danger/20 bg-danger/10 sticky top-0 z-10">
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-start gap-4">
              <div className="rounded-xl bg-danger/15 border border-danger/30 p-3">
                <AlertTriangle className="w-7 h-7 text-danger flex-shrink-0" />
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-danger/80 mb-2">
                  Resumen final
                </p>
                <h2 className="text-2xl font-bold text-slate-50">
                  {collapseSummary.title}
                </h2>
                <p className="text-sm text-slate-300 mt-2 max-w-xl leading-relaxed">
                  {collapseSummary.description}
                </p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => setShowColapsoModal(false)}
              className="text-slate-400 hover:text-slate-200 transition-colors flex-shrink-0 mt-1"
              title="Cerrar"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        <div className="px-8 py-6">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-6">
            <div className="rounded-xl border border-slate-700 bg-slate-900/50 px-4 py-3">
              <div className="text-[11px] uppercase tracking-wider text-slate-400 mb-1">
                Estado final
              </div>
              <div className="text-base font-semibold text-danger">
                Colapso operativo
              </div>
            </div>
            <div className="rounded-xl border border-slate-700 bg-slate-900/50 px-4 py-3">
              <div className="text-[11px] uppercase tracking-wider text-slate-400 mb-1">
                Tiempo ejecutado
              </div>
              <div className="text-base font-semibold text-slate-100">
                {formatElapsedHMS(executionElapsedMs)}
              </div>
            </div>
            <div className="rounded-xl border border-slate-700 bg-slate-900/50 px-4 py-3">
              <div className="text-[11px] uppercase tracking-wider text-slate-400 mb-1">
                Momento del colapso
              </div>
              <div className="text-base font-semibold text-slate-100">
                {simulationClock.date}
              </div>
              <div className="text-sm text-info tabular-nums">
                {simulationClock.time} UTC
              </div>
            </div>
          </div>

          {collapseStablePlanningSummary ? (
            <div className="mb-6">
              <h3 className="text-sm font-semibold text-slate-200 mb-2">
                Ultima planificacion estable
              </h3>
              <p className="text-sm text-slate-400 mb-3">
                Corresponde a la ultima planificacion que se completo correctamente antes de la que desencadeno el colapso.
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {collapseStablePlanningSummary.map(({ label, value }) => (
                  <div
                    key={label}
                    className="rounded-xl border border-slate-700 bg-slate-900/40 px-4 py-3"
                  >
                    <div className="text-sm text-slate-400 mb-1">{label}</div>
                    <div className="text-base font-semibold text-slate-100 tabular-nums">
                      {value}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : null}

          <div className="mb-6">
            <h3 className="text-sm font-semibold text-slate-200 mb-3">
              Estadisticas generales al cierre
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {collapseStats.map(({ key, label, value }) => (
                <div
                  key={key}
                  className="rounded-xl border border-slate-700 bg-slate-900/40 px-4 py-3"
                >
                  <div className="text-sm text-slate-400 mb-1">{label}</div>
                  <div className="text-xl font-bold text-slate-50 tabular-nums">
                    {value}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {collapseEntityDetails.length > 0 ? (
            <div className="mb-6">
              <h3 className="text-sm font-semibold text-slate-200 mb-3">
                Entidades que colapsaron
              </h3>
              <div className="grid grid-cols-1 gap-3">
                {collapseEntityDetails.map(({ key, label, value }) => (
                  <div
                    key={key}
                    className="rounded-xl border border-slate-700 bg-slate-900/40 px-4 py-3"
                  >
                    <div className="text-sm text-slate-400 mb-1">{label}</div>
                    <div className="text-base font-semibold text-slate-100 break-words">
                      {value}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : null}

          <div className="rounded-xl border border-warning/20 bg-warning/10 px-4 py-3 text-sm text-slate-300 leading-relaxed">
            La simulacion se detuvo al alcanzar una condicion critica. Puedes cerrar este resumen o usar el boton{" "}
            <Square className="inline w-3 h-3 text-danger" /> para finalizar la ejecucion y comenzar una nueva corrida.
          </div>
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
      {finalSummaryModal}
      {resumenColapsoModal || colapsoModal}
      <MapDashboard
      title=""
      header={header}
      mapOverlays={mapOverlays}
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
      draggable={hasActiveRun}
    />
    </>
  );
}
