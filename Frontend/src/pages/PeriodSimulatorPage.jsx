import { useEffect, useRef, useState } from "react";
import { Play, Square } from "lucide-react";
import MapDashboard from "../components/simulator/MapDashboard";

const PERIOD_DAYS = 5;
/** Mock: la simulación tarda 30-90 min en backend real. Aquí 60s para demo visual. */
const MOCK_DURATION_MS = 60_000;

export default function PeriodSimulatorPage() {
  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [status, setStatus] = useState("idle"); // idle | running | done
  const [progress, setProgress] = useState(0);
  const startedAtRef = useRef(null);
  const rafRef = useRef(null);

  useEffect(() => {
    if (status !== "running") return;
    startedAtRef.current = performance.now();

    const tick = (now) => {
      const elapsed = now - startedAtRef.current;
      const pct = Math.min(100, (elapsed / MOCK_DURATION_MS) * 100);
      setProgress(pct);
      if (pct >= 100) {
        setStatus("done");
        return;
      }
      rafRef.current = requestAnimationFrame(tick);
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafRef.current);
  }, [status]);

  const handleStart = () => {
    setProgress(0);
    setStatus("running");
  };
  const handleStop = () => {
    cancelAnimationFrame(rafRef.current);
    setStatus("idle");
    setProgress(0);
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
          disabled={status === "running"}
          className="bg-surface-2 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-slate-200 outline-none focus:border-blue-500 disabled:opacity-50"
        />
      </div>

      <div className="flex flex-col">
        <span className="text-[10px] text-slate-400 uppercase tracking-wider font-medium">Duración</span>
        <span className="px-3 py-1.5 bg-surface-2 border border-slate-700 rounded-lg text-sm font-bold text-success">
          {PERIOD_DAYS} días
        </span>
      </div>

      {status !== "running" ? (
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

      {status !== "idle" && (
        <div className="flex flex-col w-44 self-end">
          <div className="flex justify-between text-[10px] text-slate-400 mb-1">
            <span>Progreso</span>
            <span>{progress.toFixed(0)}%</span>
          </div>
          <div className="h-2 bg-surface-2 rounded-full overflow-hidden border border-slate-700">
            <div
              className={`h-full transition-all ${status === "done" ? "bg-success" : "bg-info"}`}
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
    />
  );
}
