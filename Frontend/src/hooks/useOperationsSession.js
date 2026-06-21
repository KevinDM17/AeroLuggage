import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useStompSubscribe } from "./useStomp";
import { usePolling } from "./usePolling";
import { useElapsedTimer } from "./useElapsedTimer";
import { isStompConnected, subscribeToReconnects } from "../api/stomp";
import { USE_MOCK } from "../api/client";
import {
  iniciarSimulacionDiaADia,
  obtenerAeropuertosDiaADia,
  obtenerVuelosDiaADia,
  obtenerMaletasDiaADia,
  obtenerRutasDiaADia,
  obtenerEstadoDiaADia,
  obtenerEstadoActualDiaADia,
  onSessionChange,
  confirmarConexionDiaADia,
  obtenerVuelosNuevosDiaADia,
} from "../api/simulator";
import { adaptAirport } from "../api/airports";
import { adaptFlightInstance } from "../api/flightInstances";
import { getMockDiaADiaState } from "../api/mock";

const ENUM_RUTA = ["PLANIFICADA", "ACTIVA", "COMPLETADA", "REPLANIFICADA"];

const REFRESH_MS = 500;

const ESTADO_BACK_A_LOCAL = {
  INICIADA: "running",
  DETENIDA: "idle",
  PEDIDO_PROCESADO: "running",
};

const emptyMetrics = {
  bagsInTransit: 0,
  bagsDelivered: 0,
  activeFlights: 0,
  airportCapacityPct: 0,
  flightCapacityPct: 0,
};

function updateEstadosOnly(oldMap, stateMap, enumArr, statusField, extraFields) {
  if (Object.keys(stateMap).length === 0) return oldMap;
  const updated = new Map(oldMap);
  for (const [id, entity] of updated) {
    const st = stateMap[id];
    if (st) {
      updated.set(id, {
        ...entity,
        [statusField]: enumArr[st.e],
        ...(extraFields ? extraFields(st, entity) : {}),
      });
    }
  }
  return updated;
}

function getUpdatedFlightOccupancy(st, flight) {
  if (flight.capacity <= 0 || !Number.isFinite(Number(st.cap))) return flight.used ?? 0;
  return Math.max(0, flight.capacity - Number(st.cap));
}

