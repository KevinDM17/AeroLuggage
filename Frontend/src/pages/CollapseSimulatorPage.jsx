import { useEffect, useMemo, useState } from "react";
import { Play, Square, AlertTriangle } from "lucide-react";
import MapDashboard from "../components/simulator/MapDashboard";
import { usePolling } from "../hooks/usePolling";
import { useElapsedTimer } from "../hooks/useElapsedTimer";
import { useToast } from "../components/ui/Toast";
import { getStatus } from "../api/status";
import { startCollapseSim, stopCollapseSim, getCollapseSimState } from "../api/simulator";
import { formatDateTimeDisplay, formatElapsedHMS } from "../utils/formatting";

const WARNING_AT_MS  = 60_000;

export default function CollapseSimulatorPage() {
  const toast = useToast();
  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [simStatus, setSimStatus] = useState("idle");
  const [runId, setRunId] = useState(0);

  const { data: simState } = usePolling(getCollapseSimState, {
    enabled: simStatus === "running",
    intervalMs: 1000,
  });

  const { data: status } = usePolling(getStatus);
  const executionElapsedMs = useElapsedTimer(simStatus, runId);

  useEffect(() => {
    if (!simState || simState.status === simStatus) return;
    setSimStatus(simState.status);
    if (simState.status === "collapsed") {
      toast.push({ type: "error", title: "Colapso detectado", message: "Las operaciones colapsaron en la simulación." });
    }
  }, [simState, simStatus, toast]);

  const elapsed = simState?.elapsedMs ?? 0;
  const simulationClock = useMemo(() => {
    const baseDate = simState?.startDate ?? startDate;
    const start = new Date(`${baseDate}T00:00:00`);
    if (Number.isNaN(start.getTime())) return formatDateTimeDisplay(null);
    return formatDateTimeDisplay(new Date(start.getTime() + elapsed));
  }, [elapsed, simState?.startDate, startDate]);

  const handleStart = async () => {
    try {
      const result = await startCollapseSim(startDate);
      setRunId((current) => current + 1);
      setSimStatus(result.status);
      toast.push({ type: "info", title: "Simulación iniciada", message: `Inicio: ${startDate} · sin límite hasta colapso` });
    } catch (err) {
      toast.push({ type: "error", title: "No se pudo iniciar", message: err.message });
    }
  };

  const handleStop = async () => {
    try {
      const result = await stopCollapseSim();
      setSimStatus(result.status);
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

  const hasActiveRun = simStatus !== "idle";

  const mapOverlay = hasActiveRun ? (
    <div className="bg-surface-2/85 backdrop-blur border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-4 py-3 flex items-center justify-center gap-6">
      <div>
        <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">Cronometro</div>
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

      {false && <div className="flex flex-col">
        <span className="text-[10px] text-slate-400 uppercase tracking-wider font-medium">Cronometro</span>
        <span className="px-3 py-1.5 bg-surface-2 border border-slate-700 rounded-lg text-sm font-bold text-slate-200 tabular-nums">
          {formatElapsedHMS(executionElapsedMs)}
        </span>
      </div>}

      {false && <div className="flex flex-col">
        <span className="text-[10px] text-slate-400 uppercase tracking-wider font-medium">Fecha/hora simulada</span>
        <span className="px-3 py-1.5 bg-surface-2 border border-slate-700 rounded-lg text-sm font-bold text-info tabular-nums">
          {simulationClock.date} - {simulationClock.time}
        </span>
      </div>}

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
      date={status?.date}
      time={status?.time}
      metrics={status ?? undefined}
    />
  );
}
