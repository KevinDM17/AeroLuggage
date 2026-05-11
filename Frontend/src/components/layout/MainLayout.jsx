import { useState, useEffect } from "react";
import { Outlet, useLocation } from "react-router-dom";
import { Menu } from "lucide-react";
import Sidebar from "./Sidebar";
import RightPanel from "./RightPanel";

const DESKTOP_BREAKPOINT = 1024;

function getIsDesktop() {
  if (typeof window === "undefined") return true;
  return window.innerWidth >= DESKTOP_BREAKPOINT;
}

export default function MainLayout() {
  const [isDesktop, setIsDesktop] = useState(getIsDesktop);
  const [leftOpen, setLeftOpen] = useState(getIsDesktop);
  const [rightOpen, setRightOpen] = useState(getIsDesktop);
  const location = useLocation();

  const isSimulator = location.pathname === "/";

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

  return (
    <div className="flex h-screen overflow-hidden bg-[#0B0E14] text-slate-200 font-sans relative">
      {showLeftHamburger && (
        <button
          onClick={() => setLeftOpen(true)}
          aria-label="Abrir menú"
          className="fixed top-3 left-3 z-[10001] p-2 bg-[#0B0E14]/90 backdrop-blur border border-slate-700 rounded-lg text-slate-300 hover:text-white hover:bg-[#151b2b] transition-colors shadow-lg"
        >
          <Menu className="w-5 h-5" />
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
          <Sidebar onClose={closeLeft} />
        </div>
      )}

      <div className="flex-1 flex flex-col h-full overflow-hidden relative border-r border-slate-800 min-w-0">
        <main className="flex-1 overflow-hidden bg-[#1e1b4b]">
          <Outlet />
        </main>
      </div>

      {showRightHamburger && (
        <button
          onClick={() => setRightOpen(true)}
          aria-label="Abrir panel"
          className="fixed top-3 right-3 z-[10001] p-2 bg-[#0B0E14]/90 backdrop-blur border border-slate-700 rounded-lg text-slate-300 hover:text-white hover:bg-[#151b2b] transition-colors shadow-lg"
        >
          <Menu className="w-5 h-5" />
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
          <RightPanel onClose={closeRight} />
        </div>
      )}
    </div>
  );
}
