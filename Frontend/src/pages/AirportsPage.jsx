import { useState, useEffect, useCallback } from "react";
import { Plus, Plane, Pencil } from "lucide-react";
import AirportFormModal from "../components/simulator/AirportFormModal";
import Modal from "../components/ui/Modal";
import { LoadingState, EmptyState, ErrorState } from "../components/ui/States";
import { useToast } from "../components/ui/Toast";
import { listAirports, createAirport, updateAirport, normalizeContinente } from "../api/airports";
import { listFlightPlans } from "../api/flights";

const formatGMT = (h) => (h >= 0 ? `GMT+${h}` : `GMT${h}`);

export default function AirportsPage() {
  const toast = useToast();

  const [airports, setAirports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editingAirport, setEditingAirport] = useState(null);
  const [formLoading, setFormLoading] = useState(false);

  const [flightPlansModalAirport, setFlightPlansModalAirport] = useState(null);
  const [airportFlightPlans, setAirportFlightPlans] = useState([]);
  const [airportFlightPlansStatus, setAirportFlightPlansStatus] = useState("idle");

  const loadAirports = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listAirports();
      setAirports(Array.isArray(data) ? data : []);
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadAirports(); }, [loadAirports]);

  useEffect(() => {
    if (!flightPlansModalAirport) return undefined;
    let cancelled = false;
    setAirportFlightPlansStatus("loading");
    listFlightPlans(flightPlansModalAirport.iata)
      .then((data) => {
        if (cancelled) return;
        setAirportFlightPlans(Array.isArray(data) ? data : []);
        setAirportFlightPlansStatus("ready");
      })
      .catch(() => {
        if (cancelled) return;
        setAirportFlightPlans([]);
        setAirportFlightPlansStatus("error");
      });
    return () => { cancelled = true; };
  }, [flightPlansModalAirport]);

  const closeFlightPlansModal = () => {
    setFlightPlansModalAirport(null);
    setAirportFlightPlans([]);
    setAirportFlightPlansStatus("idle");
  };

  const openCreate = () => {
    setEditingAirport(null);
    setFormOpen(true);
  };

  const openEdit = (airport) => {
    setEditingAirport(airport);
    setFormOpen(true);
  };

  const handleFormSubmit = async (payload) => {
    setFormLoading(true);
    try {
      if (editingAirport) {
        await updateAirport(editingAirport.iata, payload);
      } else {
        await createAirport(payload);
      }
      setFormOpen(false);
      await loadAirports();
      toast.push({
        type: "success",
        title: editingAirport ? "Aeropuerto actualizado" : "Aeropuerto creado",
        message: `${payload.iata} ${editingAirport ? "actualizado" : "creado"} correctamente`,
      });
    } catch (err) {
      toast.push({ type: "error", title: "Error", message: err.message });
    } finally {
      setFormLoading(false);
    }
  };

  const refreshAirportFlightPlans = async (airport) => {
    setAirportFlightPlansStatus("loading");
    try {
      const data = await listFlightPlans(airport.iata);
      setAirportFlightPlans(Array.isArray(data) ? data : []);
      setAirportFlightPlansStatus("ready");
    } catch {
      setAirportFlightPlans([]);
      setAirportFlightPlansStatus("error");
    }
  };

  return (
    <div className="app-scrollbar flex-1 bg-surface-0 flex flex-col min-h-0 overflow-y-auto w-full h-full p-4 sm:p-8 text-slate-200">
      <div className="flex flex-col sm:flex-row justify-between sm:items-start gap-4 mb-8 pl-12 sm:pl-14">
        <div>
          <h1 className="text-2xl sm:text-4xl font-extrabold text-white mb-2">Tabla de Aeropuertos</h1>
          <p className="text-slate-400 text-base sm:text-lg">Gestiona los aeropuertos del sistema.</p>
        </div>
        <button
          type="button"
          onClick={openCreate}
          className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded-lg font-medium text-sm transition-colors"
        >
          <Plus className="w-4 h-4" /> Agregar Aeropuerto
        </button>
      </div>

      <div className="app-scrollbar bg-surface-1 border border-slate-800 rounded-xl overflow-x-auto">
        {loading && <LoadingState label="Cargando aeropuertos..." />}
        {!loading && error && <ErrorState error={error} />}
        {!loading && !error && airports.length === 0 && (
          <EmptyState title="Sin aeropuertos" message="No hay aeropuertos registrados. Crea el primero." />
        )}
        {!loading && !error && airports.length > 0 && (
          <table className="w-full text-left border-collapse min-w-[720px]">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400 text-sm">
                <th className="py-4 px-6 font-medium">IATA</th>
                <th className="py-4 px-6 font-medium">Ciudad</th>
                <th className="py-4 px-6 font-medium">Continente</th>
                <th className="py-4 px-6 font-medium">Capacidad</th>
                <th className="py-4 px-6 font-medium">GMT</th>
                <th className="py-4 px-6 font-medium text-right">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {airports.map((apt) => (
                <tr key={apt.iata} className="border-b border-slate-800/50 hover:bg-surface-2 transition-colors group">
                  <td className="py-4 px-6 font-bold text-blue-400">{apt.iata}</td>
                  <td className="py-4 px-6">
                    <div className="flex items-center gap-2">
                      <div className="w-1.5 h-1.5 rounded-full bg-slate-500"></div>
                      {apt.city}
                    </div>
                  </td>
                  <td className="py-4 px-6 text-slate-300">{apt.continent}</td>
                  <td className="py-4 px-6 text-slate-300">{apt.capacity}</td>
                  <td className="py-4 px-6 text-slate-300">{formatGMT(apt.gmt)}</td>
                  <td className="py-4 px-6 text-right">
                    <div className="flex justify-end gap-1 opacity-60 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
                      <button
                        type="button"
                        onClick={() => {
                          setFlightPlansModalAirport(apt);
                        }}
                        title="Ver vuelos programados"
                        className="p-2 rounded-lg hover:bg-surface-2 hover:text-blue-400 transition-colors text-slate-400"
                      >
                        <Plane className="w-4 h-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => openEdit(apt)}
                        title="Editar aeropuerto"
                        className="p-2 rounded-lg hover:bg-surface-2 hover:text-blue-400 transition-colors text-slate-400"
                      >
                        <Pencil className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <AirportFormModal
        open={formOpen}
        initialData={editingAirport}
        onClose={() => setFormOpen(false)}
        onSubmit={handleFormSubmit}
        loading={formLoading}
      />

      <Modal
        open={Boolean(flightPlansModalAirport)}
        onClose={closeFlightPlansModal}
        title={`Vuelos programados de ${flightPlansModalAirport?.iata ?? ""}`}
        maxWidth="max-w-4xl"
      >
        <div className="flex max-h-[80vh] flex-col">
          <div className="border-b border-slate-800 px-6 py-5 pr-16">
            <h3 className="text-lg font-semibold text-slate-100">
              Vuelos programados de {flightPlansModalAirport?.iata ?? "--"}
            </h3>
            <p className="mt-2 text-sm leading-relaxed text-slate-400">
              Revisa los vuelos programados desde este aeropuerto.
            </p>
          </div>

          <div className="app-scrollbar overflow-y-auto px-6 py-5">
            {airportFlightPlansStatus === "loading" ? (
              <LoadingState label="Cargando vuelos programados..." />
            ) : airportFlightPlansStatus === "error" ? (
              <ErrorState
                error={{ message: "No se pudieron cargar los vuelos programados de este aeropuerto." }}
                onRetry={() => refreshAirportFlightPlans(flightPlansModalAirport)}
              />
            ) : airportFlightPlans.length === 0 ? (
              <EmptyState title="Sin vuelos programados" message="Este aeropuerto no tiene vuelos programados registrados." />
            ) : (
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-800 text-slate-400 text-sm">
                    <th className="py-3 px-4 font-medium">Codigo</th>
                    <th className="py-3 px-4 font-medium">Origen</th>
                    <th className="py-3 px-4 font-medium">Destino</th>
                    <th className="py-3 px-4 font-medium">Salida</th>
                    <th className="py-3 px-4 font-medium">Llegada</th>
                    <th className="py-3 px-4 font-medium">Capacidad</th>
                  </tr>
                </thead>
                <tbody>
                  {airportFlightPlans.map((fl, idx) => (
                    <tr key={fl.id ?? idx} className="border-b border-slate-800/50 hover:bg-surface-2 transition-colors">
                      <td className="py-3 px-4 font-bold text-blue-400 text-sm">{fl.id}</td>
                      <td className="py-3 px-4 text-slate-300 text-sm font-medium">{fl.origin}</td>
                      <td className="py-3 px-4 text-slate-300 text-sm">
                        {fl.dest}
                        {(fl.destCity || fl.destCont) && (
                          <div className="text-xs text-slate-400">{fl.destCity}{fl.destCity && fl.destCont ? ", " : ""}{normalizeContinente(fl.destCont)}</div>
                        )}
                      </td>
                      <td className="py-3 px-4 text-sm text-slate-400">{fl.depTime} ({formatGMT(fl.gmtOrigin)})</td>
                      <td className="py-3 px-4 text-sm text-slate-400">{fl.arrTime} ({formatGMT(fl.gmtDest)})</td>
                      <td className="py-3 px-4 text-sm text-slate-300">{fl.capacity}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </Modal>
    </div>
  );
}
