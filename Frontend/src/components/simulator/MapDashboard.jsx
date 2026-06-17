import { CheckCircle2, ChevronDown, ChevronUp, CircleAlert, Luggage, Plane, Warehouse } from "lucide-react";
import { useState } from "react";
import { useLocation } from "react-router-dom";
import AirportMap from "../map/AirportMap";

export default function MapDashboard({
  title,
  header = null,
  mapOverlay = null,
  showMapFlights = true,
  showMapRouteLines = true,
  animateMapFlights = false,
  mapAutoload = true,
  airports,
  flights,
  simulatedNowMs,
  simulatedDayDurationMs,
  progress,
  simStatus,
  metrics = {},
}) {
  const location = useLocation();
  const {
    bagsInTransit = 0,
    bagsDelivered = 0,
    bagsUnassigned = 0,
    activeFlights = 0,
    freeCapacityPct = 0,
  } = metrics;

  const capacityTone =
    freeCapacityPct < 15 ? "danger" : freeCapacityPct < 35 ? "warning" : "success";

  const [showKpis, setShowKpis] = useState(true);
  const [showBottom, setShowBottom] = useState(true);

  return (
    <div className="relative w-full h-full">
      {progress != null && simStatus !== "idle" && (
        <div className="absolute top-0 left-0 right-0 z-[5000] h-1">
          <div
            className={`h-full transition-all ${simStatus === "done" ? "bg-success" : simStatus === "paused" ? "bg-warning" : "bg-info"}`}
            style={{ width: `${progress}%` }}
          />
        </div>
      )}

      <div className="absolute inset-0">
        <AirportMap
          key={location.pathname}
          showFlights={showMapFlights}
          showRouteLines={showMapRouteLines}
          airports={airports}
          flights={flights}
          autoload={mapAutoload}
          simulatedNowMs={simulatedNowMs}
          simulatedDayDurationMs={simulatedDayDurationMs}
          animateFlights={animateMapFlights}
        />

        {showBottom && mapOverlay && (
          <div className="absolute left-1/2 bottom-6 z-[2000] -translate-x-1/2">
            {mapOverlay}
          </div>
        )}

        {showBottom && header && (
          <div className="absolute bottom-6 right-4 z-[3000]">
            {header}
          </div>
        )}
      </div>

      {title && (
        <div className="absolute top-3 left-16 z-[2500] max-w-[calc(100%-8rem)]">
          <div className="bg-surface-1/60 backdrop-blur px-3 py-1.5 rounded-lg">
            <h1 className="text-lg sm:text-xl font-bold tracking-tight text-white truncate">{title}</h1>
          </div>
        </div>
      )}

      {showKpis && (
        <div className="absolute top-2 left-2 right-2 z-[2000]">
          <div className="relative bg-surface-1/75 backdrop-blur border border-slate-700/50 rounded-xl pl-2 pr-9 py-1.5">
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-1.5 sm:gap-2">
              <Kpi
                icon={Luggage}
                label="Maletas en Tránsito"
                value={bagsInTransit.toLocaleString()}
                tone="info"
              />
              <Kpi
                icon={CheckCircle2}
                label="Maletas Entregadas"
                value={bagsDelivered.toLocaleString()}
                tone="success"
              />
              <Kpi
                icon={CircleAlert}
                label="Maletas No Asignadas"
                value={bagsUnassigned.toLocaleString()}
                tone={bagsUnassigned > 0 ? "danger" : "success"}
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
            <button
              type="button"
              onClick={() => setShowKpis(false)}
              className="absolute top-1/2 right-2 -translate-y-1/2 z-[4000] bg-surface-1/60 hover:bg-surface-1/80 backdrop-blur border border-slate-700/50 rounded-full p-1 text-slate-400 hover:text-white transition-colors"
              title="Ocultar métricas"
            >
              <ChevronUp className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      )}

      {!showKpis && (
        <button
          type="button"
          onClick={() => setShowKpis(true)}
          className="absolute top-2 left-1/2 -translate-x-1/2 z-[4000] bg-surface-1/60 hover:bg-surface-1/80 backdrop-blur border border-slate-700/50 rounded-full p-1 text-slate-400 hover:text-white transition-colors"
          title="Mostrar métricas"
        >
          <ChevronDown className="w-3.5 h-3.5" />
        </button>
      )}

      <button
        type="button"
        onClick={() => setShowBottom((v) => !v)}
        className="absolute bottom-1 left-1/2 -translate-x-1/2 z-[4000] bg-surface-1/60 hover:bg-surface-1/80 backdrop-blur border border-slate-700/50 rounded-full p-1 text-slate-400 hover:text-white transition-colors"
        title={showBottom ? "Ocultar panel inferior" : "Mostrar panel inferior"}
      >
        {showBottom ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronUp className="w-3.5 h-3.5" />}
      </button>
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
    <div className="bg-surface-1/70 backdrop-blur border border-slate-800 rounded-lg px-2.5 py-1 flex items-center gap-2 min-w-0">
      <div className={`shrink-0 ${valueClass}`}>
        <Icon className="w-4 h-4" />
      </div>
      <div className="min-w-0 flex-1">
        <div
          className="text-[10px] text-slate-400 font-medium uppercase tracking-wide leading-tight truncate"
          title={label}
        >
          {label}
        </div>
        <div className={`text-base sm:text-lg font-bold tabular-nums leading-none mt-0.5 ${valueClass}`}>
          {value}
        </div>
      </div>
    </div>
  );
}
