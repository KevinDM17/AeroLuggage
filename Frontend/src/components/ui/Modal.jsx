import { useEffect, useRef } from "react";
import { X } from "lucide-react";

/**
 * Modal accesible: cierra con Escape, click en backdrop, y botón ✕.
 * No implementa focus-trap completo (suficiente para el alcance actual).
 */
export default function Modal({ open, onClose, title, children, maxWidth = "max-w-3xl" }) {
  const dialogRef = useRef(null);

  useEffect(() => {
    if (!open) return;
    const handleKey = (e) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKey);
    // Mover foco al diálogo para que screen readers anuncien
    dialogRef.current?.focus();
    return () => document.removeEventListener("keydown", handleKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      className="fixed inset-0 z-[10000] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={dialogRef}
        tabIndex={-1}
        className={`bg-surface-1 border border-slate-800 rounded-xl w-full ${maxWidth} overflow-hidden shadow-2xl flex flex-col max-h-full relative outline-none`}
      >
        <button
          type="button"
          onClick={onClose}
          aria-label="Cerrar"
          className="absolute top-4 right-4 z-10 p-2 rounded-lg text-slate-400 hover:text-white hover:bg-surface-2 transition-colors"
        >
          <X className="w-5 h-5" />
        </button>
        {children}
      </div>
    </div>
  );
}
