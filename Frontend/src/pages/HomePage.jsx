import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  RotateCw,
  Calendar,
  Clock,
  Building,
  Plane,
  Package,
  ArrowRight,
  Map as MapIcon,
  Activity,
  Boxes,
} from "lucide-react";

const QUICK_ACCESS = [
  {
    to: "/operaciones",
    icon: RotateCw,
    title: "Operaciones día a día",
    desc: "Monitorea en tiempo real el flujo de maletas, vuelos y almacenes.",
    accent: "text-info",
  },
  {
    to: "/simulator/period",
    icon: Calendar,
    title: "Simulación por periodo",
    desc: "Ejecuta una ventana de días y analiza el comportamiento del sistema.",
    accent: "text-blue-400",
  },
  {
    to: "/simulator/collapse",
    icon: Clock,
    title: "Simulación hasta colapso",
    desc: "Estresa la red hasta encontrar el punto de saturación.",
    accent: "text-warning",
  },
  {
    to: "/airports",
    icon: Building,
    title: "Aeropuertos",
    desc: "Administra los nodos logísticos: capacidad, ubicación y estado.",
    accent: "text-success",
  },
  {
    to: "/flights/plans",
    icon: Plane,
    title: "Vuelos",
    desc: "Consulta planes de vuelo y la ocurrencia de cada instancia.",
    accent: "text-fuchsia-400",
  },
  {
    to: "/orders",
    icon: Package,
    title: "Pedidos",
    desc: "Registra y sigue los envíos de maletas entre aeropuertos.",
    accent: "text-info",
  },
];

const FEATURES = [
  {
    icon: MapIcon,
    title: "Mapa interactivo",
    desc: "Visualiza aeropuertos y vuelos en vivo sobre un mapa global con código de colores por ocupación.",
  },
  {
    icon: Activity,
    title: "Métricas en tiempo real",
    desc: "Sigue maletas en transito, entregadas y la capacidad de aeropuertos y vuelos.",
  },
  {
    icon: Boxes,
    title: "Trazabilidad de carga",
    desc: "Inspecciona el manifiesto de cada vuelo y el contenido de cada almacén al instante.",
  },
];

export default function HomePage() {
  const navigate = useNavigate();
  const [logoError, setLogoError] = useState(false);

  return (
    <div className="w-full h-full overflow-y-auto bg-surface-0 text-slate-200">
      <div className="mx-auto max-w-6xl px-4 sm:px-8 py-10 sm:py-14">
        {/* Encabezado con espacio para el logo */}
        <header className="flex flex-col items-center text-center gap-6 mb-12 sm:mb-16">
          {/* Logo de la app. Coloca el archivo en Frontend/public/logo.png.
              Si no existe, se muestra un marcador de reserva. */}
          {logoError ? (
            <div
              className="flex h-40 w-40 sm:h-48 sm:w-48 items-center justify-center rounded-full border-2 border-dashed border-slate-700 bg-surface-1/60"
              aria-label="Espacio reservado para el logo"
            >
              <span className="text-[11px] uppercase tracking-widest text-slate-500">Logo</span>
            </div>
          ) : (
            <img
              src="/logo.png"
              alt="AeroLuggage"
              onError={() => setLogoError(true)}
              className="h-40 w-40 sm:h-48 sm:w-48 rounded-full object-cover bg-surface-1 ring-2 ring-slate-700/60 shadow-lg"
            />
          )}

          <div>
            <h1 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white">
              AeroLuggage
            </h1>
            <p className="mt-3 max-w-2xl text-base sm:text-lg text-slate-400">
              Plataforma de simulación y control logístico para el ruteo de maletas
              en una red aérea global. Planifica, simula y monitorea las operaciones
              de extremo a extremo.
            </p>
          </div>

          <button
            type="button"
            onClick={() => navigate("/operaciones")}
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-blue-500"
          >
            Ir a operaciones día a día
            <ArrowRight className="h-4 w-4" />
          </button>
        </header>

        {/* Accesos rápidos */}
        <section className="mb-14">
          <h2 className="mb-5 text-sm font-bold uppercase tracking-wider text-slate-400">
            Accesos rápidos
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {QUICK_ACCESS.map(({ to, icon: Icon, title, desc, accent }) => (
              <button
                key={to}
                type="button"
                onClick={() => navigate(to)}
                className="group flex flex-col items-start gap-3 rounded-xl border border-slate-800 bg-surface-1 p-5 text-left transition-colors hover:border-slate-600 hover:bg-surface-2"
              >
                <div className={`rounded-lg border border-slate-800 bg-surface-2 p-2.5 ${accent}`}>
                  <Icon className="h-5 w-5" />
                </div>
                <div className="flex-1">
                  <h3 className="font-bold text-white">{title}</h3>
                  <p className="mt-1 text-sm text-slate-400">{desc}</p>
                </div>
                <span className="mt-1 inline-flex items-center gap-1 text-xs font-semibold text-blue-400 opacity-0 transition-opacity group-hover:opacity-100">
                  Abrir <ArrowRight className="h-3.5 w-3.5" />
                </span>
              </button>
            ))}
          </div>
        </section>

        {/* Características — contenido informativo (no accionable): sin contenedor
            tipo card para no confundirlo con los accesos rápidos clicables. */}
        <section className="mb-4 border-t border-slate-800 pt-10">
          <h2 className="mb-6 text-sm font-bold uppercase tracking-wider text-slate-400">
            ¿Qué puedes hacer aquí?
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-x-8 gap-y-8">
            {FEATURES.map(({ icon: Icon, title, desc }) => (
              <div key={title} className="flex gap-3">
                <Icon className="h-6 w-6 shrink-0 text-info" />
                <div>
                  <h3 className="font-bold text-white">{title}</h3>
                  <p className="mt-1 text-sm text-slate-400">{desc}</p>
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}
