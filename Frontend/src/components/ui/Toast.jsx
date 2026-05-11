import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { CheckCircle2, AlertTriangle, AlertCircle, X } from "lucide-react";

const ToastContext = createContext(null);

const ICONS = { success: CheckCircle2, error: AlertCircle, warning: AlertTriangle, info: AlertCircle };
const TONE = {
  success: "border-success/40 text-success",
  error:   "border-danger/40 text-danger",
  warning: "border-warning/40 text-warning",
  info:    "border-info/40 text-info",
};

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const push = useCallback((toast) => {
    const id = Date.now() + Math.random();
    const item = { id, type: "info", duration: 4000, ...toast };
    setToasts((prev) => [...prev, item]);
    if (item.duration > 0) {
      setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), item.duration);
    }
  }, []);

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ push, dismiss }}>
      {children}
      <div className="fixed bottom-4 right-4 z-[10020] flex flex-col gap-2 max-w-[calc(100%-2rem)]" aria-live="polite" aria-atomic="true">
        {toasts.map((t) => {
          const Icon = ICONS[t.type] ?? ICONS.info;
          return (
            <div
              key={t.id}
              role="status"
              className={`bg-surface-1 border ${TONE[t.type] ?? TONE.info} rounded-xl px-4 py-3 shadow-2xl flex items-start gap-3 min-w-[260px] max-w-md backdrop-blur`}
            >
              <Icon className="w-5 h-5 shrink-0 mt-0.5" />
              <div className="flex-1 text-sm text-slate-200">
                {t.title && <div className="font-bold mb-0.5">{t.title}</div>}
                <div className="text-slate-300 text-sm leading-snug">{t.message}</div>
              </div>
              <button
                type="button"
                aria-label="Cerrar notificación"
                onClick={() => dismiss(t.id)}
                className="shrink-0 text-slate-500 hover:text-white p-1 -m-1 rounded transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast debe usarse dentro de <ToastProvider>");
  return ctx;
}
