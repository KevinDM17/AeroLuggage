import { useMemo, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { Plus } from "lucide-react";
import MapDashboard from "../components/simulator/MapDashboard";
import PedidoModal from "../components/simulator/PedidoModal";
import { useToast } from "../components/ui/Toast";
import { procesarPedidoOperacionesDiaADia, obtenerPedidosOperacionesDiaADia, obtenerMaletasOperacionesDiaADia, obtenerRutasOperacionesDiaADia } from "../api/simulator";

function formatLimaTime(utcIsoString) {
  if (!utcIsoString) return { date: "--", time: "--:--:--" };
  const d = new Date(utcIsoString.endsWith("Z") ? utcIsoString : utcIsoString + "Z");
  if (Number.isNaN(d.getTime())) return { date: "--", time: "--:--:--" };
  return {
    date: d.toLocaleDateString("es-PE", { timeZone: "America/Lima", day: "2-digit", month: "2-digit", year: "numeric" }),
    time: d.toLocaleTimeString("es-PE", { timeZone: "America/Lima", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false }),
  };
}

export default function SimulatorPage() {
  const toast = useToast();
  const { simulationPanelData, setSimulationPanelData, ops } = useOutletContext();

  const { simStatus, currentSimTimeUtc, mapAirports, liveMetrics, simulatedNowMs, hasActiveRun } =
    ops ?? {};

  const [pedidoOpen, setPedidoOpen] = useState(false);
  const [pedidoLoading, setPedidoLoading] = useState(false);
  const [showRouteLines, setShowRouteLines] = useState(true);

  const normalizeFlightStatus = (status) =>
    String(status ?? "").trim().toUpperCase().replace(/\s+/g, "_");

  const mapVisibleFlights = useMemo(() => {
    if (simStatus !== "running") return [];
    if (!simulationPanelData?.flights) return [];
    const out = [];
    for (const flight of simulationPanelData.flights.values()) {
      const status = flight.status;
      if (normalizeFlightStatus(status) !== "EN_PROGRESO") continue;
      const salidaMs = Date.parse(`${flight.depTime}Z`);
      const llegadaMs = Date.parse(`${flight.arrTime}Z`);
      if (!Number.isFinite(salidaMs) || !Number.isFinite(llegadaMs)) continue;
      out.push({
        id: flight.idVueloInstancia ?? flight.id,
        origin: flight.origin,
        dest: flight.dest,
        depTime: flight.depTime,
        arrTime: flight.arrTime,
        used: flight.used,
        capacity: flight.capacity,
      });
    }
    return out;
  }, [simulationPanelData?.flights, simStatus]);

  const handlePedidoSubmit = async (pedido) => {
    setPedidoLoading(true);
    try {
      await procesarPedidoOperacionesDiaADia(pedido);
      const [pedidosData, maletasData, rutasData] = await Promise.all([
        obtenerPedidosOperacionesDiaADia().catch(() => []),
        obtenerMaletasOperacionesDiaADia().catch(() => []),
        obtenerRutasOperacionesDiaADia().catch(() => []),
      ]);
      setSimulationPanelData((prev) => {
        const orders = new Map(prev.orders);
        for (const o of pedidosData ?? []) orders.set(o.id ?? o.idPedido, o);
        const bags = new Map(prev.bags);
        for (const b of maletasData ?? []) bags.set(b.idMaleta, { ...b, ticksAusente: 0 });
        const routes = new Map(prev.routes);
        for (const r of rutasData ?? []) routes.set(r.idRuta, { ...r, ticksAusente: 0 });
        return { ...prev, orders, bags, routes };
      });
      setPedidoOpen(false);
      toast.push({ type: "success", title: "Pedido enviado", message: `${pedido.cantidadMaletas} maletas desde ${pedido.idAeropuertoOrigen} a ${pedido.idAeropuertoDestino}` });
    } catch (err) {
      toast.push({ type: "error", title: "Error al enviar pedido", message: err.message });
    } finally {
      setPedidoLoading(false);
    }
  };

  const limaTime = formatLimaTime(currentSimTimeUtc);

  const mapOverlay = hasActiveRun ? (
    <div className="bg-surface-2/85 backdrop-blur border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-4 py-3 flex items-center justify-center gap-5">
      <div>
        <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">Fecha Lima, Peru (GMT-5)</div>
        <div className="text-lg font-bold text-info tabular-nums">{limaTime.date}</div>
      </div>
      <div className="border-x-1 px-6">
        <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">Hora Lima, Peru (GMT-5)</div>
        <div className="text-lg font-bold text-info tabular-nums">{limaTime.time}</div>
      </div>
      <div className="h-9 w-px bg-slate-700 shrink-0" />
      <button
        type="button"
        onClick={() => setShowRouteLines((v) => !v)}
        className={`rounded-lg px-3 py-1.5 text-sm font-medium whitespace-nowrap transition-colors ${
          showRouteLines
            ? "bg-blue-600/20 text-blue-400 border border-blue-500/40 hover:bg-blue-600/30"
            : "bg-surface-2 text-slate-400 border border-slate-700 hover:text-slate-200"
        }`}
      >
        Mostrar lineas
      </button>
      <div className="h-9 w-px bg-slate-700 shrink-0" />
      <button type="button" onClick={() => setPedidoOpen(true)} className="self-center bg-blue-600 hover:bg-blue-500 text-white px-5 py-2.5 rounded-lg flex items-center gap-2 font-medium text-sm leading-none transition-colors shrink-0">
        <Plus className="w-4 h-4" /> Agregar Pedido
      </button>
    </div>
  ) : null;

  return (
    <>
      <MapDashboard
        title="Operaciones dia a dia · Tiempo real"
        header={null}
        mapOverlay={mapOverlay}
        showMapClock={false}
        showMapFlights={hasActiveRun}
        showMapRouteLines={hasActiveRun && showRouteLines}
        animateMapFlights={simStatus === "running"}
        mapAutoload={false}
        airports={mapAirports ?? []}
        flights={mapVisibleFlights}
        simulatedNowMs={simulatedNowMs}
        simulatedDayDurationMs={86400000}
        metrics={liveMetrics}
      />
      <PedidoModal
        open={pedidoOpen}
        airports={mapAirports ?? []}
        onClose={() => setPedidoOpen(false)}
        onSubmit={handlePedidoSubmit}
        loading={pedidoLoading}
      />
    </>
  );
}
