import { LayoutDashboard, Users, Activity, MoveUpRight, ArrowRight } from "lucide-react";
import { Link } from "react-router-dom";

export default function DashboardPage() {
  return (
    <div className="p-8 pb-20 max-w-[1600px] mx-auto">
      <h1 className="text-3xl font-bold tracking-tight text-slate-900 mb-2">Dashboard Principal</h1>
      <p className="text-slate-500 mb-8">Visión general del sistema de gestión de maletas.</p>
      
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200">
          <div className="flex justify-between items-start mb-4">
            <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
              <Activity className="w-6 h-6" />
            </div>
            <span className="flex items-center text-sm font-medium text-emerald-600 bg-emerald-50 px-2.5 py-0.5 rounded-full">
              <MoveUpRight className="w-3 h-3 mr-1" /> +12.5%
            </span>
          </div>
          <p className="text-slate-500 text-sm font-medium">Maletas Procesadas Hoy</p>
          <h3 className="text-2xl font-bold text-slate-900 mt-1">12,450</h3>
        </div>

        <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200">
          <div className="flex justify-between items-start mb-4">
            <div className="p-2 bg-indigo-50 text-indigo-600 rounded-lg">
              <Users className="w-6 h-6" />
            </div>
          </div>
          <p className="text-slate-500 text-sm font-medium">Equipos Activos</p>
          <h3 className="text-2xl font-bold text-slate-900 mt-1">34 Centros</h3>
        </div>

        <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200">
          <div className="flex justify-between items-start mb-4">
            <div className="p-2 bg-rose-50 text-rose-600 rounded-lg">
              <LayoutDashboard className="w-6 h-6" />
            </div>
            <span className="flex items-center text-sm font-medium text-rose-600 bg-rose-50 px-2.5 py-0.5 rounded-full">
              -4.2%
            </span>
          </div>
          <p className="text-slate-500 text-sm font-medium">Retrasos Promedio</p>
          <h3 className="text-2xl font-bold text-slate-900 mt-1">14 mins</h3>
        </div>
      </div>

      <div className="bg-white p-8 rounded-2xl shadow-sm border border-slate-200 relative overflow-hidden">
        <div className="absolute top-0 right-0 w-64 h-64 bg-blue-50 rounded-full blur-3xl -mr-20 -mt-20 z-0"></div>
        <div className="relative z-10">
          <h2 className="text-xl font-bold text-slate-900 mb-3">Ir al Simulador de Algoritmos</h2>
          <p className="text-slate-600 mb-6 max-w-xl">
            Ejecuta y compara los algoritmos de optimización (Ant Colony Optimization vs Híbrido Genético) en tiempo real para visualizar rutas, capacidades de aeropuertos y el comportamiento del tráfico de maletas.
          </p>
          <Link 
            to="/simulator" 
            className="inline-flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-5 py-2.5 rounded-xl font-medium transition-colors"
          >
            Abrir Simulador <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      </div>
    </div>
  );
}