export function useOperationsSession({ enabled, setSimulationPanelData, resetSimulationPanelData, toast }) {
  const [sessionId, setSessionId] = useState(null);
  const [simStatus, setSimStatus] = useState("idle");
  const [runId, setRunId] = useState(0);
  const [currentSimTimeUtc, setCurrentSimTimeUtc] = useState(null);
  const [mapAirports, setMapAirports] = useState([]);
  const [datosBaseCargados, setDatosBaseCargados] = useState(false);
  const datosBaseCargadosRef = useRef(false);

  const sessionIdRef = useRef(null);
  sessionIdRef.current = sessionId;
  const ultimoTickRef = useRef(0);
  const tickCountRef = useRef(0);
  const reiniciarTokenRef = useRef(0);
  const startSimMsRef = useRef(null);
  const flightAggRef = useRef({ totalCap: 0, totalUsed: 0 });
  const [flightAggVersion, setFlightAggVersion] = useState(0);

  const tickTopic = !USE_MOCK && sessionId ? `/topic/operations/${sessionId}` : null;
  const statusTopic = !USE_MOCK && sessionId ? `/topic/operations/${sessionId}/estado` : null;

  const { data: tick } = useStompSubscribe(tickTopic);
  const { data: estadoMessage } = useStompSubscribe(statusTopic);

  const { data: mockState } = usePolling(getMockDiaADiaState, {
    enabled: USE_MOCK && simStatus === "running",
    intervalMs: REFRESH_MS,
  });

  const executionElapsedMs = useElapsedTimer(simStatus, runId, REFRESH_MS);
  const hasActiveRun = simStatus === "running";

  const simulatedNowMs = useMemo(() => {
    const startMs = startSimMsRef.current;
    if (startMs == null) {
      if (!currentSimTimeUtc) return null;
      const parsed = Date.parse(`${currentSimTimeUtc}Z`);
      return Number.isFinite(parsed) ? parsed : null;
    }
    if (simStatus !== "running") {
      if (!currentSimTimeUtc) return startMs;
      const parsed = Date.parse(`${currentSimTimeUtc}Z`);
      return Number.isFinite(parsed) ? startMs : startMs;
    }
    return startMs + executionElapsedMs;
  }, [currentSimTimeUtc, simStatus, executionElapsedMs]);

  useEffect(() => {
    return subscribeToReconnects(() => {
      reiniciarTokenRef.current++;
    });
  }, []);

  const inicializarSesion = useCallback(
    async (esInicial) => {
      try {
        const result = await iniciarSimulacionDiaADia();
        const newSessionId = result.sessionId;
        setSessionId(newSessionId);
        setRunId((c) => c + 1);

        let aeropuertosData, vuelosData;
        try {
          const snapshot = await obtenerEstadoActualDiaADia();
          if (snapshot && snapshot.activa) {
            aeropuertosData = snapshot.aeropuertos;
            vuelosData = snapshot.vuelos;
          }
        } catch {
          // fallback
        }
        if (!aeropuertosData || !vuelosData) {
          [aeropuertosData, vuelosData] = await Promise.all([
            obtenerAeropuertosDiaADia(),
            obtenerVuelosDiaADia(),
          ]);
        }
        const adaptedAirports = Array.isArray(aeropuertosData)
          ? aeropuertosData.map(adaptAirport)
          : [];
        setMapAirports(adaptedAirports);

        const adaptedFlights = (vuelosData ?? []).map(adaptFlightInstance);
        const flights = new Map();
        for (const f of adaptedFlights)
          flights.set(f.idVueloInstancia ?? f.id, { ...f, ticksAusente: 0 });

        const [rutasData, maletasData] = await Promise.all([
          obtenerRutasDiaADia().catch(() => []),
          obtenerMaletasDiaADia().catch(() => []),
        ]);

        const routes = new Map();
        for (const r of rutasData) routes.set(r.idRuta, { ...r, ticksAusente: 0 });
        const bags = new Map();
        for (const m of maletasData) bags.set(m.idMaleta, { ...m, ticksAusente: 0 });

        if (!esInicial) {
          setSimulationPanelData((prev) => ({
            ...prev,
            airports: adaptedAirports,
            flights,
            routes,
            bags,
            sessionId: newSessionId,
          }));
        } else {
          setSimulationPanelData({
            airports: adaptedAirports,
            flights,
            orders: new Map(),
            bags,
            routes,
            sessionId: newSessionId,
            loaded: true,
          });
        }
        setSimStatus("running");
        setDatosBaseCargados(true);
        datosBaseCargadosRef.current = true;
        ultimoTickRef.current = Date.now();
        if (!USE_MOCK) {
          await confirmarConexionDiaADia();
        }
        if (esInicial && toast) {
          toast.push({
            type: "info",
            title: "Operaciones dia a dia",
            message: "Simulacion iniciada en tiempo real",
          });
        }
      } catch (err) {
        if (esInicial && toast) {
          toast.push({ type: "error", title: "No se pudo iniciar", message: err.message });
        }
      }
    },
    [setSimulationPanelData, toast],
  );

  useEffect(() => {
    if (!enabled) return;
    resetSimulationPanelData();
    inicializarSesion(true);
  }, [enabled]);

  useEffect(() => {
    if (!enabled || USE_MOCK) return;
    const interval = setInterval(async () => {
      if (!hasActiveRun || !sessionIdRef.current) return;
      if (Date.now() - ultimoTickRef.current < 5000) return;
      if (!isStompConnected()) return;
      try {
        await obtenerEstadoDiaADia();
      } catch {
        // withReconnect handles session renewal
      }
    }, 3000);
    return () => clearInterval(interval);
  }, [enabled, hasActiveRun]);

  useEffect(() => {
    if (!enabled) return;
    return onSessionChange((newSessionId) => {
      setSessionId(newSessionId);
      setRunId((c) => c + 1);
    });
  }, [enabled]);

  useEffect(() => {
    if (!enabled || USE_MOCK) {
      if (!mockState) return;
      const local = ESTADO_BACK_A_LOCAL[mockState.status] ?? simStatus;
      if (local !== simStatus) setSimStatus(local);
      return;
    }
    if (!estadoMessage) return;
    const local = ESTADO_BACK_A_LOCAL[estadoMessage.estado] ?? simStatus;
    if (local !== simStatus) setSimStatus(local);
    if (estadoMessage.estado === "DETENIDA" && simStatus !== "idle") {
      setSessionId(null);
      setCurrentSimTimeUtc(null);
      setDatosBaseCargados(false);
    }
  }, [enabled, mockState, estadoMessage]);

  useEffect(() => {
    if (!enabled || !tick?.type) return;
    if (tick.type !== "TICK_DIAADIA") return;
    if (!datosBaseCargadosRef.current) return;
    ultimoTickRef.current = Date.now();
    tickCountRef.current++;
    setCurrentSimTimeUtc(tick.simTime);

    if (startSimMsRef.current == null) {
      const ms = Date.parse(`${tick.simTime}Z`);
      if (Number.isFinite(ms)) startSimMsRef.current = ms;
    }

    const occMap = {};
    if (Array.isArray(tick.aeropuertos)) {
      for (const a of tick.aeropuertos) occMap[a.id] = a.occ;
    }

    let vueloStateMap = {};
    let maletaStateMap = {};
    let rutaStateMap = {};
    if (Array.isArray(tick.estadosVuelos))
      vueloStateMap = Object.fromEntries(tick.estadosVuelos.map((v) => [v.id, v]));
    if (Array.isArray(tick.estadosMaletas))
      maletaStateMap = Object.fromEntries(tick.estadosMaletas.map((m) => [m.id, m]));
    if (Array.isArray(tick.estadosRutas))
      rutaStateMap = Object.fromEntries(tick.estadosRutas.map((r) => [r.id, r]));

    setMapAirports((prev) => prev.map((ap) => ({ ...ap, used: occMap[ap.iata] ?? ap.used })));

    setSimulationPanelData((prev) => {
      const ENUM_VUELO = ["PROGRAMADO", "CONFIRMADO", "EN_PROGRESO", "FINALIZADO", "CANCELADO"];
      const ENUM_MALETA = ["EN_ALMACEN", "EN_TRANSITO", "ENTREGADA"];

      const updatedFlights = updateEstadosOnly(
        prev.flights,
        vueloStateMap,
        ENUM_VUELO,
        "status",
        (st, flight) => ({ used: getUpdatedFlightOccupancy(st, flight) }),
      );
      for (const [id, flight] of updatedFlights) {
        const status = flight?.status;
        if (status === "FINALIZADO" || status === "CANCELADO") {
          updatedFlights.delete(id);
        }
      }
      const updatedBags = updateEstadosOnly(
        prev.bags,
        maletaStateMap,
        ENUM_MALETA,
        "estado",
        (st, bag) => (st.e === 2 ? { fechaLlegada: bag.fechaLlegada ?? tick.simTime } : {}),
      );
      const updatedRoutes = updateEstadosOnly(prev.routes, rutaStateMap, ENUM_RUTA, "estado");
      for (const [id, bag] of updatedBags) {
        if (bag.estado === "ENTREGADA") updatedBags.delete(id);
      }
      for (const [id, route] of updatedRoutes) {
        if (route.estado === "COMPLETADA") updatedRoutes.delete(id);
      }
      for (const [id, order] of prev.orders) {
        const s = String(order.status ?? "").toUpperCase();
        if (s === "ENTREGADO" || s === "FINALIZADO" || s === "ENVIADO") {
          prev.orders.delete(id);
        }
      }
      let flightTotalCap = 0;
      let flightTotalUsed = 0;
      for (const f of updatedFlights.values()) {
        if (f.status === "EN_PROGRESO") {
          flightTotalCap += f.capacity ?? 0;
          flightTotalUsed += f.used ?? 0;
        }
      }
      flightAggRef.current = { totalCap: flightTotalCap, totalUsed: flightTotalUsed };
      return {
        ...prev,
        simTime: tick.simTime,
        airports: prev.airports.map((ap) => ({ ...ap, used: occMap[ap.iata] ?? ap.used })),
        flights: updatedFlights,
        bags: updatedBags,
        routes: updatedRoutes,
      };
    });
    setFlightAggVersion((v) => v + 1);
  }, [enabled, tick]);

  useEffect(() => {
    if (!enabled || USE_MOCK || !hasActiveRun) return;
    if (tickCountRef.current % 60 !== 0) return;
    obtenerVuelosNuevosDiaADia()
      .then((nuevos) => {
        if (!nuevos?.length) return;
        const adapted = nuevos.map(adaptFlightInstance);
        setSimulationPanelData((prev) => {
          const flights = new Map(prev.flights);
          for (const f of adapted) {
            flights.set(f.idVueloInstancia ?? f.id, { ...f, ticksAusente: 0 });
          }
          return { ...prev, flights };
        });
      })
      .catch(() => {});
  }, [enabled, tick, hasActiveRun]);

  const liveMetrics = useMemo(() => {
    if (!hasActiveRun) return emptyMetrics;
    if (USE_MOCK) {
      return mockState
        ? {
            bagsInTransit: mockState.bagsInTransit ?? 0,
            bagsDelivered: mockState.bagsDelivered ?? 0,
            activeFlights: mockState.activeFlights ?? 0,
            airportCapacityPct: mockState.airportCapacityPct ?? 0,
            flightCapacityPct: mockState.flightCapacityPct ?? 0,
          }
        : emptyMetrics;
    }
    if (!tick || tick.type !== "TICK_DIAADIA") return emptyMetrics;
    const apTotalCap = mapAirports.reduce((s, a) => s + (a.capacity ?? 0), 0);
    const apTotalUsed = mapAirports.reduce((s, a) => s + (a.used ?? 0), 0);
    const airportCapacityPct = apTotalCap > 0 ? Math.round((apTotalUsed / apTotalCap) * 100) : 0;
    const agg = flightAggRef.current;
    const flightCapacityPct = agg.totalCap > 0 ? Math.round((agg.totalUsed / agg.totalCap) * 100) : 0;
    return {
      bagsInTransit: tick.maletasEnTransito ?? 0,
      bagsDelivered: tick.maletasEntregadas ?? 0,
      activeFlights: tick.vuelosActivos ?? 0,
      airportCapacityPct,
      flightCapacityPct,
    };
  }, [tick, mockState, hasActiveRun, mapAirports, flightAggVersion]);

  return {
    simStatus,
    currentSimTimeUtc,
    mapAirports,
    liveMetrics,
    simulatedNowMs,
    hasActiveRun,
  };
}
