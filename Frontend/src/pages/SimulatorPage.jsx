import { useEffect, useMemo, useRef, useState } from "react";
import { useOutletContext } from "react-router-dom";
import MapDashboard from "../components/simulator/MapDashboard";
import { useStompPublish, useStompSubscribe } from "../hooks/useStomp";
import { usePolling } from "../hooks/usePolling";
import { iniciarSimulacionPeriodo, getPeriodSimState } from "../api/simulator";
import { USE_MOCK } from "../api/client";

/**
 * "Día a Día" — monitoreo en vivo de operaciones reales.
 *
 * Por acuerdo con back: reutilizamos la sim de periodo con totalDias muy alto
 * para que los ticks fluyan permanentemente. El front auto-arranca la sesión
 * al montar y la detiene al desmontar. Cero polling: los KPIs llegan por WS.
 */
const TOTAL_DIAS_VIVO = 9999;
const TICK_MS = 500;

export default function SimulatorPage() {
  const publish = useStompPublish();
  const { resetSimulationPanelData } = useOutletContext();
  const [sessionId, setSessionId] = useState(null);
  const sessionIdRef = useRef(null);

  const clearSimulationData = () => {
    resetSimulationPanelData();
  };

  /* Auto-iniciar sesión "día a día" en el back al montar */
  // useEffect(() => {
  //   let cancelled = false;
  //   clearSimulationData();
  //   (async () => {
  //     try {
  //       const today = new Date().toISOString().slice(0, 10);
  //       const result = await iniciarSimulacionPeriodo({
  //         fechaInicio: today,
  //         totalDias: TOTAL_DIAS_VIVO,
  //         intervaloTickMs: TICK_MS,
  //       });
  //       if (cancelled) return;
  //       const sid = result?.sessionId ?? null;
  //       sessionIdRef.current = sid;
  //       setSessionId(sid);
  //     } catch (e) {
    //       // ignore
  //     }
  //   })();
  //
  //   return () => {
  //     cancelled = true;
  //     const sid = sessionIdRef.current;
  //     if (sid && !USE_MOCK) {
  //       publish("/app/simulacion/periodo/detener", { sessionId: sid }).catch(() => {});
  //     }
  //   };
  //   // eslint-disable-next-line react-hooks/exhaustive-deps
  // }, []);

  /* Modo real: ticks por WS */
  const tickTopic = !USE_MOCK && sessionId ? `/topic/simulacion/${sessionId}` : null;
  const { data: tick } = useStompSubscribe(tickTopic);

  /* Modo mock: leemos el estado mock del simulador con un poll corto solo en dev */
  const { data: mockState } = usePolling(getPeriodSimState, {
    enabled: USE_MOCK,
    intervalMs: TICK_MS,
  });

  const metrics = useMemo(() => {
    if (USE_MOCK) {
      return {
        bagsInTransit:   mockState?.bagsInTransit   ?? 0,
        bagsDelivered:   mockState?.bagsDelivered   ?? 0,
        bagsUnassigned:  mockState?.bagsUnassigned  ?? 0,
        activeFlights:   mockState?.activeFlights   ?? 0,
        freeCapacityPct: mockState?.freeCapacityPct ?? 0,
      };
    }
    if (!tick) return undefined;
    return {
      bagsInTransit:   tick.maletasEnTransito ?? 0,
      bagsDelivered:   tick.maletasEntregadas ?? tick.maletasEntregadasATiempo ?? 0,
      bagsUnassigned:  tick.maletasNoAsignadas ?? tick.maletasSinRuta ?? 0,
      activeFlights:   tick.vuelosActivos ?? 0,
      freeCapacityPct: tick.capacidadLibrePct ?? 0,
    };
  }, [tick, mockState]);

  const clock = useMemo(() => {
    const raw = tick?.fechaHoraSimulada ?? tick?.fechaSimulada;
    if (!raw) return { date: undefined, time: undefined };
    const value = String(raw);
    if (value.includes("T")) {
      const [d, t = ""] = value.split("T");
      return { date: d, time: t.slice(0, 8) };
    }
    return { date: value, time: tick?.horaSimulada ?? undefined };
  }, [tick]);

  return (
    <MapDashboard
      title="Visualización de Operaciones día a día"
      date={clock.date}
      time={clock.time}
      metrics={metrics}
    />
  );
}
