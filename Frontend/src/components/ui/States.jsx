import { Inbox, Loader2, AlertTriangle, RefreshCw } from "lucide-react";

export function LoadingState({ label = "Cargando..." }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-slate-400 gap-3">
      <Loader2 className="w-8 h-8 animate-spin" />
      <span className="text-sm">{label}</span>
    </div>
  );
}

export function EmptyState({ title = "Sin datos", message = "No hay registros para mostrar." }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-slate-400 gap-2">
      <Inbox className="w-10 h-10 opacity-70" />
      <div className="text-base font-semibold text-slate-200">{title}</div>
      <div className="text-sm text-center max-w-sm">{message}</div>
    </div>
  );
}

export function ErrorState({ error, onRetry }) {
  const message = error?.message || "No se pudo cargar la información.";
  return (
    <div className="flex flex-col items-center justify-center py-16 gap-3 text-slate-400">
      <AlertTriangle className="w-10 h-10 text-danger" />
      <div className="text-base font-semibold text-slate-200">Error al cargar</div>
      <div className="text-sm text-center max-w-md">{message}</div>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 inline-flex items-center gap-2 bg-surface-2 hover:bg-surface-3 border border-slate-700 text-slate-200 px-4 py-2 rounded-lg text-sm transition-colors"
        >
          <RefreshCw className="w-4 h-4" /> Reintentar
        </button>
      )}
    </div>
  );
}
