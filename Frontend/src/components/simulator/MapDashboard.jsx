import { Building, CheckCircle2, ChevronDown, ChevronUp, Luggage, Plane, Warehouse } from "lucide-react";
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
    activeFlights = 0,
    airportCapacityPct = 0,
    flightCapacityPct = 0,
  } = metrics;

  const airportCapacityTone =
    airportCapacityPct >= 85 ? "danger" : airportCapacityPct >= 65 ? "warning" : "success";
  const flightCapacityTone =
    flightCapacityPct >= 85 ? "danger" : flightCapacityPct >= 65 ? "warning" : "success";

  const [showKpis, setShowKpis] = useState(false);
  const [showBottom, setShowBottom] = useState(true);

  return (
    <div className="relative w-full h-full">
      {progress != null && simStatus !== "idle" && (
        <div className="absolute top-0 left-0 right-0 z-[5000] h-1">
          <div
            className={`h-full transition-all ${simStatus === "collapsed" ? "bg-danger" : simStatus === "done" ? "bg-success" : simStatus === "paused" ? "bg-warning" : "bg-info"}`}
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

      {showKpis ? (
        <div className="absolute top-14 left-2 z-[2000] w-44">
          <div className="relative bg-surface-1/80 backdrop-blur border border-slate-700/50 rounded-lg p-1 pt-5">
            <button
              type="button"
              onClick={() => setShowKpis(false)}
              className="absolute top-1 right-1 z-[4000] text-slate-400 hover:text-white transition-colors"
              title="Ocultar métricas"
            >
              <ChevronUp className="w-3.5 h-3.5" />
            </button>
            <div className="flex flex-col">
              <Kpi icon={Luggage} label="En Tránsito" value={bagsInTransit.toLocaleString()} tone="info" />
              <Kpi icon={CheckCircle2} label="Entregadas" value={bagsDelivered.toLocaleString()} tone="success" />
              <Kpi icon={Plane} label="Vuelos Activos" value={activeFlights} tone="fuchsia" />
              <Kpi icon={Building} label="Ocup. Aerop." value={`${airportCapacityPct}%`} tone={airportCapacityTone} />
              <Kpi icon={Warehouse} label="Ocup. Vuelos" value={`${flightCapacityPct}%`} tone={flightCapacityTone} />
            </div>
          </div>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setShowKpis(true)}
          className="absolute top-14 left-2 z-[4000] bg-surface-1/70 hover:bg-surface-1/90 backdrop-blur border border-slate-700/50 rounded-full p-1 text-slate-400 hover:text-white transition-colors"
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
    <div className="flex items-center gap-1.5 px-1 py-0.5 min-w-0">
      <Icon className={`w-3 h-3 shrink-0 ${valueClass}`} />
      <span
        className="flex-1 min-w-0 text-[9px] text-slate-400 font-medium uppercase tracking-wide truncate"
        title={label}
      >
        {label}
      </span>
      <span className={`shrink-0 text-[11px] font-bold tabular-nums leading-none ${valueClass}`}>
        {value}
      </span>
    </div>
  );
}
