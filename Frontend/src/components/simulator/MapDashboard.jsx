import AirportMap from "../map/AirportMap";

/**
 * Capa común para los 3 escenarios (día a día, periodo, colapso):
 * mapa + overlays de fecha/hora (top-left) y stats (bottom-center).
 * El header de la página se compone fuera con la prop `header`.
 */
export default function MapDashboard({
  title,
  header = null,
  date = "18-03-26",
  time = "12:34:16 UTC",
  bagsInTransit = 825,
  activeFlights = 3,
}) {
  return (
    <div className="flex flex-col h-full bg-canvas">
      <div className="px-4 sm:px-8 pt-4 pl-14 sm:pl-16 flex items-center justify-between gap-4 flex-wrap">
        <h1 className="text-lg sm:text-xl font-bold tracking-tight text-white mb-2">{title}</h1>
        {header}
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

          <div className="absolute bottom-4 sm:bottom-6 left-1/2 -translate-x-1/2 z-[1000] max-w-[calc(100%-2rem)]">
            <div className="bg-surface-2/75 backdrop-blur px-4 sm:px-6 py-3 rounded-xl border border-slate-700 flex gap-6 sm:gap-8 items-center">
              <div>
                <div className="text-xs text-slate-400 font-medium whitespace-nowrap">Maletas en Tránsito</div>
                <div className="text-lg font-bold text-info mt-0.5">{bagsInTransit}</div>
              </div>
              <div>
                <div className="text-xs text-slate-400 font-medium whitespace-nowrap">Vuelos Activos</div>
                <div className="text-lg font-bold text-fuchsia-500 mt-0.5">{activeFlights}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
