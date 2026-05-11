import { useEffect, useState } from "react";
import { Play, Square } from "lucide-react";
import MapDashboard from "../components/simulator/MapDashboard";
import { usePolling } from "../hooks/usePolling";
import { useToast } from "../components/ui/Toast";
import { getStatus } from "../api/status";
import { startPeriodSim, stopPeriodSim, getPeriodSimState } from "../api/simulator";

const PERIOD_DAYS = 5;

export default function PeriodSimulatorPage() {
  const toast = useToast();
  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [simStatus, setSimStatus] = useState("idle"); // refleja respuesta del back

  // Pollea el estado de la sim solo cuando esta corriendo
  const { data: simState } = usePolling(getPeriodSimState, {
    enabled: simStatus === "running",
    intervalMs: 1000,
  });

  // Status global para los KPIs (siempre)
  const { data: status } = usePolling(getStatus);

  // Sincroniza el estado local con el del back
  useEffect(() => {
    if (!simState || simState.status === simStatus) return;
    setSimStatus(simState.status);
    if (simState.status === "done") {
      toast.push({ type: "success", title: "Simulación completada", message: `Periodo de ${PERIOD_DAYS} días procesado.` });
    }
  }, [simState, simStatus, toast]);

  const progress = simState?.progress ?? 0;

  const handleStart = async () => {
    try {
      const result = await startPeriodSim(startDate);
      setSimStatus(result.status);
      toast.push({ type: "info", title: "Simulación iniciada", message: `Inicio: ${startDate} · ${PERIOD_DAYS} días` });
    } catch (err) {
      toast.push({ type: "error", title: "No se pudo iniciar", message: err.message });
    }
  };

  const handleStop = async () => {
    try {
      const result = await stopPeriodSim();
      setSimStatus(result.status);
      toast.push({ type: "warning", title: "Simulación detenida" });
    } catch (err) {
      toast.push({ type: "error", title: "No se pudo detener", message: err.message });
    }
  };

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
          disabled={simStatus === "running"}
          className="bg-surface-2 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-slate-200 outline-none focus:border-blue-500 disabled:opacity-50"
        />
      </div>

      <div className="flex flex-col">
        <span className="text-[10px] text-slate-400 uppercase tracking-wider font-medium">Duración</span>
        <span className="px-3 py-1.5 bg-surface-2 border border-slate-700 rounded-lg text-sm font-bold text-success">
          {PERIOD_DAYS} días
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

      {simStatus !== "idle" && (
        <div className="flex flex-col w-44 self-end">
          <div className="flex justify-between text-[10px] text-slate-400 mb-1">
            <span>Progreso</span>
            <span>{progress.toFixed(0)}%</span>
          </div>
          <div className="h-2 bg-surface-2 rounded-full overflow-hidden border border-slate-700">
            <div
              className={`h-full transition-all ${simStatus === "done" ? "bg-success" : "bg-info"}`}
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
      metrics={status ?? undefined}
    />
  );
}
