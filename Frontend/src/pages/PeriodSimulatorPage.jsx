import { useEffect, useMemo, useRef, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { Play, Square, RotateCw } from "lucide-react";
import MapDashboard from "../components/simulator/MapDashboard";
import { usePolling } from "../hooks/usePolling";
import { useElapsedTimer } from "../hooks/useElapsedTimer";
import { useStompPublish, useStompSubscribe } from "../hooks/useStomp";
import { useToast } from "../components/ui/Toast";
import {
  iniciarSimulacionPeriodo,
  stopPeriodSim,
  getPeriodSimState,
} from "../api/simulator";
import { adaptAirport } from "../api/airports";
import { adaptFlightInstance } from "../api/flightInstances";
import { USE_MOCK } from "../api/client";
import {
  formatElapsedHMS,
  formatUtcDateTimeDisplay,
} from "../utils/formatting";

const PERIOD_DAYS = 5;
const CLOCK_REFRESH_MS = 33;
const MAP_REFRESH_MS = 500;
const SIMULATED_DAY_MS = 24 * 60 * 60 * 1000;

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
  const [mapFlights, setMapFlights] = useState([]);
  const [showRouteLines, setShowRouteLines] = useState(true);
  const [currentSimTimeUtc, setCurrentSimTimeUtc] = useState(null);
  const [simulatedDayDurationMs, setSimulatedDayDurationMs] = useState(null);
  const [tickBaseSimTimeUtc, setTickBaseSimTimeUtc] = useState(null);
  const [tickReceiptElapsedMs, setTickReceiptElapsedMs] = useState(0);
  const ignoreTicksRef = useRef(true);

  const clearSimulationData = () => {
    setMapAirports([]);
    setMapFlights([]);
    resetSimulationPanelData();
  };

  const applyCancelledFlights = (flights) =>
    flights.map((flight) =>
      cancelledFlightIds?.has(flight.id)
        ? { ...flight, status: "CANCELADO", used: 0 }
        : flight,
    );

  useEffect(() => {
    resetSimulationPanelData();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

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
    if (estadoMessage.estado === "DETENIDA") {
      ignoreTicksRef.current = true;
      setSessionId(null);
      setCurrentSimTimeUtc(null);
      setTickBaseSimTimeUtc(null);
      setTickReceiptElapsedMs(0);
      clearSimulationData();
    }
  }, [mockState, estadoMessage]); // eslint-disable-line react-hooks/exhaustive-deps

  const progress = useMemo(() => {
    if (USE_MOCK) return mockState?.progress ?? lastMockState?.progress ?? 0;
    return getSimulationProgress(
      executionElapsedMs,
      PERIOD_DAYS,
      simulatedDayDurationMs,
    );
  }, [executionElapsedMs, mockState, lastMockState, simulatedDayDurationMs]);

  useEffect(() => {
    if (ignoreTicksRef.current || simStatus === "idle" || isStarting) return;
    if (!tick?.currentSimTimeUtc) return;
    setCurrentSimTimeUtc(tick.currentSimTimeUtc);
    setTickBaseSimTimeUtc(tick.currentSimTimeUtc);
    setTickReceiptElapsedMs(executionElapsedMs);
    if (Array.isArray(tick.vuelosInstancia)) {
      const adaptedFlights = applyCancelledFlights(
        tick.vuelosInstancia.map(adaptFlightInstance),
      );
      const adaptedAirports = Array.isArray(tick.aeropuertos)
        ? tick.aeropuertos.map(adaptAirport)
        : null;
      setMapFlights(adaptedFlights);
      if (adaptedAirports) {
        setMapAirports(adaptedAirports);
      }
      setSimulationPanelData((prev) => ({
        ...prev,
        ...(adaptedAirports && { airports: adaptedAirports }),
        flights: adaptedFlights,
        orders: Array.isArray(tick.pedidos) ? tick.pedidos : prev.orders,
        bags: Array.isArray(tick.maletas) ? tick.maletas : prev.bags,
        routes: Array.isArray(tick.rutas) ? tick.rutas : prev.routes,
        loaded: true,
      }));
    } else {
      setSimulationPanelData((prev) => ({
        ...prev,
        orders: Array.isArray(tick.pedidos) ? tick.pedidos : prev.orders,
        bags: Array.isArray(tick.maletas) ? tick.maletas : prev.bags,
        routes: Array.isArray(tick.rutas) ? tick.rutas : prev.routes,
        loaded: true,
      }));
    }
  }, [tick, executionElapsedMs, simStatus, cancelledFlightIds]); // eslint-disable-line react-hooks/exhaustive-deps

  const simulatedNowMs = useMemo(() => {
    const base = tickBaseSimTimeUtc ?? currentSimTimeUtc;
    if (!base) return null;
    const parsed = Date.parse(`${base}Z`);
    if (!Number.isFinite(parsed)) return null;
    if (simStatus !== "running") return parsed;

    const elapsedSinceTickMs = Math.max(
      0,
      executionElapsedMs - tickReceiptElapsedMs,
    );
    const simulatedDeltaMs =
      elapsedSinceTickMs * (SIMULATED_DAY_MS / simulatedDayDurationMs);
    return parsed + simulatedDeltaMs;
  }, [
    tickBaseSimTimeUtc,
    currentSimTimeUtc,
    simStatus,
    executionElapsedMs,
    tickReceiptElapsedMs,
    simulatedDayDurationMs,
  ]);

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
    if (!tick) return undefined;
    return {
      bagsInTransit: tick.maletasEnTransito ?? 0,
      bagsDelivered:
        tick.maletasEntregadas ?? tick.maletasEntregadasATiempo ?? 0,
      bagsUnassigned: tick.maletasNoAsignadas ?? tick.maletasSinRuta ?? 0,
      activeFlights: tick.vuelosActivos ?? 0,
      freeCapacityPct: tick.capacidadLibrePct ?? 0,
    };
  }, [tick, mockState, hasActiveRun]);

  const handleStart = async () => {
    const startDateTime = `${startDate}T${startTime || "00:00"}:00`;
    ignoreTicksRef.current = true;
    setSimStatus("starting");
    setSessionId(null);
    setCurrentSimTimeUtc(null);
    setTickBaseSimTimeUtc(null);
    setTickReceiptElapsedMs(0);
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
      const adaptedAirports = Array.isArray(result.aeropuertos)
        ? result.aeropuertos.map(adaptAirport)
        : [];
      const adaptedFlights = Array.isArray(result.vuelosInstancia)
        ? result.vuelosInstancia.map(adaptFlightInstance)
        : [];
      setCancelledFlightIds(new Set());
      ignoreTicksRef.current = false;
      setSessionId(result.sessionId ?? null);
      setLastMockState(null);
      setCurrentSimTimeUtc(result.currentSimTimeUtc ?? startDateTime);
      setTickBaseSimTimeUtc(
        result.currentSimTimeUtc ?? startDateTime,
      );
      setTickReceiptElapsedMs(0);
      setSimulatedDayDurationMs(result.duracionDiaSimuladoMs);
      setMapAirports(adaptedAirports);
      setMapFlights(adaptedFlights);
      setSimulationPanelData({
        airports: adaptedAirports,
        flights: adaptedFlights,
        orders: [],
        bags: [],
        routes: [],
        loaded: true,
      });
      setRunId((current) => current + 1);
      setSimStatus(
        USE_MOCK
          ? (result.status ?? "running")
          : (ESTADO_BACK_A_LOCAL[result.estado] ?? "running"),
      );
      toast.push({
        type: "info",
        title: "Simulación iniciada",
        message: `Inicio: ${startDate} ${startTime || "00:00"} · ${PERIOD_DAYS} días`,
      });
    } catch (err) {
      ignoreTicksRef.current = true;
      setSimStatus("idle");
      setSessionId(null);
      setCurrentSimTimeUtc(null);
      setTickBaseSimTimeUtc(null);
      setTickReceiptElapsedMs(0);
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
    ignoreTicksRef.current = true;
    setSimStatus("idle");
    setSessionId(null);
    setCurrentSimTimeUtc(null);
    setTickBaseSimTimeUtc(null);
    setTickReceiptElapsedMs(0);
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
          {simulationClock.date} - {simulationClock.time}{" "}
          {simulationClock.timeZone}
          {tick?.currentWindowId
            ? ` - ${tick.currentWindowId}`
            : displayedDay
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
      flights={mapFlights}
      simulatedNowMs={simulatedNowMs}
      date={simulationClock.date}
      time={`${simulationClock.time} ${simulationClock.timeZone}`}
      metrics={liveMetrics}
    />
  );
}
