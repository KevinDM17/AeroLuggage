import { Building, CheckCircle2, ChevronDown, ChevronUp, Gauge, Luggage, Plane, Warehouse } from "lucide-react";
import { useState, useEffect, useRef } from "react";
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
  draggable = true,
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

  const [panelPos, setPanelPos] = useState(null);
  const [isDragging, setIsDragging] = useState(false);
  const dragStartRef = useRef({ mouseX: 0, mouseY: 0, panelX: 0, panelY: 0 });
  const panelRef = useRef(null);

  const isInteractiveTarget = (el) => el.closest("button, input, select, label, a");

  const handleDragStart = (e) => {
    if (e.button !== 0) return;
    if (isInteractiveTarget(e.target)) return;
    const rect = panelRef.current.getBoundingClientRect();
    dragStartRef.current = {
      mouseX: e.clientX,
      mouseY: e.clientY,
      panelX: rect.left,
      panelY: rect.top,
    };
    setIsDragging(true);
  };

  useEffect(() => {
    if (!isDragging) return;
    const onMove = (e) => {
      const dx = e.clientX - dragStartRef.current.mouseX;
      const dy = e.clientY - dragStartRef.current.mouseY;
      setPanelPos({
        x: dragStartRef.current.panelX + dx,
        y: dragStartRef.current.panelY + dy,
      });
    };
    const onUp = () => setIsDragging(false);
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
    return () => {
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
  }, [isDragging]);

  const handleShow = () => {
    setShowBottom(true);
    setPanelPos(null);
  };

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

        {showBottom && mapOverlay && draggable && (
          <div
            ref={panelRef}
            className="absolute select-none z-[2000]"
            style={
              panelPos
                ? { left: panelPos.x, top: panelPos.y, cursor: isDragging ? "grabbing" : "grab" }
                : { left: "50%", bottom: "24px", transform: "translateX(-50%)", cursor: "grab" }
            }
            onMouseDown={handleDragStart}
          >
            <div className="relative">
              <button
                type="button"
                onClick={() => setShowBottom(false)}
                className="absolute top-1.5 right-1.5 z-10 rounded-full p-1 text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
                title="Ocultar panel"
              >
                <ChevronDown className="w-3.5 h-3.5" />
              </button>
              {mapOverlay}
            </div>
          </div>
        )}

        {showBottom && mapOverlay && !draggable && (
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

      {/* Barra superior izquierda. Empieza en left-14 para dejar libre el botón
          de la barra lateral (hamburguesa, fijo en top-3 left-3). Contiene el
          desplegable de Métricas y, a su derecha, el título de la vista. */}
      <div className="absolute top-3 left-14 z-[2500] flex items-center gap-2 max-w-[calc(100%-9rem)]">
        {/* Métricas: botón + desplegable. Abre y cierra desde el mismo lugar. */}
        <div className="relative shrink-0">
          <button
            type="button"
            onClick={() => setShowKpis((v) => !v)}
            aria-expanded={showKpis}
            className="flex items-center gap-1.5 rounded-lg bg-surface-1/70 hover:bg-surface-1/90 backdrop-blur border border-slate-700/50 px-2 py-1.5 transition-colors"
            title={showKpis ? "Ocultar métricas" : "Mostrar métricas"}
          >
            <Gauge className="w-3.5 h-3.5 text-info" />
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Métricas</span>
            {showKpis
              ? <ChevronUp className="w-3.5 h-3.5 text-slate-400" />
              : <ChevronDown className="w-3.5 h-3.5 text-slate-400" />}
          </button>
          {showKpis && (
            <div className="absolute top-full left-0 mt-1 w-44 flex flex-col rounded-lg bg-surface-1/90 backdrop-blur border border-slate-700/50 px-1 py-1 shadow-lg">
              <Kpi icon={Luggage} label="En Tránsito" value={bagsInTransit.toLocaleString()} tone="info" />
              <Kpi icon={CheckCircle2} label="Entregadas" value={bagsDelivered.toLocaleString()} tone="success" />
              <Kpi icon={Plane} label="Vuelos Activos" value={activeFlights} tone="info" />
              <Kpi icon={Building} label="Ocup. Aerop." value={`${airportCapacityPct}%`} tone={airportCapacityTone} />
              <Kpi icon={Warehouse} label="Ocup. Vuelos" value={`${flightCapacityPct}%`} tone={flightCapacityTone} />
            </div>
          )}
        </div>

        {/* Título de la vista */}
        {title && (
          <div className="min-w-0 bg-surface-1/60 backdrop-blur px-3 py-1.5 rounded-lg">
            <h1 className="text-lg sm:text-xl font-bold tracking-tight text-white truncate">{title}</h1>
          </div>
        )}
      </div>

      {!showBottom && mapOverlay && draggable && (
        <button
          type="button"
          onClick={handleShow}
          className="absolute bottom-4 left-1/2 -translate-x-1/2 z-[4000] bg-surface-1/80 hover:bg-surface-1 backdrop-blur border border-slate-700/50 rounded-full p-2 text-slate-300 hover:text-white transition-colors shadow-lg"
          title="Mostrar panel inferior"
        >
          <ChevronUp className="w-4 h-4" />
        </button>
      )}
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
