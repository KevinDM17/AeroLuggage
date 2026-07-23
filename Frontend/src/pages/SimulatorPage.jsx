import { useEffect, useMemo, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { SlidersHorizontal } from "lucide-react";
import MapDashboard from "../components/simulator/MapDashboard";
import PedidoModal from "../components/simulator/PedidoModal";
import { useToast } from "../components/ui/Toast";
import { procesarPedidoOperacionesDiaADia, obtenerPedidosOperacionesDiaADia, obtenerMaletasOperacionesDiaADia, obtenerRutasOperacionesDiaADia } from "../api/simulator";

export default function SimulatorPage() {
  const toast = useToast();
  const { simulationPanelData, setSimulationPanelData, ops, setTopBarActions } = useOutletContext();

  const { simStatus, currentSimTimeUtc, mapAirports, liveMetrics, simulatedNowMs, hasActiveRun } =
    ops ?? {};

  const [pedidoOpen, setPedidoOpen] = useState(false);
  const [pedidoLoading, setPedidoLoading] = useState(false);
  const [showRouteLines, setShowRouteLines] = useState(true);

  useEffect(() => {
    if (!hasActiveRun) {
      setTopBarActions(null);
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
      </>
    );
    return () => setTopBarActions(null);
  }, [hasActiveRun, showRouteLines, setTopBarActions]);

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
