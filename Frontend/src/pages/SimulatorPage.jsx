import AirportMap from "../components/map/AirportMap";
import { ChevronLeft } from "lucide-react";

export default function SimulatorPage() {
  return (
    <div className="flex flex-col h-full bg-[#1e1b4b]">
      <div className="p-4 flex items-center bg-[#1e1b4b]">
        <h1 className="text-xl font-bold tracking-tight text-white mb-2 ml-4">Visualización de Operaciones día a día</h1>
      </div>
      <div className="flex-1 relative w-full h-full bg-[#1e1b4b] p-4">
          <div className="w-full h-full rounded-2xl overflow-hidden relative shadow-[0_0_40px_rgba(0,0,0,0.5)] border border-slate-700/50">
            <AirportMap />

            {/* Overlays */}
            <div className="absolute top-6 left-6 z-[1000] flex gap-2">
              <button className="bg-[#151b2fc0] hover:bg-[#151b2f] backdrop-blur text-slate-300 p-2 rounded-xl border border-slate-700">
                <ChevronLeft className="w-5 h-5" />
              </button>
              <div className="bg-[#151b2fc0] backdrop-blur pl-4 pr-6 py-2.5 rounded-xl border border-slate-700 flex gap-6">
                <div>
                  <div className="text-[10px] text-slate-400 font-medium">Fecha</div>
                  <div className="text-sm font-bold text-[#00ff88]">18-03-26</div>
                </div>
                <div>
                  <div className="text-[10px] text-slate-400 font-medium">Hora</div>
                  <div className="text-sm font-bold text-[#00ff88]">12:34:16 UTC</div>
                </div>
              </div>
            </div>

            <div className="absolute bottom-6 left-1/2 -translate-x-1/2 z-[1000]">
              <div className="bg-[#151b2fc0] backdrop-blur px-6 py-3 rounded-xl border border-slate-700 flex gap-8 items-center cursor-pointer hover:bg-[#151b2f]">
                <div>
                  <div className="text-xs text-slate-400 font-medium whitespace-nowrap">Maletas en Tránsito</div>
                  <div className="text-lg font-bold text-[#3abff8] mt-0.5">825</div>
                </div>
                <div>
                  <div className="text-xs text-slate-400 font-medium whitespace-nowrap">Vuelos Activos</div>
                  <div className="text-lg font-bold text-fuchsia-500 mt-0.5">3</div>
                </div>
              </div>
            </div>
          </div>
      </div>
    </div>
  );
}
