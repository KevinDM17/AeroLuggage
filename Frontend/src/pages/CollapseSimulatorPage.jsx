import { useEffect, useRef, useState } from "react";
import { Play, Square, AlertTriangle } from "lucide-react";
import MapDashboard from "../components/simulator/MapDashboard";

/** Mock: umbrales hasta colapso. En backend real vienen del planificador. */
const COLLAPSE_AT_MS = 90_000;
const WARNING_AT_MS = 60_000;

const formatElapsed = (ms) => {
  const totalSec = Math.floor(ms / 1000);
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
};

export default function CollapseSimulatorPage() {
  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [status, setStatus] = useState("idle"); // idle | running | collapsed
  const [elapsed, setElapsed] = useState(0);
  const startedAtRef = useRef(null);
  const rafRef = useRef(null);

  useEffect(() => {
    if (status !== "running") return;
    startedAtRef.current = performance.now();

    const tick = (now) => {
      const e = now - startedAtRef.current;
      setElapsed(e);
      if (e >= COLLAPSE_AT_MS) {
        setStatus("collapsed");
        return;
      }
      rafRef.current = requestAnimationFrame(tick);
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafRef.current);
  }, [status]);

  const handleStart = () => {
    setElapsed(0);
    setStatus("running");
  };
  const handleStop = () => {
    cancelAnimationFrame(rafRef.current);
    setStatus("idle");
    setElapsed(0);
  };

  const operationalState =
    status === "collapsed"
      ? { label: "Colapso", color: "text-danger", bg: "bg-danger/15 border-danger/40" }
      : elapsed >= WARNING_AT_MS
      ? { label: "Próximo al colapso", color: "text-warning", bg: "bg-warning/15 border-warning/40" }
      : status === "running"
      ? { label: "Operativo", color: "text-success", bg: "bg-success/15 border-success/40" }
      : { label: "En espera", color: "text-slate-300", bg: "bg-surface-2 border-slate-700" };

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
          disabled={status === "running"}
          className="bg-surface-2 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-slate-200 outline-none focus:border-blue-500 disabled:opacity-50"
        />
      </div>

      <div className="flex flex-col">
        <span className="text-[10px] text-slate-400 uppercase tracking-wider font-medium">Tiempo simulado</span>
        <span className="px-3 py-1.5 bg-surface-2 border border-slate-700 rounded-lg text-sm font-bold text-slate-200 tabular-nums">
          {formatElapsed(elapsed)}
        </span>
      </div>

      <div className="flex flex-col">
        <span className="text-[10px] text-slate-400 uppercase tracking-wider font-medium">Estado</span>
        <span className={`px-3 py-1.5 border rounded-lg text-sm font-bold flex items-center gap-2 ${operationalState.bg} ${operationalState.color}`}>
          {status === "collapsed" && <AlertTriangle className="w-4 h-4" />}
          {operationalState.label}
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
    </div>
  );

  return <MapDashboard title="Simulación hasta Colapso" header={header} />;
}
