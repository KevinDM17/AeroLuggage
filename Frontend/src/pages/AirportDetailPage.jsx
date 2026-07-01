import { useState, useEffect, useMemo, useCallback } from "react";
import { useParams } from "react-router-dom";
import { Plus, ArrowLeft } from "lucide-react";
import FlightPlanFormModal from "../components/simulator/FlightPlanFormModal";
import { LoadingState, EmptyState, ErrorState } from "../components/ui/States";
import { useToast } from "../components/ui/Toast";
import { normalizeContinente, getAirport, listAirports } from "../api/airports";
import { listFlightPlans, createFlightPlan } from "../api/flights";

const formatGMT = (h) => (h >= 0 ? `GMT+${h}` : `GMT${h}`);

export default function AirportDetailPage() {
  const { iata } = useParams();
  const toast = useToast();

  const [airport, setAirport] = useState(null);
  const [airportLoading, setAirportLoading] = useState(true);
  const [airportError, setAirportError] = useState(null);

  const [allAirports, setAllAirports] = useState([]);

  const [flightPlans, setFlightPlans] = useState(null);
  const [plansLoading, setPlansLoading] = useState(true);
  const [plansError, setPlansError] = useState(null);
  const [viewMode, setViewMode] = useState("origin");

  const [planFormOpen, setPlanFormOpen] = useState(false);
  const [planFormLoading, setPlanFormLoading] = useState(false);

  const loadAirport = useCallback(async () => {
    setAirportLoading(true);
    try {
      const data = await getAirport(iata);
      setAirport(data);
      setAirportError(null);
    } catch (err) {
      setAirportError(err.message);
    } finally {
      setAirportLoading(false);
    }
  }, [iata]);

  const loadFlightPlans = useCallback(async () => {
    setPlansLoading(true);
    try {
      const data = await listFlightPlans();
      setFlightPlans(Array.isArray(data) ? data : []);
      setPlansError(null);
    } catch (err) {
      setPlansError(err.message);
    } finally {
      setPlansLoading(false);
    }
  }, []);

  const loadAllAirports = useCallback(async () => {
    try {
      const data = await listAirports();
      setAllAirports(Array.isArray(data) ? data : []);
    } catch {
      setAllAirports([]);
    }
  }, []);

  useEffect(() => { loadAirport(); }, [loadAirport]);
  useEffect(() => { loadFlightPlans(); }, [loadFlightPlans]);
  useEffect(() => { loadAllAirports(); }, [loadAllAirports]);

  const filteredFlightPlans = useMemo(() => {
    if (!flightPlans || !iata) return [];
    return flightPlans.filter((f) =>
      viewMode === "origin" ? f.origin === iata : f.dest === iata
    );
  }, [flightPlans, iata, viewMode]);

  const handlePlanSubmit = async (payload) => {
    setPlanFormLoading(true);
    try {
      await createFlightPlan(payload);
      setPlanFormOpen(false);
      await loadFlightPlans();
      toast.push({ type: "success", title: "Plan de vuelo creado", message: `${payload.id} creado correctamente` });
    } catch (err) {
      toast.push({ type: "error", title: "Error", message: err.message });
    } finally {
      setPlanFormLoading(false);
    }
  };

  if (airportLoading) {
    return (
      <div className="flex-1 bg-surface-0 flex flex-col min-h-0 w-full h-full p-4 sm:p-8 text-slate-200">
        <LoadingState label="Cargando datos del aeropuerto..." />
      </div>
    );
  }

  if (airportError || !airport) {
    return (
      <div className="flex-1 bg-surface-0 flex flex-col min-h-0 w-full h-full p-4 sm:p-8 text-slate-200">
        <ErrorState error={airportError || `Aeropuerto "${iata}" no encontrado`} />
      </div>
    );
  }

  return (
    <div className="app-scrollbar flex-1 bg-surface-0 flex flex-col min-h-0 overflow-y-auto w-full min-h-full p-4 pb-8 sm:p-8 sm:pb-10 text-slate-200">
      <div className="mb-6 pl-12 sm:pl-14">
        <a href="/airports" className="inline-flex items-center gap-2 text-slate-400 hover:text-white text-sm transition-colors">
          <ArrowLeft className="w-4 h-4" /> Volver a Aeropuertos
        </a>
      </div>

      <div className="flex flex-col sm:flex-row justify-between sm:items-start gap-4 mb-8 pl-12 sm:pl-14">
        <div>
          <h1 className="text-2xl sm:text-4xl font-extrabold text-white mb-2">
            {airport.iata} — {airport.city}
          </h1>
          <p className="text-slate-400 text-base sm:text-lg">{airport.continent}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8 mx-0 sm:mx-14">
        <div className="bg-surface-1 border border-slate-800 rounded-xl p-5">
          <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Capacidad</div>
          <div className="mt-2 text-xl font-bold text-white">{airport.capacity} <span className="text-sm font-normal text-slate-400">maletas</span></div>
        </div>
        <div className="bg-surface-1 border border-slate-800 rounded-xl p-5">
          <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Huso GMT</div>
          <div className="mt-2 text-xl font-bold text-white">{formatGMT(airport.gmt)}</div>
        </div>
        <div className="bg-surface-1 border border-slate-800 rounded-xl p-5">
          <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Latitud</div>
          <div className="mt-2 text-xl font-bold text-white">{airport.lat ?? "—"}</div>
        </div>
        <div className="bg-surface-1 border border-slate-800 rounded-xl p-5">
          <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Longitud</div>
          <div className="mt-2 text-xl font-bold text-white">{airport.lng ?? "—"}</div>
        </div>
      </div>

      <div className="bg-surface-1 border border-slate-800 rounded-xl overflow-hidden mx-0 sm:mx-14">
        <div className="px-6 py-4 border-b border-slate-800 flex flex-col sm:flex-row justify-between sm:items-center gap-4">
          <div>
            <h2 className="text-lg font-bold text-white">Planes de Vuelo</h2>
            <p className="text-xs text-slate-400 mt-1">
              {filteredFlightPlans.length} plan{filteredFlightPlans.length !== 1 ? "es" : ""} encontrado{filteredFlightPlans.length !== 1 ? "s" : ""}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-4">
            <div className="flex bg-surface-2 rounded-lg p-0.5">
              <button
                type="button"
                onClick={() => setViewMode("origin")}
                className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                  viewMode === "origin"
                    ? "bg-blue-600 text-white"
                    : "text-slate-400 hover:text-slate-200"
                }`}
              >
                Como origen
              </button>
              <button
                type="button"
                onClick={() => setViewMode("dest")}
                className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                  viewMode === "dest"
                    ? "bg-blue-600 text-white"
                    : "text-slate-400 hover:text-slate-200"
                }`}
              >
                Como destino
              </button>
            </div>
            <button
              type="button"
              onClick={() => setPlanFormOpen(true)}
              className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white px-3 py-1.5 rounded-lg font-medium text-sm transition-colors"
            >
              <Plus className="w-4 h-4" /> Agregar Plan de Vuelo
            </button>
          </div>
        </div>

        {plansLoading && <LoadingState label="Cargando planes de vuelo..." />}
        {!plansLoading && plansError && <ErrorState error={plansError} />}
        {!plansLoading && !plansError && filteredFlightPlans.length === 0 && (
          <EmptyState title="Sin planes de vuelo" message={`No hay planes de vuelo con ${viewMode === "origin" ? "origen" : "destino"} en ${iata}.`} />
        )}
        {!plansLoading && !plansError && filteredFlightPlans.length > 0 && (
          <div className="app-scrollbar overflow-x-auto overflow-y-auto max-h-[56vh] min-h-[320px]">
            <table className="w-full text-left border-collapse min-w-[720px]">
              <thead className="sticky top-0 bg-surface-1 z-10">
                <tr className="border-b border-slate-800 text-slate-400 text-sm">
                  <th className="py-4 px-6 font-medium">Codigo</th>
                  <th className="py-4 px-6 font-medium">Origen</th>
                  <th className="py-4 px-6 font-medium">Destino</th>
                  <th className="py-4 px-6 font-medium">Salida</th>
                  <th className="py-4 px-6 font-medium">Llegada</th>
                  <th className="py-4 px-6 font-medium">Capacidad</th>
                </tr>
              </thead>
              <tbody>
                {filteredFlightPlans.map((fl) => (
                  <tr key={fl.id} className="border-b border-slate-800/50 hover:bg-slate-800/30 transition-colors group">
                    <td className="py-4 px-6 font-bold text-blue-400">{fl.id}</td>
                    <td className="py-4 px-6 text-slate-300 font-medium">{fl.origin}</td>
                    <td className="py-4 px-6 text-slate-300">
                      <div className="font-medium">{fl.dest}</div>
                      {(fl.destCity || fl.destCont) && (
                        <div className="text-xs text-slate-400">{fl.destCity}{fl.destCity && fl.destCont ? ", " : ""}{normalizeContinente(fl.destCont)}</div>
                      )}
                    </td>
                    <td className="py-4 px-6 text-sm text-slate-400">
                      {fl.depTime} <span className="text-slate-500">({formatGMT(fl.gmtOrigin)})</span>
                    </td>
                    <td className="py-4 px-6 text-sm text-slate-400">
                      {fl.arrTime} <span className="text-slate-500">({formatGMT(fl.gmtDest)})</span>
                    </td>
                    <td className="py-4 px-6 text-sm text-slate-300">{fl.capacity}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <FlightPlanFormModal
        open={planFormOpen}
        initialData={null}
        airports={allAirports}
        onClose={() => setPlanFormOpen(false)}
        onSubmit={handlePlanSubmit}
        loading={planFormLoading}
      />
    </div>
  );
}
