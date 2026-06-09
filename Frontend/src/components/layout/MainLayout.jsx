import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";
import { PanelLeftOpen, PanelRightOpen } from "lucide-react";
import Sidebar from "./Sidebar";
import RightPanel from "./RightPanel";

const DESKTOP_BREAKPOINT = 1024;
const EMPTY_SIMULATION_PANEL_DATA = {
  airports: [],
  flights: new Map(),
  orders: new Map(),
  bags: new Map(),
  routes: new Map(),
  loaded: false,
};

function getIsDesktop() {
  if (typeof window === "undefined") return true;
  return window.innerWidth >= DESKTOP_BREAKPOINT;
}

export default function MainLayout() {
  const [isDesktop, setIsDesktop] = useState(getIsDesktop);
  const [leftOpen, setLeftOpen] = useState(getIsDesktop);
  const [rightOpen, setRightOpen] = useState(getIsDesktop);
  const [simulationPanelData, setSimulationPanelData] = useState(EMPTY_SIMULATION_PANEL_DATA);
  const [cancelledFlightIds, setCancelledFlightIds] = useState(() => new Set());
  const [panelResetVersion, setPanelResetVersion] = useState(0);
  const location = useLocation();
  const previousIsSimulatorRef = useRef(null);

  const isSimulator = location.pathname === "/" || location.pathname.startsWith("/simulator");

  useEffect(() => {
    const handleResize = () => {
      const desktop = getIsDesktop();
      setIsDesktop(desktop);
      if (desktop) {
        setLeftOpen(true);
        setRightOpen(true);
      } else {
        setLeftOpen(false);
        setRightOpen(false);
      }
    };
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  useEffect(() => {
    if (!isDesktop) {
      setLeftOpen(false);
      setRightOpen(false);
    }
  }, [location.pathname, isDesktop]);

  const showLeftHamburger = !leftOpen;
  const showRightHamburger = isSimulator && !rightOpen;
  const showBackdrop = !isDesktop && (leftOpen || (rightOpen && isSimulator));

  const closeLeft = () => setLeftOpen(false);
  const closeRight = () => setRightOpen(false);
  const resetSimulationPanelData = useCallback(() => {
    setSimulationPanelData({ ...EMPTY_SIMULATION_PANEL_DATA });
    setCancelledFlightIds(new Set());
    setPanelResetVersion((current) => current + 1);
  }, []);

  useEffect(() => {
    const previousIsSimulator = previousIsSimulatorRef.current;
    if (previousIsSimulator === true && !isSimulator) {
      resetSimulationPanelData();
    }
    previousIsSimulatorRef.current = isSimulator;
  }, [isSimulator, resetSimulationPanelData]);

  const layoutContext = useMemo(
    () => ({
      simulationPanelData,
      setSimulationPanelData,
      resetSimulationPanelData,
      cancelledFlightIds,
      setCancelledFlightIds,
    }),
    [simulationPanelData, resetSimulationPanelData, cancelledFlightIds],
  );

  return (
    <div className="flex h-screen overflow-hidden bg-surface-1 text-slate-200 font-sans relative">
      {showLeftHamburger && (
        <button
          type="button"
          onClick={() => setLeftOpen(true)}
          aria-label="Abrir menú lateral"
          className="fixed top-3 left-3 z-[10001] p-2 bg-surface-1/90 backdrop-blur border border-slate-700 rounded-lg text-slate-300 hover:text-white hover:bg-surface-2 transition-colors shadow-lg"
        >
          <PanelLeftOpen className="w-5 h-5" />
        </button>
      )}

      {showBackdrop && (
        <div
          onClick={() => {
            closeLeft();
            closeRight();
          }}
          className="fixed inset-0 z-[9990] bg-black/60 backdrop-blur-sm lg:hidden"
          aria-hidden="true"
        />
      )}

      {leftOpen && (
        <div
          className={
            isDesktop
              ? "relative z-[9995] shrink-0"
              : "fixed inset-y-0 left-0 z-[9995] max-w-[85%] shadow-2xl"
          }
        >
          <Sidebar onClose={closeLeft} closeOnNavigate={!isDesktop} />
        </div>
      )}

      <div className="flex-1 flex flex-col h-full overflow-hidden relative border-r border-slate-800 min-w-0">
        <main key={location.pathname} className="flex-1 overflow-hidden bg-canvas">
          <Outlet context={layoutContext} />
        </main>
      </div>

      {showRightHamburger && (
        <button
          type="button"
          onClick={() => setRightOpen(true)}
          aria-label="Abrir panel de detalle"
          className="fixed top-3 right-3 z-[10001] p-2 bg-surface-1/90 backdrop-blur border border-slate-700 rounded-lg text-slate-300 hover:text-white hover:bg-surface-2 transition-colors shadow-lg"
        >
          <PanelRightOpen className="w-5 h-5" />
        </button>
      )}

      {rightOpen && isSimulator && (
        <div
          className={
            isDesktop
              ? "relative z-[9995] shrink-0"
              : "fixed inset-y-0 right-0 z-[9995] max-w-[90%] shadow-2xl"
          }
        >
          <RightPanel
            key={panelResetVersion}
            onClose={closeRight}
            simulationPanelData={simulationPanelData}
            setSimulationPanelData={setSimulationPanelData}
            setCancelledFlightIds={setCancelledFlightIds}
          />
        </div>
      )}
    </div>
  );
}
