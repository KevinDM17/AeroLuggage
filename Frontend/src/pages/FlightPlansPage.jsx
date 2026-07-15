import { useState, useEffect, useCallback } from "react";
import { Plus, Pencil, Trash2 } from "lucide-react";
import FlightPlanFormModal from "../components/simulator/FlightPlanFormModal";
import Modal from "../components/ui/Modal";
import { LoadingState, EmptyState, ErrorState } from "../components/ui/States";
import { useToast } from "../components/ui/Toast";
import { normalizeContinente, listAirports } from "../api/airports";
import {
  listFlightPlans,
  createFlightPlan,
  updateFlightPlan,
  deleteFlightPlan,
} from "../api/flights";

const formatGMT = (h) => (h >= 0 ? `GMT+${h}` : `GMT${h}`);

export default function FlightPlansPage() {
  const toast = useToast();

  const [airports, setAirports] = useState([]);
  const [airportsLoading, setAirportsLoading] = useState(true);

  const [flights, setFlights] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedAirport, setSelectedAirport] = useState("");

  const [formOpen, setFormOpen] = useState(false);
  const [editingFlight, setEditingFlight] = useState(null);
  const [formLoading, setFormLoading] = useState(false);

  const [deleteConfirmFlight, setDeleteConfirmFlight] = useState(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const loadAirports = useCallback(async () => {
    setAirportsLoading(true);
    try {
      const data = await listAirports();
      const arr = Array.isArray(data) ? data : [];
      setAirports(arr);
      if (!selectedAirport && arr.length > 0) {
        setSelectedAirport(arr[0].iata ?? arr[0].idAeropuerto ?? "");
      }
    } catch {
      setAirports([]);
    } finally {
      setAirportsLoading(false);
    }
  }, []);

  useEffect(() => { loadAirports(); }, [loadAirports]);

  const loadFlights = useCallback(async () => {
    if (!selectedAirport) return;
    setLoading(true);
    try {
      const data = await listFlightPlans(selectedAirport);
      setFlights(Array.isArray(data) ? data : []);
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [selectedAirport]);

  useEffect(() => { loadFlights(); }, [loadFlights]);

  const openCreate = () => {
    setEditingFlight(null);
    setFormOpen(true);
  };

  const openEdit = (flight) => {
    setEditingFlight(flight);
    setFormOpen(true);
  };

  const handleDeleteConfirm = async () => {
    if (!deleteConfirmFlight) return;
    setDeleteLoading(true);
    try {
      await deleteFlightPlan(deleteConfirmFlight.idVueloProgramado);
      setDeleteConfirmFlight(null);
      await loadFlights();
      toast.push({
        type: "success",
        title: "Plan de vuelo eliminado",
        message: `${deleteConfirmFlight.id} eliminado correctamente`,
      });
    } catch (err) {
      toast.push({ type: "error", title: "Error al eliminar", message: err.message });
    } finally {
      setDeleteLoading(false);
    }
  };

  const handleFormSubmit = async (payload) => {
    setFormLoading(true);
    try {
      if (editingFlight) {
        await updateFlightPlan(editingFlight.idVueloProgramado, payload);
      } else {
        await createFlightPlan(payload);
      }
      setFormOpen(false);
      await loadFlights();
      toast.push({
        type: "success",
        title: editingFlight ? "Plan de vuelo actualizado" : "Plan de vuelo creado",
        message: `${payload.id} ${editingFlight ? "actualizado" : "creado"} correctamente`,
      });
    } catch (err) {
      toast.push({ type: "error", title: "Error", message: err.message });
    } finally {
      setFormLoading(false);
    }
  };

  return (
    <div className="flex-1 bg-surface-0 flex flex-col min-h-0 overflow-y-auto w-full h-full p-4 sm:p-8 text-slate-200">
      <div className="flex flex-col sm:flex-row justify-between sm:items-start gap-4 mb-8 pl-12 sm:pl-14">
        <div>
          <h1 className="text-2xl sm:text-4xl font-extrabold text-white mb-2">Planes de Vuelo</h1>
          <p className="text-slate-400 text-base sm:text-lg">Define y gestiona los planes de vuelo programados.</p>
        </div>
        <button
          type="button"
          onClick={openCreate}
          className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded-lg font-medium text-sm transition-colors"
        >
          <Plus className="w-4 h-4" /> Agregar Plan de Vuelo
        </button>
      </div>

      <div className="mb-6 pl-12 sm:pl-14">
        <span className="text-slate-400 text-base sm:text-lg mr-2">Aeropuerto de origen: </span>
        {airportsLoading ? (
          <span className="text-slate-500 text-sm">Cargando...</span>
        ) : (
          <select
            value={selectedAirport}
            onChange={(e) => setSelectedAirport(e.target.value)}
            className="bg-surface-2 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-200 outline-none focus:border-blue-500"
          >
            {airports.map((a) => (
              <option key={a.iata ?? a.idAeropuerto} value={a.iata ?? a.idAeropuerto}>
                {a.iata ?? a.idAeropuerto} — {a.city ?? a?.ciudad?.nombre ?? ""}
              </option>
            ))}
          </select>
        )}
      </div>

      <div className="bg-surface-1 border border-slate-800 rounded-xl overflow-x-auto">
        {loading && <LoadingState label="Cargando planes de vuelo..." />}
        {!loading && error && <ErrorState error={error} />}
        {!loading && !error && flights && flights.length === 0 && (
          <EmptyState title="Sin planes de vuelo" message="No hay planes de vuelo programados. Crea el primero." />
        )}
        {!loading && !error && flights && flights.length > 0 && (
          <table className="w-full text-left border-collapse min-w-[720px]">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400 text-sm">
                <th className="py-4 px-6 font-medium">Codigo</th>
                <th className="py-4 px-6 font-medium">Origen</th>
                <th className="py-4 px-6 font-medium">Destino</th>
                <th className="py-4 px-6 font-medium">Salida</th>
                <th className="py-4 px-6 font-medium">Llegada</th>
                <th className="py-4 px-6 font-medium">Capacidad</th>
                <th className="py-4 px-6 font-medium text-right">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {flights.map((fl) => (
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
                  <td className="py-4 px-6 text-right">
                    <div className="flex justify-end gap-1 opacity-60 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
                      <button
                        type="button"
                        onClick={() => openEdit(fl)}
                        title="Editar plan de vuelo"
                        className="p-2 rounded-lg hover:bg-surface-2 hover:text-blue-400 transition-colors text-slate-400"
                      >
                        <Pencil className="w-4 h-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => setDeleteConfirmFlight(fl)}
                        title="Eliminar plan de vuelo"
                        className="p-2 rounded-lg hover:bg-red-900/30 hover:text-red-400 transition-colors text-slate-400"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <FlightPlanFormModal
        open={formOpen}
        initialData={editingFlight}
        airports={airports}
        onClose={() => setFormOpen(false)}
        onSubmit={handleFormSubmit}
        loading={formLoading}
      />

      <Modal
        open={Boolean(deleteConfirmFlight)}
        onClose={() => !deleteLoading && setDeleteConfirmFlight(null)}
        title="Confirmar eliminacion"
        maxWidth="max-w-md"
      >
        <div className="px-6 py-5">
          <p className="text-slate-300 text-sm leading-relaxed">
            Estas seguro de que deseas eliminar el plan de vuelo{" "}
            <span className="font-bold text-white">{deleteConfirmFlight?.id}</span>
            {" "}({deleteConfirmFlight?.origin} &rarr; {deleteConfirmFlight?.dest})?
          </p>
          <p className="mt-2 text-xs text-slate-500">
            Se cancelaran todas las instancias activas de este vuelo y se replanificaran las maletas afectadas.
          </p>
          <div className="mt-6 flex justify-end gap-3">
            <button
              type="button"
              onClick={() => setDeleteConfirmFlight(null)}
              disabled={deleteLoading}
              className="px-4 py-2 rounded-lg text-sm font-medium text-slate-300 hover:bg-surface-2 transition-colors disabled:opacity-50"
            >
              Cancelar
            </button>
            <button
              type="button"
              onClick={handleDeleteConfirm}
              disabled={deleteLoading}
              className="px-4 py-2 rounded-lg text-sm font-medium bg-red-600 hover:bg-red-500 text-white transition-colors disabled:opacity-50 flex items-center gap-2"
            >
              {deleteLoading ? "Eliminando..." : "Eliminar"}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
