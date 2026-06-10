import { useEffect, useMemo, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { Play, Square, AlertTriangle } from "lucide-react";
import MapDashboard from "../components/simulator/MapDashboard";
import { usePolling } from "../hooks/usePolling";
import { useElapsedTimer } from "../hooks/useElapsedTimer";
import { useDefensivePerformanceCleanup } from "../hooks/useDefensivePerformanceCleanup";
import { useStompPublish, useStompSubscribe } from "../hooks/useStomp";
import { useToast } from "../components/ui/Toast";
import { iniciarSimulacionColapso, stopCollapseSim, getCollapseSimState } from "../api/simulator";
import { USE_MOCK } from "../api/client";
import { clearPerformanceTimeline } from "../utils/performanceCleanup";
import { formatDateTimeDisplay, formatElapsedHMS } from "../utils/formatting";

const WARNING_AT_MS = 60_000;
const TICK_MS = 500;

const ESTADO_BACK_A_LOCAL = {
  INICIADA: "running",
  REANUDADA: "running",
  DETENIDA: "idle",
  COLAPSO: "collapsed",
  FINALIZADA: "collapsed",
};

const emptyMetrics = {
  bagsInTransit: 0,
  bagsDelivered: 0,
  bagsUnassigned: 0,
  activeFlights: 0,
  freeCapacityPct: 0,
};

export default function CollapseSimulatorPage() {
  const toast = useToast();
  const publish = useStompPublish();
  const { resetSimulationPanelData } = useOutletContext();

  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [sessionId, setSessionId] = useState(null);
  const [simStatus, setSimStatus] = useState("idle");
  const [runId, setRunId] = useState(0);

  const clearSimulationData = () => {
    resetSimulationPanelData();
    clearPerformanceTimeline();
  };

  useDefensivePerformanceCleanup(simStatus === "running");

  useEffect(() => {
    clearSimulationData();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  /* ============ Modo real: STOMP ============ */
  const tickTopic   = !USE_MOCK && sessionId ? `/topic/simulacion/colapso/${sessionId}` : null;
  const statusTopic = !USE_MOCK && sessionId ? `/topic/simulacion/colapso/${sessionId}/estado` : null;

  const { data: tick }          = useStompSubscribe(tickTopic);
  const { data: estadoMessage } = useStompSubscribe(statusTopic);

  /* ============ Modo mock: polling local ============ */
  const { data: mockState } = usePolling(getCollapseSimState, {
    enabled: USE_MOCK && simStatus === "running",
    intervalMs: TICK_MS,
  });

  /* Sincroniza estado local con el origen activo */
  useEffect(() => {
    if (USE_MOCK) {
      if (!mockState || mockState.status === simStatus) return;
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
    if (estadoMessage.estado === "DETENIDA") {
      setSessionId(null);
      clearSimulationData();
    }
  }, [mockState, estadoMessage]); // eslint-disable-line react-hooks/exhaustive-deps

  /* Tiempo simulado: mock viene en elapsedMs; back lo deriva de tickActual * tickMs */
  const elapsed = useMemo(() => {
    if (USE_MOCK) return mockState?.elapsedMs ?? 0;
    if (!tick) return 0;
    if (typeof tick.elapsedMs === "number") return tick.elapsedMs;
    if (typeof tick.tickActual === "number") return tick.tickActual * TICK_MS;
    return 0;
  }, [tick, mockState]);

  const executionElapsedMs = useElapsedTimer(simStatus, runId);
  const hasActiveRun = simStatus !== "idle";

  const simulationClock = useMemo(() => {
    if (!USE_MOCK && tick?.fechaSimulada) {
      return formatDateTimeDisplay(tick.fechaSimulada);
    }
    const baseDate = mockState?.startDate ?? startDate;
    const start = new Date(`${baseDate}T00:00:00`);
    if (Number.isNaN(start.getTime())) return formatDateTimeDisplay(null);
    return formatDateTimeDisplay(new Date(start.getTime() + elapsed));
  }, [tick, elapsed, mockState?.startDate, startDate]);

  /* Metricas en vivo solo desde tick WS (cero polling de /status) */
  const liveMetrics = useMemo(() => {
    if (!hasActiveRun) return emptyMetrics;
    if (USE_MOCK || !tick) return undefined;
    return {
      bagsInTransit:   tick.maletasEnTransito ?? 0,
      bagsDelivered:   tick.maletasEntregadas ?? 0,
      bagsUnassigned:  tick.maletasRetrasadas ?? tick.maletasNoAsignadas ?? 0,
      activeFlights:   tick.vuelosActivos ?? 0,
      freeCapacityPct: Math.max(0, Math.min(100, 100 - (tick.porcentajeSaturacion ?? 0) * 100)),
    };
  }, [tick, hasActiveRun]);

  const handleStart = async () => {
    clearSimulationData();
    try {
      const result = await iniciarSimulacionColapso({
        fechaInicio: startDate,
        intervaloTickMs: TICK_MS,
      });
      setSessionId(result?.sessionId ?? null);
      setRunId((c) => c + 1);
      setSimStatus(USE_MOCK ? (result.status ?? "running") : (ESTADO_BACK_A_LOCAL[result.estado] ?? "running"));
      toast.push({ type: "info", title: "Simulación iniciada", message: `Inicio: ${startDate} · sin límite hasta colapso` });
    } catch (err) {
      toast.push({ type: "error", title: "No se pudo iniciar", message: err.message });
    }
  };

  const handleStop = async () => {
    setSimStatus("idle");
    setSessionId(null);
    clearSimulationData();
    try {
      if (USE_MOCK) {
        await stopCollapseSim();
      } else {
        await publish("/app/simulacion/colapso/detener", { sessionId });
      }
      toast.push({ type: "warning", title: "Simulación detenida" });
    } catch (err) {
      toast.push({ type: "error", title: "No se pudo detener", message: err.message });
    }
  };

  const operationalState =
    simStatus === "collapsed"
      ? { label: "Colapso", color: "text-danger", bg: "bg-danger/15 border-danger/40" }
      : elapsed >= WARNING_AT_MS
      ? { label: "Próximo al colapso", color: "text-warning", bg: "bg-warning/15 border-warning/40" }
      : simStatus === "running"
      ? { label: "Operativo", color: "text-success", bg: "bg-success/15 border-success/40" }
      : { label: "En espera", color: "text-slate-300", bg: "bg-surface-2 border-slate-700" };

  const mapOverlay = hasActiveRun ? (
    <div className="bg-surface-2/85 backdrop-blur border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-4 py-3 flex items-center justify-center gap-6">
      <div>
        <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">Cronómetro</div>
        <div className="text-lg font-bold text-slate-100 tabular-nums">{formatElapsedHMS(executionElapsedMs)}</div>
      </div>
      <div className="h-9 w-px bg-slate-700" />
      <div>
        <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">Fecha/hora simulada</div>
        <div className="text-lg font-bold text-info tabular-nums">
          {simulationClock.date} - {simulationClock.time} {simulationClock.timeZone}
        </div>
      </div>
    </div>
  ) : null;

  const header = (
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
      ) : (
        <button
          type="button"
          onClick={handleStop}
          className="self-end bg-danger/10 hover:bg-danger/20 text-danger border border-danger/40 px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors"
        >
          <Square className="w-4 h-4" /> Detener
        </button>
      )}
    </div>
  );

  return (
    <MapDashboard
      title="Simulación hasta Colapso"
      header={header}
      mapOverlay={mapOverlay}
      showMapClock={false}
      showMapFlights={hasActiveRun}
      date={simulationClock?.date}
      time={simulationClock?.time}
      metrics={liveMetrics}
    />
  );
}
