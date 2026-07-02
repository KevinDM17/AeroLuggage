import { Building, CheckCircle2, ChevronDown, ChevronLeft, ChevronRight, ChevronUp, Gauge, Luggage, Plane, Warehouse } from "lucide-react";
import { cloneElement, isValidElement, useState, useEffect, useLayoutEffect, useRef } from "react";
import { useLocation } from "react-router-dom";
import AirportMap from "../map/AirportMap";

const clampPanelToContainer = (nextPos, rect, containerWidth, containerHeight) => {
  const margin = 16;
  const width = rect?.width ?? 0;
  const height = rect?.height ?? 0;
  const maxX = Math.max(margin, containerWidth - width - margin);
  const maxY = Math.max(margin, containerHeight - height - margin);

  return {
    x: Math.min(Math.max(nextPos.x, margin), maxX),
    y: Math.min(Math.max(nextPos.y, margin), maxY),
  };
};

export default function MapDashboard({
  title,
  header = null,
  mapOverlay = null,
  mapOverlays = null,
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
  const mapStageRef = useRef(null);
  const overlayItems = Array.isArray(mapOverlays) && mapOverlays.length > 0
    ? mapOverlays
    : mapOverlay
      ? [{ id: "default-overlay", content: mapOverlay }]
      : [];

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

      <div ref={mapStageRef} className="absolute inset-0">
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

        {overlayItems.map((overlayItem, index) => (
          <DraggableOverlayPanel
            key={overlayItem.id ?? `overlay-${index}`}
            content={overlayItem.content ?? overlayItem}
            draggable={draggable}
            index={index}
            total={overlayItems.length}
            mapStageRef={mapStageRef}
          />
        ))}

        {header && (
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

    </div>
  );
}

function DraggableOverlayPanel({ content, draggable, index, total, mapStageRef }) {
  const [panelPos, setPanelPos] = useState(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isPanelCollapsed, setIsPanelCollapsed] = useState(false);
  const dragStartRef = useRef({ offsetX: 0, offsetY: 0 });
  const panelRef = useRef(null);
  const hasUserMovedPanelRef = useRef(false);
  const dragMovedRef = useRef(false);
  const buttonSide = index === 0 ? "left" : "right";
  const expandFromButtonRef = useRef(null);

  const contentWithJoinedEdge = isValidElement(content)
    ? cloneElement(content, {
        className: `${content.props.className ?? ""} ${
          buttonSide === "left" ? "rounded-l-none border-l-0" : "rounded-r-none border-r-0"
        }`.trim(),
      })
    : content;

  const isInteractiveTarget = (el) => el.closest("button, input, select, label, a");

  useLayoutEffect(() => {
    if (!draggable || isPanelCollapsed || !panelRef.current) return;

    const rect = panelRef.current.getBoundingClientRect();
    const containerRect = mapStageRef.current?.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    if (!containerRect?.width || !containerRect?.height) return;

    setPanelPos((currentPos) => {
      if (expandFromButtonRef.current) {
        const triggerRect = expandFromButtonRef.current;
        expandFromButtonRef.current = null;

        const nextPos = buttonSide === "left"
          ? {
              x: triggerRect.left - containerRect.left + triggerRect.width,
              y: triggerRect.top - containerRect.top,
            }
          : {
              x: triggerRect.left - containerRect.left - rect.width,
              y: triggerRect.top - containerRect.top,
            };

        return clampPanelToContainer(nextPos, rect, containerRect.width, containerRect.height);
      }

      if (currentPos && hasUserMovedPanelRef.current) {
        return clampPanelToContainer(currentPos, rect, containerRect.width, containerRect.height);
      }

      const gap = 24;
      const centerOffset = (index - (total - 1) / 2) * (rect.width + gap);

      return clampPanelToContainer(
        {
          x: (containerRect.width - rect.width) / 2 + centerOffset,
          y: containerRect.height - rect.height - 24,
        },
        rect,
        containerRect.width,
        containerRect.height,
      );
    });
  }, [draggable, index, isPanelCollapsed, mapStageRef, total, content]);

  useEffect(() => {
    if (!draggable || isPanelCollapsed) return;

    const handleResize = () => {
      if (!panelRef.current || !mapStageRef.current) return;
      const rect = panelRef.current.getBoundingClientRect();
      const containerRect = mapStageRef.current.getBoundingClientRect();
      setPanelPos((currentPos) => {
        const basePos = currentPos ?? {
          x: (containerRect.width - rect.width) / 2,
          y: containerRect.height - rect.height - 24,
        };
        return clampPanelToContainer(basePos, rect, containerRect.width, containerRect.height);
      });
    };

    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, [draggable, isPanelCollapsed, mapStageRef]);

  const handleDragStart = (e, options = {}) => {
    const { allowInteractiveTarget = false } = options;
    if (e.button !== 0) return;
    if (!allowInteractiveTarget && isInteractiveTarget(e.target)) return;
    if (!panelRef.current) return;

    const rect = panelRef.current.getBoundingClientRect();
    dragStartRef.current = {
      offsetX: e.clientX - rect.left,
      offsetY: e.clientY - rect.top,
    };
    dragMovedRef.current = false;
    hasUserMovedPanelRef.current = true;
    setIsDragging(true);
  };

  useEffect(() => {
    if (!isDragging) return;

    const onMove = (e) => {
      const rect = panelRef.current?.getBoundingClientRect();
      const containerRect = mapStageRef.current?.getBoundingClientRect();
      if (!rect || !containerRect) return;

      dragMovedRef.current = true;
      setPanelPos(
        clampPanelToContainer(
          {
            x: e.clientX - containerRect.left - dragStartRef.current.offsetX,
            y: e.clientY - containerRect.top - dragStartRef.current.offsetY,
          },
          rect,
          containerRect.width,
          containerRect.height,
        ),
      );
    };

    const onUp = () => setIsDragging(false);
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
    return () => {
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
  }, [isDragging, mapStageRef]);

  const handleHide = (e) => {
    const buttonRect = e.currentTarget.getBoundingClientRect();
    const containerRect = mapStageRef.current?.getBoundingClientRect();

    if (containerRect) {
      setPanelPos({
        x: buttonRect.left - containerRect.left,
        y: buttonRect.top - containerRect.top,
      });
    }

    setIsPanelCollapsed(true);
  };
  const handleShow = () => {
    if (dragMovedRef.current) {
      dragMovedRef.current = false;
      return;
    }

    if (panelRef.current) {
      expandFromButtonRef.current = panelRef.current.getBoundingClientRect();
    }

    setIsPanelCollapsed(false);
  };

  if (!draggable) {
    return (
      <div className="absolute left-1/2 bottom-6 z-[2000] -translate-x-1/2">
        {content}
      </div>
    );
  }

  if (isPanelCollapsed) {
    return (
      <div
        ref={panelRef}
        className="absolute select-none z-[2000]"
        style={
          panelPos
            ? { left: panelPos.x, top: panelPos.y, cursor: isDragging ? "grabbing" : "grab" }
            : { left: 16, bottom: "24px", cursor: "grab" }
        }
        onMouseDown={(e) => handleDragStart(e, { allowInteractiveTarget: true })}
      >
        <button
          type="button"
          onClick={handleShow}
          className={`min-h-16 bg-surface-2/95 border border-slate-700 px-2.5 text-slate-400 hover:text-white transition-colors ${
            buttonSide === "left" ? "rounded-l-xl rounded-r-none" : "rounded-l-none rounded-r-xl"
          }`}
          title="Mostrar panel"
        >
          {buttonSide === "left"
            ? <ChevronRight className="w-4 h-4" />
            : <ChevronLeft className="w-4 h-4" />}
        </button>
      </div>
    );
  }

  return (
    <div
      ref={panelRef}
      className="absolute z-[2000] w-max max-w-[calc(100vw-2rem)] select-none"
      style={{
        left: panelPos?.x ?? 0,
        top: panelPos?.y ?? 0,
        cursor: isDragging ? "grabbing" : "grab",
        visibility: panelPos ? "visible" : "hidden",
      }}
      onMouseDown={handleDragStart}
    >
      <div className="relative">
        <button
          type="button"
          onClick={handleHide}
          className={`absolute inset-y-0 z-10 flex items-center bg-surface-2/95 border border-slate-700 px-2.5 text-slate-400 hover:text-white transition-colors ${
            buttonSide === "left"
              ? "-left-9 rounded-l-xl rounded-r-none border-r-0"
              : "-right-9 rounded-l-none rounded-r-xl border-l-0"
          }`}
          title="Ocultar panel"
        >
          {buttonSide === "left"
            ? <ChevronLeft className="w-4 h-4" />
            : <ChevronRight className="w-4 h-4" />}
        </button>
        <div
          className={`pointer-events-none absolute top-px bottom-px z-10 w-[2px] bg-surface-2/95 ${
            buttonSide === "left" ? "left-0" : "right-0"
          }`}
        />
        {contentWithJoinedEdge}
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
