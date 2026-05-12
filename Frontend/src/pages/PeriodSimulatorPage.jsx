import { useEffect, useMemo, useState } from "react";
import { Play, Pause, Square, RotateCw } from "lucide-react";
import MapDashboard from "../components/simulator/MapDashboard";
import { usePolling } from "../hooks/usePolling";
import { useStompPublish, useStompSubscribe } from "../hooks/useStomp";
import { useToast } from "../components/ui/Toast";
import { getStatus } from "../api/status";
import { iniciarSimulacionPeriodo, stopPeriodSim, getPeriodSimState } from "../api/simulator";
import { USE_MOCK } from "../api/client";

const PERIOD_DAYS = 5;
const INTERVAL_TICK_MS = 1000;

const ESTADO_BACK_A_LOCAL = {
  INICIADA: "running",
  REANUDADA: "running",
  PAUSADA: "paused",
  DETENIDA: "idle",
  FINALIZADA: "done",
};

export default function PeriodSimulatorPage() {
  const toast = useToast();
  const publish = useStompPublish();

  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [sessionId, setSessionId] = useState(null);
  const [simStatus, setSimStatus] = useState("idle"); // idle | running | paused | done

  /* ============ MODO REAL: STOMP ============ */
  const tickTopic   = !USE_MOCK && sessionId ? `/topic/simulacion/${sessionId}` : null;
  const statusTopic = !USE_MOCK && sessionId ? `/topic/simulacion/${sessionId}/estado` : null;

  const { data: tick }         = useStompSubscribe(tickTopic);
  const { data: estadoMessage } = useStompSubscribe(statusTopic);

  /* ============ MODO MOCK: polling ============ */
  const { data: mockState } = usePolling(getPeriodSimState, {
    enabled: USE_MOCK && simStatus === "running",
    intervalMs: 1000,
  });

  /* KPIs globales del topbar (siempre con polling — vienen de /status que no es del WS) */
  const { data: status } = usePolling(getStatus);

  /* Sincronizo simStatus segun el origen activo */
  useEffect(() => {
    if (USE_MOCK) {
      if (!mockState || mockState.status === simStatus) return;
      setSimStatus(mockState.status);
      if (mockState.status === "done") {
        toast.push({ type: "success", title: "Simulación completada", message: `Periodo de ${PERIOD_DAYS} días procesado.` });
      }
      return;
    }
    if (!estadoMessage) return;
    const local = ESTADO_BACK_A_LOCAL[estadoMessage.estado] ?? simStatus;
    if (local !== simStatus) setSimStatus(local);
    if (estadoMessage.estado === "FINALIZADA") {
      toast.push({ type: "success", title: "Simulación completada", message: estadoMessage.mensaje });
    }
    if (estadoMessage.estado === "DETENIDA") {
      setSessionId(null);
    }
  }, [mockState, estadoMessage]); // eslint-disable-line react-hooks/exhaustive-deps

  /* Progreso */
  const progress = useMemo(() => {
    if (USE_MOCK) return mockState?.progress ?? 0;
    if (!tick) return 0;
    const dia = tick.diaActual ?? 0;
    return Math.min(100, (dia / PERIOD_DAYS) * 100);
  }, [tick, mockState]);

  /* Metricas en vivo (sobreescriben las de /status cuando el tick las trae) */
  const liveMetrics = useMemo(() => {
    if (USE_MOCK || !tick) return status ?? undefined;
    return {
      ...(status ?? {}),
      bagsInTransit:  tick.maletasEnTransito,
      activeFlights:  status?.activeFlights ?? 0,
      freeCapacityPct: status?.freeCapacityPct ?? 0,
    };
  }, [tick, status]);

  /* ============ Handlers ============ */
  const handleStart = async () => {
    try {
      const result = await iniciarSimulacionPeriodo({
        fechaInicio: startDate,
        totalDias: PERIOD_DAYS,
        intervaloTickMs: INTERVAL_TICK_MS,
      });
      const newSessionId = result.sessionId ?? null;
      setSessionId(newSessionId);
      setSimStatus(USE_MOCK ? (result.status ?? "running") : (ESTADO_BACK_A_LOCAL[result.estado] ?? "running"));
      toast.push({ type: "info", title: "Simulación iniciada", message: `Inicio: ${startDate} · ${PERIOD_DAYS} días` });
    } catch (err) {
      toast.push({ type: "error", title: "No se pudo iniciar", message: err.message });
    }
  };

  const handlePause = async () => {
    if (USE_MOCK) {
      toast.push({ type: "warning", title: "Pausa no soportada en modo mock" });
      return;
    }
    try {
      await publish("/app/simulacion/periodo/pausar", { sessionId });
    } catch (err) {
      toast.push({ type: "error", title: "No se pudo pausar", message: err.message });
    }
  };

  const handleResume = async () => {
    if (USE_MOCK) return;
    try {
      await publish("/app/simulacion/periodo/reanudar", { sessionId });
    } catch (err) {
      toast.push({ type: "error", title: "No se pudo reanudar", message: err.message });
    }
  };

  const handleStop = async () => {
    try {
      if (USE_MOCK) {
        await stopPeriodSim();
        setSimStatus("idle");
        setSessionId(null);
      } else {
        await publish("/app/simulacion/periodo/detener", { sessionId });
      }
      toast.push({ type: "warning", title: "Simulación detenida" });
    } catch (err) {
      toast.push({ type: "error", title: "No se pudo detener", message: err.message });
    }
  };

  /* ============ UI ============ */
  const header = (
    <div className="flex items-center gap-3 flex-wrap">
      <div className="flex flex-col">
        <label htmlFor="period-start" className="text-[10px] text-slate-400 uppercase tracking-wider font-medium">
          Fecha de inicio
        </label>
        <input
          id="period-start"
          type="date"
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
          disabled={simStatus === "running" || simStatus === "paused"}
          className="bg-surface-2 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-slate-200 outline-none focus:border-blue-500 disabled:opacity-50"
        />
      </div>

      <div className="flex flex-col">
        <span className="text-[10px] text-slate-400 uppercase tracking-wider font-medium">Duración</span>
        <span className="px-3 py-1.5 bg-surface-2 border border-slate-700 rounded-lg text-sm font-bold text-success">
          {PERIOD_DAYS} días
        </span>
      </div>

      {tick?.fechaSimulada && (
        <div className="flex flex-col">
          <span className="text-[10px] text-slate-400 uppercase tracking-wider font-medium">Día simulado</span>
          <span className="px-3 py-1.5 bg-surface-2 border border-slate-700 rounded-lg text-sm font-bold text-info tabular-nums">
            {tick.fechaSimulada} · día {tick.diaActual}/{PERIOD_DAYS}
          </span>
        </div>
      )}

      {simStatus === "idle" || simStatus === "done" ? (
        <button type="button" onClick={handleStart} className="self-end bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors">
          <Play className="w-4 h-4" /> Ejecutar
        </button>
      ) : simStatus === "paused" ? (
        <>
          <button type="button" onClick={handleResume} className="self-end bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors">
            <RotateCw className="w-4 h-4" /> Reanudar
          </button>
          <button type="button" onClick={handleStop} className="self-end bg-danger/10 hover:bg-danger/20 text-danger border border-danger/40 px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors">
            <Square className="w-4 h-4" /> Detener
          </button>
        </>
      ) : (
        <>
          {!USE_MOCK && (
            <button type="button" onClick={handlePause} className="self-end bg-warning/10 hover:bg-warning/20 text-warning border border-warning/40 px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors">
              <Pause className="w-4 h-4" /> Pausar
            </button>
          )}
          <button type="button" onClick={handleStop} className="self-end bg-danger/10 hover:bg-danger/20 text-danger border border-danger/40 px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors">
            <Square className="w-4 h-4" /> Detener
          </button>
        </>
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
      date={status?.date}
      time={status?.time}
      metrics={liveMetrics}
    />
  );
}
