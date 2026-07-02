import { useState } from "react";
import { cn } from "../../utils/cn";
import { useLocation, useNavigate } from "react-router-dom";
import {
  PanelLeftClose,
  Home,
  RotateCw,
  Spline,
  Calendar,
  Clock,
  Plane,
  Building,
  Package,
} from "lucide-react";

export default function Sidebar({ onClose, closeOnNavigate = false }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [logoError, setLogoError] = useState(false);

  const handleNavigate = (to) => {
    if (location.pathname !== to) {
      navigate(to);
    }
    if (closeOnNavigate) onClose?.();
  };

  const navItemClass = (isActive, nested = false) =>
    cn(
      "flex w-full items-center gap-3 rounded-xl border px-3 py-2.5 text-left transition-all duration-200",
      nested ? "text-sm" : "",
      isActive
        ? "border-blue-500/35 bg-blue-500/12 text-blue-300 shadow-[0_10px_24px_rgba(37,99,235,0.14)]"
        : nested
          ? "border-slate-800 bg-surface-2/80 text-slate-300 hover:border-slate-700 hover:bg-slate-800"
          : "border-transparent text-slate-400 hover:border-slate-800 hover:bg-slate-800/80 hover:text-slate-200",
    );

  return (
    <div className="w-64 shrink-0 bg-surface-1 border-r border-slate-800 h-screen flex flex-col text-slate-300 relative z-[9999]">
      <div className="px-4 py-5 flex items-center justify-between gap-2 border-b border-transparent">
        <div className="flex items-center gap-3 min-w-0">
          {/* Logo (Frontend/public/logo.png). Si no existe, se usa el ícono SVG. */}
          {logoError ? (
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-surface-2 text-blue-500 ring-1 ring-slate-700/60">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="w-7 h-7"
              >
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
                <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
                <line x1="12" y1="22.08" x2="12" y2="12" />
              </svg>
            </div>
          ) : (
            <img
              src="/logo.png"
              alt="AeroLuggage"
              onError={() => setLogoError(true)}
              className="w-12 h-12 shrink-0 rounded-full object-cover bg-surface-2 ring-1 ring-slate-700/60"
            />
          )}
          <span className="text-lg font-bold text-white tracking-wide truncate">AeroLuggage</span>
        </div>
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar menu lateral"
            className="shrink-0 p-1.5 hover:bg-slate-800 rounded text-slate-400 hover:text-white transition-colors"
          >
            <PanelLeftClose className="w-5 h-5" />
          </button>
        )}
      </div>

      <nav className="app-scrollbar flex-1 py-3 px-3 space-y-1 overflow-y-auto">
        <button
          type="button"
          onClick={() => handleNavigate("/inicio")}
          className={navItemClass(location.pathname === "/inicio")}
        >
          <Home className="w-4 h-4" />
          <span className="font-medium text-sm">Inicio</span>
        </button>

        <button
          type="button"
          onClick={() => handleNavigate("/operaciones")}
          className={navItemClass(location.pathname === "/operaciones")}
        >
          <RotateCw className="w-4 h-4" />
          <span className="font-medium text-sm">Operaciones dia a dia</span>
        </button>

        <div className="pt-2">
          <div className="flex items-center gap-3 px-3 py-2 text-slate-400">
            <Spline className="w-4 h-4" />
            <span className="font-medium text-sm">Simulaciones</span>
          </div>
          <div className="flex flex-col gap-1 ml-[1.65rem] mt-1 pr-3 border-l border-slate-800 pl-4">
            <button
              type="button"
              onClick={() => handleNavigate("/simulator/period")}
              className={navItemClass(location.pathname === "/simulator/period", true)}
            >
              <Calendar className="w-4 h-4 text-blue-500" />
              Por periodo
            </button>
            <button
              type="button"
              onClick={() => handleNavigate("/simulator/collapse")}
              className={navItemClass(location.pathname === "/simulator/collapse", true)}
            >
              <Clock className="w-4 h-4 text-blue-500" />
              Hasta colapso
            </button>
          </div>
        </div>

        <div className="space-y-1 pt-2">
          <button
            type="button"
            onClick={() => handleNavigate("/airports")}
            className={navItemClass(location.pathname === "/airports")}
          >
            <Building className="w-4 h-4" />
            <span className="font-medium">Aeropuertos</span>
          </button>

          <button
            type="button"
            onClick={() => handleNavigate("/gestion-aeropuerto")}
            className={navItemClass(location.pathname.startsWith("/gestion-aeropuerto"))}
          >
            <Building className="w-4 h-4" />
            <span className="font-medium">Gestión de Aeropuerto</span>
          </button>

          <button
            type="button"
            onClick={() => handleNavigate("/flights/plans")}
            className={cn(
              "flex w-full items-center gap-3 rounded-xl border px-3 py-2.5 text-left transition-all duration-200",
              location.pathname === "/flights/plans"
                ? "border-blue-500/35 bg-blue-500/12 text-blue-300 shadow-[0_10px_24px_rgba(37,99,235,0.14)]"
                : "border-transparent text-slate-400 hover:border-slate-800 hover:bg-slate-800/80 hover:text-slate-200",
            )}
          >
            <Plane className="w-4 h-4" />
            <span className="font-medium text-sm">Planes de vuelo</span>
          </button>

          <button
            type="button"
            onClick={() => handleNavigate("/orders")}
            className={navItemClass(location.pathname === "/orders")}
          >
            <Package className="w-4 h-4" />
            <span className="font-medium">Pedidos</span>
          </button>
        </div>
      </nav>

      <div className="p-4 border-t border-slate-800">
        <div className="flex items-center gap-3 px-2 py-2">
          <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300 font-bold text-xs">
            OP
          </div>
          <div>
            <p className="text-sm font-bold text-slate-200">Operador 01</p>
            <p className="text-[10px] text-slate-400 uppercase tracking-wider">Centro de Control</p>
          </div>
        </div>
      </div>
    </div>
  );
}
