import { Luggage, Plane, Warehouse } from "lucide-react";
import AirportMap from "../map/AirportMap";

/**
 * Capa común para los 3 escenarios (día a día, periodo, colapso):
 * - Header con título + controles opcionales (prop `header`)
 * - Strip de KPIs globales (siempre visible — son la métrica central de un command center)
 * - Mapa con overlay de fecha/hora (top-left)
 * Las props `pl-14 sm:pl-16` y `pr-14 sm:pr-16` dejan espacio a los hamburger de
 * izquierda/derecha que `MainLayout` pone como `fixed`.
 */
export default function MapDashboard({
  title,
  header = null,
  date = "18-03-26",
  time = "12:34:16 UTC",
  metrics = {},
}) {
  const {
    bagsInTransit = 825,
    activeFlights = 3,
    freeCapacityPct = 42,
  } = metrics;

  const capacityTone =
    freeCapacityPct < 15 ? "danger" : freeCapacityPct < 35 ? "warning" : "success";

  return (
    <div className="flex flex-col h-full bg-canvas">
      <div className="px-4 sm:px-8 pt-4 pl-14 sm:pl-16 pr-14 sm:pr-16 flex items-center justify-between gap-4 flex-wrap">
        <h1 className="text-lg sm:text-xl font-bold tracking-tight text-white mb-2">{title}</h1>
        {header}
      </div>

      <div className="px-4 sm:px-8 pl-14 sm:pl-16 pr-14 sm:pr-16 pb-2">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 sm:gap-3">
          <Kpi
            icon={Luggage}
            label="Maletas en Tránsito"
            value={bagsInTransit.toLocaleString()}
            tone="info"
          />
          <Kpi
            icon={Plane}
            label="Vuelos Activos"
            value={activeFlights}
            tone="fuchsia"
          />
          <Kpi
            icon={Warehouse}
            label="Capacidad Libre Global"
            value={`${freeCapacityPct}%`}
            tone={capacityTone}
          />
        </div>
      </div>

      <div className="flex-1 relative w-full h-full bg-canvas p-2 sm:p-4 min-h-0">
        <div className="w-full h-full rounded-2xl overflow-hidden relative shadow-[0_0_40px_rgba(0,0,0,0.5)] border border-slate-700/50">
          <AirportMap />

          <div className="absolute top-4 left-1/2 -translate-x-1/2 sm:left-6 sm:translate-x-0 z-[1000]">
            <div className="bg-surface-2/75 backdrop-blur pl-4 pr-6 py-2.5 rounded-xl border border-slate-700 flex gap-4 sm:gap-6">
              <div>
                <div className="text-[10px] text-slate-400 font-medium">Fecha</div>
                <div className="text-sm font-bold text-success">{date}</div>
              </div>
              <div>
                <div className="text-[10px] text-slate-400 font-medium">Hora</div>
                <div className="text-sm font-bold text-success">{time}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

const TONE_CLASSES = {
  info:    "text-info",
  fuchsia: "text-fuchsia-400",
  success: "text-success",
  warning: "text-warning",
  danger:  "text-danger",
};

function Kpi({ icon: Icon, label, value, tone = "info" }) {
  const valueClass = TONE_CLASSES[tone] ?? TONE_CLASSES.info;
  return (
    <div className="bg-surface-1/70 backdrop-blur border border-slate-800 rounded-xl px-3 sm:px-4 py-2.5 flex items-center gap-3">
      <div className={`shrink-0 ${valueClass}`}>
        <Icon className="w-5 h-5" />
      </div>
      <div className="min-w-0">
        <div className="text-[10px] sm:text-xs text-slate-400 font-medium uppercase tracking-wider truncate">
          {label}
        </div>
        <div className={`text-xl sm:text-2xl font-bold tabular-nums leading-none mt-1 ${valueClass}`}>
          {value}
        </div>
      </div>
    </div>
  );
}
