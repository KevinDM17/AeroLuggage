import { useEffect, useMemo, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { Clock, Plus, SlidersHorizontal } from "lucide-react";
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
  const { simulationPanelData, setSimulationPanelData, ops, setTopBarActions, setTopBarInfo } = useOutletContext();

  const { simStatus, currentSimTimeUtc, mapAirports, liveMetrics, simulatedNowMs, hasActiveRun } =
    ops ?? {};

  const [pedidoOpen, setPedidoOpen] = useState(false);
  const [pedidoLoading, setPedidoLoading] = useState(false);
  const [showRouteLines, setShowRouteLines] = useState(true);

  useEffect(() => {
    if (!hasActiveRun) {
      setTopBarActions(null);
      setTopBarInfo(null);
      return;
    }
    setTopBarActions(
      <>
        <button
          type="button"
          onClick={() => setShowRouteLines((v) => !v)}
          className={`rounded px-2.5 py-1 text-xs font-medium whitespace-nowrap transition-colors ${
            showRouteLines
              ? "bg-blue-600/20 text-blue-400 border border-blue-500/40 hover:bg-blue-600/30"
              : "bg-white/5 text-slate-400 border border-white/10 hover:text-slate-200"
          }`}
        >
          Mostrar lineas
        </button>
        <button type="button" onClick={() => setPedidoOpen(true)} className="bg-blue-600 hover:bg-blue-500 text-white px-3 py-1 rounded text-xs font-medium leading-none transition-colors shrink-0 flex items-center gap-1.5">
          <Plus className="w-3.5 h-3.5" /> Agregar Pedido
        </button>
      </>
    );
    return () => setTopBarActions(null);
  }, [hasActiveRun, showRouteLines, setTopBarActions]);

  const limaTime = formatLimaTime(currentSimTimeUtc);

  useEffect(() => {
    if (!hasActiveRun || !limaTime) {
      setTopBarInfo(null);
      return;
    }
    setTopBarInfo(
      <div className="flex items-center gap-4 text-xs">
        <span className="text-slate-400">Lima, Peru</span>
        <span className="text-slate-200 tabular-nums">{limaTime.date}</span>
        <span className="text-slate-200 tabular-nums">{limaTime.time}</span>
      </div>
    );
    return () => setTopBarInfo(null);
  }, [hasActiveRun, limaTime?.date, limaTime?.time, setTopBarInfo]);

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

  const mapOverlays = [];

  return (
    <>
      <MapDashboard
        title={null}
        header={null}
        mapOverlays={mapOverlays}
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
