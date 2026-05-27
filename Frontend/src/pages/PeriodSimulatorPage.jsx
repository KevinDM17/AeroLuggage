import { useEffect, useMemo, useRef, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { Play, Pause, Square, RotateCw } from "lucide-react";
import MapDashboard from "../components/simulator/MapDashboard";
import { usePolling } from "../hooks/usePolling";
import { useElapsedTimer } from "../hooks/useElapsedTimer";
import { useStompPublish, useStompSubscribe } from "../hooks/useStomp";
import { useToast } from "../components/ui/Toast";
import {
  iniciarSimulacionPeriodo,
  stopPeriodSim,
  getPeriodSimState,
} from "../api/simulator";
import { adaptAirport } from "../api/airports";
import { adaptFlightInstance } from "../api/flightInstances";
import { USE_MOCK } from "../api/client";
import {
  formatElapsedHMS,
  formatUtcDateTimeDisplay,
} from "../utils/formatting";

const PERIOD_DAYS = 5;
const DURATION_SIMULATED_DAY_MS = 10 * 60 * 1000;
/* 100 ms = 10 Hz para el display del reloj. La animacion del mapa NO depende
 * de este valor — corre a la tasa nativa del monitor leyendo simClockRef. */
const CLOCK_REFRESH_MS = 100;
const SIMULATED_DAY_MS = 24 * 60 * 60 * 1000;
/* Suavizado del re-anclaje: cada tick mezclamos 15% del valor del back con el
 * 85% del valor extrapolado del front. Asi los pequenos saltos de latencia
 * (jitter de red, GC pauses) se disuelven en ~7 ticks en vez de notarse como
 * un step visible. Mantenemos sync a largo plazo sin jumps abruptos. */
const CLOCK_REANCHOR_ALPHA = 0.15;

/* Diff estructural: si el contenido visual es identico al previo, preservamos
 * la referencia anterior para que `AirportMap` (memoizado) NO se re-renderice
 * y NO reconcilie sus markers/polylines.
 *
 * CLAVE: comparamos por TIER de color (verde/amarillo/rojo), no por valor
 * exacto de `used/capacity`. El back recalcula `capacidadUsada` en cada tick
 * dentro de `recalcularEstadoSesion`, asi que si comparamos por valor exacto
 * el diff casi nunca devuelve `true` y el optimo se cae. El tier solo cambia
 * cuando se cruza un umbral (60% o 85% de ocupacion) — eso si justifica
 * repintar. El resto del tiempo el avion sigue siendo "el mismo avion verde
 * en el mismo vuelo", la posicion ya la actualiza el rAF del layer leyendo
 * simClockRef. */
const loadTier = (used, capacity) => {
  const pct = capacity > 0 ? (used / capacity) * 100 : 0;
  if (pct >= 85) return 2;
  if (pct >= 60) return 1;
  return 0;
};

const flightsEqual = (a, b) => {
  if (a === b) return true;
  if (!a || !b || a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    const x = a[i];
    const y = b[i];
    if (
      x.id !== y.id ||
      x.origin !== y.origin ||
      x.dest !== y.dest ||
      x.depTime !== y.depTime ||
      x.arrTime !== y.arrTime ||
      loadTier(x.used, x.capacity) !== loadTier(y.used, y.capacity)
    ) {
      return false;
    }
  }
  return true;
};

const airportsEqual = (a, b) => {
  if (a === b) return true;
  if (!a || !b || a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    const x = a[i];
    const y = b[i];
    if (
      x.iata !== y.iata ||
      x.lat !== y.lat ||
      x.lng !== y.lng ||
      loadTier(x.used, x.capacity) !== loadTier(y.used, y.capacity)
    ) {
      return false;
    }
  }
  return true;
};

const ESTADO_BACK_A_LOCAL = {
  INICIADA: "running",
  REANUDADA: "running",
  PAUSADA: "paused",
  DETENIDA: "idle",
  FINALIZADA: "done",
};

const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

const getSimulationProgress = (
  elapsedRealMs,
  totalDays,
  simulatedDayDurationMs,
) => {
  if (totalDays <= 0 || simulatedDayDurationMs <= 0) return 0;
  const totalRealDurationMs = totalDays * simulatedDayDurationMs;
  return Math.min(
    100,
    Math.max(0, (elapsedRealMs / totalRealDurationMs) * 100),
  );
};

const getDisplayedDay = (progress, hasActiveRun) => {
  if (!hasActiveRun) return 0;
  return clamp(Math.ceil((progress / 100) * PERIOD_DAYS), 1, PERIOD_DAYS);
};

export default function PeriodSimulatorPage() {
  const toast = useToast();
  const publish = useStompPublish();
  const { setSimulationPanelData } = useOutletContext();

  const [startDate, setStartDate] = useState(() =>
    new Date().toISOString().slice(0, 10),
  );
  const [sessionId, setSessionId] = useState(null);
  const [simStatus, setSimStatus] = useState("idle");
  const [runId, setRunId] = useState(0);
  const [lastMockState, setLastMockState] = useState(null);
  const [mapAirports, setMapAirports] = useState([]);
  const [mapFlights, setMapFlights] = useState([]);
  const [showRouteLines, setShowRouteLines] = useState(true);
  const [currentSimTimeUtc, setCurrentSimTimeUtc] = useState(null);
  const [simulatedDayDurationMs, setSimulatedDayDurationMs] = useState(
    DURATION_SIMULATED_DAY_MS,
  );
  const [tickBaseSimTimeUtc, setTickBaseSimTimeUtc] = useState(null);
  const [tickReceiptElapsedMs, setTickReceiptElapsedMs] = useState(0);

  /* Reloj simulado en un ref mutable. AirportMap lee de aca dentro de su
   * requestAnimationFrame para extrapolar la posicion de los aviones SIN
   * disparar re-renders. El ref tiene identidad estable, asi que AirportMap
   * (memoizado) no se vuelve a renderizar 30 fps por culpa del reloj — solo
   * cuando llegan datos nuevos (ticks). */
  const simClockRef = useRef({
    baseSimMs: null,
    baseRealMs: 0,
    speed: 1,
    running: false,
  });

  useEffect(() => {
    setSimulationPanelData({
      airports: [],
      flights: [],
      orders: [],
      bags: [],
      routes: [],
      loaded: false,
    });
    return () => {
      setSimulationPanelData({
        airports: [],
        flights: [],
        orders: [],
        bags: [],
        routes: [],
        loaded: false,
      });
    };
  }, [setSimulationPanelData]);

  const tickTopic =
    !USE_MOCK && sessionId ? `/topic/simulacion/${sessionId}` : null;
  const statusTopic =
    !USE_MOCK && sessionId ? `/topic/simulacion/${sessionId}/estado` : null;

  const { data: tick } = useStompSubscribe(tickTopic);
  const { data: estadoMessage } = useStompSubscribe(statusTopic);

  const { data: mockState } = usePolling(getPeriodSimState, {
    enabled: USE_MOCK && simStatus === "running",
    intervalMs: 1000,
  });

  const executionElapsedMs = useElapsedTimer(
    simStatus,
    runId,
    CLOCK_REFRESH_MS,
  );
  const hasActiveRun =
    simStatus === "running" || simStatus === "paused" || simStatus === "done";

  /* Cuando el usuario pausa/reanuda, freezamos o reanclamos el reloj sin
   * tocar el ref desde el cuerpo del render. */
  useEffect(() => {
    const clock = simClockRef.current;
    if (clock.baseSimMs == null) return;
    if (simStatus === "running") {
      // Reancla la base real, conservando el simNow visible hasta ahora.
      const wasPausedAt = clock.baseSimMs;
      simClockRef.current = {
        baseSimMs: wasPausedAt,
        baseRealMs: performance.now(),
        speed: clock.speed,
        running: true,
      };
    } else {
      // Congelamos baseSimMs en el simNow extrapolado al momento de pausar.
      const frozen = clock.running
        ? clock.baseSimMs +
          (performance.now() - clock.baseRealMs) * clock.speed
        : clock.baseSimMs;
      simClockRef.current = {
        baseSimMs: frozen,
        baseRealMs: performance.now(),
        speed: clock.speed,
        running: false,
      };
    }
  }, [simStatus]);

  useEffect(() => {
    if (USE_MOCK) {
      if (!mockState || mockState.status === simStatus) return;
      setLastMockState(mockState);
      setSimStatus(mockState.status);
      if (mockState.status === "done") {
        toast.push({
          type: "success",
          title: "Simulación completada",
          message: `Periodo de ${PERIOD_DAYS} días procesado.`,
        });
      }
      return;
    }
    if (!estadoMessage) return;
    const local = ESTADO_BACK_A_LOCAL[estadoMessage.estado] ?? simStatus;
    if (local !== simStatus) setSimStatus(local);
    if (estadoMessage.estado === "FINALIZADA") {
      toast.push({
        type: "success",
        title: "Simulación completada",
        message: estadoMessage.mensaje,
      });
    }
    if (estadoMessage.estado === "DETENIDA") {
      setSessionId(null);
    }
  }, [mockState, estadoMessage]); // eslint-disable-line react-hooks/exhaustive-deps

  const progress = useMemo(() => {
    if (USE_MOCK) return mockState?.progress ?? lastMockState?.progress ?? 0;
    return getSimulationProgress(
      executionElapsedMs,
      PERIOD_DAYS,
      simulatedDayDurationMs,
    );
  }, [executionElapsedMs, mockState, lastMockState, simulatedDayDurationMs]);

  useEffect(() => {
    if (!tick?.currentSimTimeUtc) return;
    setCurrentSimTimeUtc(tick.currentSimTimeUtc);
    setTickBaseSimTimeUtc(tick.currentSimTimeUtc);
    setTickReceiptElapsedMs(executionElapsedMs);

    /* Re-anclamos el reloj mutable cada vez que llega un tick, pero
     * suavemente: mezclamos el valor del back con el extrapolado del front
     * (CLOCK_REANCHOR_ALPHA) para no notar los pequenos saltos de latencia.
     * El rAF de AirportMap lee de aca y se mantiene continuo. */
    const parsedTickMs = Date.parse(`${tick.currentSimTimeUtc}Z`);
    if (Number.isFinite(parsedTickMs)) {
      const speed = SIMULATED_DAY_MS / simulatedDayDurationMs;
      const nowReal = performance.now();
      const prevClock = simClockRef.current;
      let nextBaseSimMs;
      if (prevClock.baseSimMs == null) {
        // Primer tick: anclamos directo al valor del back.
        nextBaseSimMs = parsedTickMs;
      } else {
        // Calculamos donde estaba extrapolando el front justo antes del tick
        // y avanzamos un 15% hacia el valor reportado por el back.
        const frontExtrap = prevClock.running
          ? prevClock.baseSimMs +
            (nowReal - prevClock.baseRealMs) * prevClock.speed
          : prevClock.baseSimMs;
        nextBaseSimMs =
          frontExtrap +
          (parsedTickMs - frontExtrap) * CLOCK_REANCHOR_ALPHA;
      }
      simClockRef.current = {
        baseSimMs: nextBaseSimMs,
        baseRealMs: nowReal,
        speed,
        running: simStatus === "running",
      };
    }
    if (Array.isArray(tick.vuelosInstancia)) {
      const adaptedFlights = tick.vuelosInstancia.map(adaptFlightInstance);
      const adaptedAirports = Array.isArray(tick.aeropuertos)
        ? tick.aeropuertos.map(adaptAirport)
        : null;
      // Diff estructural: preservamos la referencia anterior si el contenido
      // es identico, asi AirportMap (memoizado) NO se re-renderiza.
      setMapFlights((prev) =>
        flightsEqual(prev, adaptedFlights) ? prev : adaptedFlights,
      );
      if (adaptedAirports) {
        setMapAirports((prev) =>
          airportsEqual(prev, adaptedAirports) ? prev : adaptedAirports,
        );
      }
      setSimulationPanelData((prev) => ({
        ...prev,
        ...(adaptedAirports && { airports: adaptedAirports }),
        flights: adaptedFlights,
        orders: Array.isArray(tick.pedidos) ? tick.pedidos : prev.orders,
        bags: Array.isArray(tick.maletas) ? tick.maletas : prev.bags,
        routes: Array.isArray(tick.rutas) ? tick.rutas : prev.routes,
        loaded: true,
      }));
    } else {
      setSimulationPanelData((prev) => ({
        ...prev,
        orders: Array.isArray(tick.pedidos) ? tick.pedidos : prev.orders,
        bags: Array.isArray(tick.maletas) ? tick.maletas : prev.bags,
        routes: Array.isArray(tick.rutas) ? tick.rutas : prev.routes,
        loaded: true,
      }));
    }
  }, [tick, executionElapsedMs]); // eslint-disable-line react-hooks/exhaustive-deps

  const simulatedNowMs = useMemo(() => {
    const base = tickBaseSimTimeUtc ?? currentSimTimeUtc;
    if (!base) return null;
    const parsed = Date.parse(`${base}Z`);
    if (!Number.isFinite(parsed)) return null;
    if (simStatus !== "running") return parsed;

    const elapsedSinceTickMs = Math.max(
      0,
      executionElapsedMs - tickReceiptElapsedMs,
    );
    const simulatedDeltaMs =
      elapsedSinceTickMs * (SIMULATED_DAY_MS / simulatedDayDurationMs);
    return parsed + simulatedDeltaMs;
  }, [
    tickBaseSimTimeUtc,
    currentSimTimeUtc,
    simStatus,
    executionElapsedMs,
    tickReceiptElapsedMs,
    simulatedDayDurationMs,
  ]);

  const simulationClock = useMemo(() => {
    if (simulatedNowMs === null || Number.isNaN(simulatedNowMs)) {
      return formatUtcDateTimeDisplay(null);
    }
    return formatUtcDateTimeDisplay(new Date(simulatedNowMs));
  }, [simulatedNowMs]);

  const liveMetrics = useMemo(() => {
    if (USE_MOCK) {
      return mockState
        ? {
            bagsInTransit: mockState.bagsInTransit ?? 0,
            bagsDelivered: mockState.bagsDelivered ?? 0,
            bagsUnassigned: mockState.bagsUnassigned ?? 0,
            activeFlights: mockState.activeFlights ?? 0,
            freeCapacityPct: mockState.freeCapacityPct ?? 0,
          }
        : undefined;
    }
    if (!tick) return undefined;
    return {
      bagsInTransit: tick.maletasEnTransito ?? 0,
      bagsDelivered:
        tick.maletasEntregadas ?? tick.maletasEntregadasATiempo ?? 0,
      bagsUnassigned: tick.maletasNoAsignadas ?? tick.maletasSinRuta ?? 0,
      activeFlights: tick.vuelosActivos ?? 0,
      freeCapacityPct: tick.capacidadLibrePct ?? 0,
    };
  }, [tick, mockState]);

  const handleStart = async () => {
    try {
      const result = await iniciarSimulacionPeriodo({
        fechaInicio: startDate,
        totalDias: PERIOD_DAYS,
        duracionDiaSimuladoMs: DURATION_SIMULATED_DAY_MS,
      });
      const adaptedAirports = Array.isArray(result.aeropuertos)
        ? result.aeropuertos.map(adaptAirport)
        : [];
      const adaptedFlights = Array.isArray(result.vuelosInstancia)
        ? result.vuelosInstancia.map(adaptFlightInstance)
        : [];
      setSessionId(result.sessionId ?? null);
      setLastMockState(null);
      const initialSimUtc = result.currentSimTimeUtc ?? `${startDate}T00:00:00`;
      const initialDurationMs =
        result.duracionDiaSimuladoMs ?? DURATION_SIMULATED_DAY_MS;
      setCurrentSimTimeUtc(initialSimUtc);
      setTickBaseSimTimeUtc(initialSimUtc);
      setTickReceiptElapsedMs(0);
      setSimulatedDayDurationMs(initialDurationMs);

      /* Anclar el reloj mutable que consume AirportMap. */
      const initialSimMs = Date.parse(`${initialSimUtc}Z`);
      simClockRef.current = {
        baseSimMs: Number.isFinite(initialSimMs) ? initialSimMs : null,
        baseRealMs: performance.now(),
        speed: SIMULATED_DAY_MS / initialDurationMs,
        running: true,
      };
      setMapAirports(adaptedAirports);
      setMapFlights(adaptedFlights);
      setSimulationPanelData({
        airports: adaptedAirports,
        flights: adaptedFlights,
        orders: [],
        bags: [],
        routes: [],
        loaded: true,
      });
      setRunId((current) => current + 1);
      setSimStatus(
        USE_MOCK
          ? (result.status ?? "running")
          : (ESTADO_BACK_A_LOCAL[result.estado] ?? "running"),
      );
      toast.push({
        type: "info",
        title: "Simulación iniciada",
        message: `Inicio: ${startDate} · ${PERIOD_DAYS} días`,
      });
    } catch (err) {
      toast.push({
        type: "error",
        title: "No se pudo iniciar",
        message: err.message,
      });
    }
  };

  const handlePause = async () => {
    if (USE_MOCK) {
      toast.push({ type: "warning", title: "Pausa no soportada en modo mock" });
      return;
    }
    try {
      await publish("/app/simulacion/periodo/pausar", { sessionId });
    } catch (err) {
      toast.push({
        type: "error",
        title: "No se pudo pausar",
        message: err.message,
      });
    }
  };

  const handleResume = async () => {
    if (USE_MOCK) return;
    try {
      await publish("/app/simulacion/periodo/reanudar", { sessionId });
    } catch (err) {
      toast.push({
        type: "error",
        title: "No se pudo reanudar",
        message: err.message,
      });
    }
  };

  const handleStop = async () => {
    try {
      if (USE_MOCK) {
        await stopPeriodSim();
      } else {
        await publish("/app/simulacion/periodo/detener", { sessionId });
      }
      setSimStatus("idle");
      setSessionId(null);
      setCurrentSimTimeUtc(null);
      setTickBaseSimTimeUtc(null);
      setTickReceiptElapsedMs(0);
      setMapAirports([]);
      setMapFlights([]);
      simClockRef.current = {
        baseSimMs: null,
        baseRealMs: 0,
        speed: 1,
        running: false,
      };
      setSimulationPanelData({
        airports: [],
        flights: [],
        orders: [],
        bags: [],
        routes: [],
        loaded: false,
      });
      toast.push({ type: "warning", title: "Simulación detenida" });
    } catch (err) {
      toast.push({
        type: "error",
        title: "No se pudo detener",
        message: err.message,
      });
    }
  };

  const displayedDay = getDisplayedDay(progress, hasActiveRun);

  const mapOverlay = hasActiveRun ? (
    <div className="bg-surface-2/85 backdrop-blur border border-slate-700 shadow-[0_12px_35px_rgba(0,0,0,0.45)] rounded-xl px-4 py-3 flex items-center justify-center gap-6">
      <div>
        <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">
          Cronometro
        </div>
        <div className="text-lg font-bold text-slate-100 tabular-nums">
          {formatElapsedHMS(executionElapsedMs)}
        </div>
      </div>
      <div className="h-9 w-px bg-slate-700" />
      <div>
        <div className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">
          Fecha/hora simulada
        </div>
        <div className="text-lg font-bold text-info tabular-nums">
          {simulationClock.date} - {simulationClock.time}{" "}
          {simulationClock.timeZone}
          {tick?.currentWindowId
            ? ` - ${tick.currentWindowId}`
            : displayedDay
              ? ` - dia ${displayedDay}/${PERIOD_DAYS}`
              : ""}
        </div>
      </div>
    </div>
  ) : null;

  const header = (
    <div className="flex items-center gap-3 flex-wrap">
      <div className="flex flex-col">
        <label
          htmlFor="period-start"
          className="text-[10px] text-slate-400 uppercase tracking-wider font-medium"
        >
          Fecha de inicio
        </label>
        <input
          id="period-start"
          type="date"
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
          disabled={simStatus === "running" || simStatus === "paused"}
          className="bg-surface-2 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-slate-200 outline-none focus:border-blue-500 disabled:opacity-50"
        />
      </div>

      <div className="flex flex-col">
        <span className="text-[10px] text-slate-400 uppercase tracking-wider font-medium">
          Duración
        </span>
        <span className="px-3 py-1.5 bg-surface-2 border border-slate-700 rounded-lg text-sm font-bold text-success">
          {PERIOD_DAYS} días
        </span>
      </div>

      <label className="flex items-center gap-2 self-end px-3 py-2 bg-surface-2 border border-slate-700 rounded-lg text-sm text-slate-200 cursor-pointer select-none">
        <input
          type="checkbox"
          checked={showRouteLines}
          onChange={(e) => setShowRouteLines(e.target.checked)}
          className="h-4 w-4 rounded border-slate-600 bg-surface-1 text-blue-500 focus:ring-blue-500"
        />
        <span>Mostrar líneas</span>
      </label>

      {simStatus === "idle" || simStatus === "done" ? (
        <button
          type="button"
          onClick={handleStart}
          className="self-end bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors"
        >
          <Play className="w-4 h-4" /> Ejecutar
        </button>
      ) : simStatus === "paused" ? (
        <>
          <button
            type="button"
            onClick={handleResume}
            className="self-end bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors"
          >
            <RotateCw className="w-4 h-4" /> Reanudar
          </button>
          <button
            type="button"
            onClick={handleStop}
            className="self-end bg-danger/10 hover:bg-danger/20 text-danger border border-danger/40 px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors"
          >
            <Square className="w-4 h-4" /> Detener
          </button>
        </>
      ) : (
        <>
          {!USE_MOCK && (
            <button
              type="button"
              onClick={handlePause}
              className="self-end bg-warning/10 hover:bg-warning/20 text-warning border border-warning/40 px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors"
            >
              <Pause className="w-4 h-4" /> Pausar
            </button>
          )}
          <button
            type="button"
            onClick={handleStop}
            className="self-end bg-danger/10 hover:bg-danger/20 text-danger border border-danger/40 px-4 py-2 rounded-lg flex items-center gap-2 font-medium text-sm transition-colors"
          >
            <Square className="w-4 h-4" /> Detener
          </button>
        </>
      )}

      {simStatus !== "idle" && (
        <div className="flex flex-col w-44 self-end">
          <div className="flex justify-between text-[10px] text-slate-400 mb-1">
            <span>Progreso</span>
            <span>{progress.toFixed(0)}%</span>
          </div>
          <div className="h-2 bg-surface-2 rounded-full overflow-hidden border border-slate-700">
            <div
              className={`h-full transition-all ${simStatus === "done" ? "bg-success" : simStatus === "paused" ? "bg-warning" : "bg-info"}`}
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
      )}
    </div>
  );

  return (
    <MapDashboard
      title={`Simulación de Periodo · ${PERIOD_DAYS} días`}
      header={header}
      mapOverlay={mapOverlay}
      showMapClock={false}
      showMapFlights={hasActiveRun}
      showMapRouteLines={showRouteLines}
      animateMapFlights={simStatus === "running"}
      mapAutoload={false}
      airports={mapAirports}
      flights={mapFlights}
      simClockRef={simClockRef}
      date={simulationClock.date}
      time={`${simulationClock.time} ${simulationClock.timeZone}`}
      metrics={liveMetrics}
    />
  );
}
