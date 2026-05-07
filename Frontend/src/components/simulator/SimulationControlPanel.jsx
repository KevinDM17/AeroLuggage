import { Play, Square, Pause, RotateCcw, Clock, Layers } from "lucide-react";
import { cn } from "../../utils/cn";

export default function SimulationControlPanel({ 
  status, 
  onStart, 
  onPause, 
  onStop, 
  onReset,
  speed,
  setSpeed
}) {
  return (
    <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-semibold text-slate-800 flex items-center gap-2">
          <Layers className="w-4 h-4 text-blue-500" />
          Controles de Simulación
        </h3>
        
        <div className="flex items-center gap-2">
          <span className="text-xs text-slate-500 font-medium">ESTADO:</span>
          <span className={cn(
            "px-2.5 py-1 text-xs font-semibold rounded-full",
            status === 'running' && "bg-blue-100 text-blue-700",
            status === 'paused' && "bg-amber-100 text-amber-700",
            status === 'stopped' && "bg-slate-100 text-slate-700",
            status === 'finished' && "bg-green-100 text-green-700",
          )}>
            {status.toUpperCase()}
          </span>
        </div>
      </div>

      <div className="flex gap-3 mb-5">
        <button 
          onClick={onStart}
          disabled={status === 'running' || status === 'finished'}
          className={cn(
            "flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl font-medium transition-all text-sm",
            status === 'running' || status === 'finished'
              ? "bg-slate-100 text-slate-400 cursor-not-allowed" 
              : "bg-blue-600 text-white hover:bg-blue-700 shadow-sm"
          )}
        >
          <Play className="w-4 h-4" /> Iniciar
        </button>
        <button 
          onClick={onPause}
          disabled={status !== 'running'}
          className={cn(
            "flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl font-medium transition-all border text-sm",
            status !== 'running'
              ? "border-slate-200 bg-slate-50 text-slate-400 cursor-not-allowed" 
              : "border-amber-200 bg-amber-50 text-amber-700 hover:bg-amber-100"
          )}
        >
          <Pause className="w-4 h-4" /> Pausar
        </button>
        <button 
          onClick={onStop}
          disabled={status === 'stopped'}
          className={cn(
            "flex items-center justify-center p-2.5 rounded-xl transition-all border",
            status === 'stopped'
              ? "border-slate-200 bg-slate-50 text-slate-400 cursor-not-allowed"
              : "border-red-200 bg-red-50 text-red-600 hover:bg-red-100"
          )}
          title="Detener"
        >
          <Square className="w-4 h-4" />
        </button>
        <button 
          onClick={onReset}
          className="flex items-center justify-center p-2.5 rounded-xl transition-all border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 hover:text-slate-900"
          title="Reiniciar"
        >
          <RotateCcw className="w-4 h-4" />
        </button>
      </div>

      <div>
        <div className="flex items-center justify-between mb-2">
          <label className="text-sm font-medium text-slate-700 flex items-center gap-1.5">
            <Clock className="w-4 h-4 text-slate-400" /> 
            Velocidad (x{speed})
          </label>
        </div>
        <input 
          type="range" 
          min="1" 
          max="10" 
          step="1"
          value={speed}
          onChange={(e) => setSpeed(parseInt(e.target.value))}
          className="w-full accent-blue-600"
          disabled={status === 'running'}
        />
        <div className="flex justify-between text-xs text-slate-400 mt-1">
          <span>Tiempo real</span>
          <span>Acelerado</span>
        </div>
      </div>
    </div>
  );
}
