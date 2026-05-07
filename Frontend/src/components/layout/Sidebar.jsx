import { cn } from "../../utils/cn";
import { Link, useLocation } from "react-router-dom";
import { Activity, Menu, RotateCw, Spline, Calendar, Clock, Plane, Luggage, Building, Package } from "lucide-react";

export default function Sidebar({ onClose }) {
  const location = useLocation();

  return (
    <div className="w-64 shrink-0 bg-[#0B0E14] border-r border-slate-800 h-screen flex flex-col text-slate-300 relative z-[9999]">
      <div className="px-6 py-5 flex items-center justify-between border-b border-transparent">
        <div className="flex items-center gap-3">
          <div className="text-blue-500">
            {/* Hexagon approximation */}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-7 h-7">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
              <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
              <line x1="12" y1="22.08" x2="12" y2="12"></line>
            </svg>
          </div>
          <span className="text-lg font-bold text-white tracking-wide">AeroLuggage</span>
        </div>
      </div>

      <div className="px-4 pb-2 flex justify-end min-h-[36px]">
        {onClose && (
          <button onClick={onClose} className="p-1 hover:bg-slate-800 rounded">
            <Menu className="w-5 h-5 text-slate-400" />
          </button>
        )}
      </div>

      <nav className="flex-1 py-2 px-3 space-y-1 overflow-y-auto">
        <Link
          to="/"
          className={cn("flex items-center gap-3 px-3 py-2 rounded-lg transition-colors duration-200", location.pathname === '/' ? "bg-blue-600/20 text-blue-400" : "text-slate-400 hover:bg-slate-800 hover:text-slate-300")}
        >
          <RotateCw className="w-4 h-4" />
          <span className="font-medium text-sm">Operaciones día a día</span>
        </Link>

        <div className="pt-2">
          <div className="flex items-center gap-3 px-3 py-2 text-slate-400">
            <Spline className="w-4 h-4" />
            <span className="font-medium text-sm">Simulaciones</span>
          </div>
          <div className="flex flex-col gap-1 ml-[1.65rem] mt-1 pr-3 border-l border-slate-800 pl-4">
            <Link to="#" className="flex items-center gap-3 px-3 py-2 rounded-lg bg-[#151b2b] text-slate-300 transition-colors text-sm hover:bg-slate-800">
              <Calendar className="w-4 h-4 text-blue-500" />
              Por periodo
            </Link>
            <Link to="#" className="flex items-center gap-3 px-3 py-2 rounded-lg bg-[#151b2b] text-slate-300 transition-colors text-sm hover:bg-slate-800">
              <Clock className="w-4 h-4 text-blue-500" />
              Hasta colapso
            </Link>
          </div>
        </div>

        <div className="space-y-1 pt-2">
          <Link to="/airports" className={cn("flex items-center gap-3 px-3 py-2 rounded-lg transition-colors text-sm", location.pathname === '/airports' ? "bg-blue-600/20 text-blue-400" : "text-slate-400 hover:bg-slate-800 hover:text-slate-300")}>
            <Building className="w-4 h-4" />
            <span className="font-medium">Aeropuertos</span>
          </Link>
          <Link to="/flights" className={cn("flex items-center gap-3 px-3 py-2 rounded-lg transition-colors text-sm", location.pathname === '/flights' ? "bg-blue-600/20 text-blue-400" : "text-slate-400 hover:bg-slate-800 hover:text-slate-300")}>
            <Plane className="w-4 h-4" />
            <span className="font-medium">Vuelos</span>
          </Link>
          <Link to="/orders" className={cn("flex items-center gap-3 px-3 py-2 rounded-lg transition-colors text-sm", location.pathname === '/orders' ? "bg-blue-600/20 text-blue-400" : "text-slate-400 hover:bg-slate-800 hover:text-slate-300")}>
            <Package className="w-4 h-4" />
            <span className="font-medium">Pedidos</span>
          </Link>
        </div>
      </nav>

      <div className="p-4 border-t border-slate-800">
        <div className="flex items-center gap-3 px-2 py-2">
          <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300 font-bold text-xs">
            OP
          </div>
          <div>
            <p className="text-sm font-bold text-slate-200">Operador 01</p>
            <p className="text-[10px] text-slate-500 uppercase tracking-wider">Centro de Control</p>
          </div>
        </div>
      </div>
    </div>
  );
}
