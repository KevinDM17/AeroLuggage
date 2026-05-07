import { useState, useEffect } from "react";
import { Outlet, useLocation } from "react-router-dom";
import { Menu } from "lucide-react";
import Sidebar from "./Sidebar";
import RightPanel from "./RightPanel";

export default function MainLayout() {
  const [leftOpen, setLeftOpen] = useState(true);
  const [rightOpen, setRightOpen] = useState(true);
  const location = useLocation();

  const isSimulator = location.pathname === "/";

  // When leaving simulator, make sure left is open
  useEffect(() => {
    if (!isSimulator) {
      setLeftOpen(true);
    }
  }, [isSimulator]);

  return (
    <div className="flex h-screen overflow-hidden bg-[#0B0E14] text-slate-200 font-sans relative">
      {!leftOpen && isSimulator && (
        <button 
          onClick={() => setLeftOpen(true)}
          className="absolute top-4 left-4 z-[9999] p-2 bg-[#0B0E14] border border-slate-800 rounded-lg text-slate-400 hover:text-white"
        >
          <Menu className="w-5 h-5" />
        </button>
      )}

      {leftOpen && <Sidebar onClose={isSimulator ? () => setLeftOpen(false) : undefined} />}
      
      <div className="flex-1 flex flex-col h-full overflow-hidden relative border-r border-slate-800">
        <main className="flex-1 overflow-hidden bg-[#1e1b4b]">
          <Outlet />
        </main>
      </div>
      
      {!rightOpen && isSimulator && (
        <button 
          onClick={() => setRightOpen(true)}
          className="absolute top-4 right-4 z-[9999] p-2 bg-[#0B0E14] border border-slate-800 rounded-lg text-slate-400 hover:text-white"
        >
          <Menu className="w-5 h-5" />
        </button>
      )}
      
      {rightOpen && isSimulator && <RightPanel onClose={() => setRightOpen(false)} />}
    </div>
  );
}
