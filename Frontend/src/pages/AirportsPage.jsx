import { useState } from "react";
import { useOutletContext } from "react-router-dom";
import { FileUp, Plus } from "lucide-react";
import PedidoModal from "../components/simulator/PedidoModal";
import AirportFormModal from "../components/simulator/AirportFormModal";
import Modal from "../components/ui/Modal";
import { LoadingState, EmptyState, ErrorState } from "../components/ui/States";
import { useToast } from "../components/ui/Toast";
import {
  procesarPedidoOperacionesDiaADia,
  procesarPedidosBulkOperacionesDiaADia,
  obtenerPedidosOperacionesDiaADia,
  obtenerMaletasOperacionesDiaADia,
  crearAeropuertoOperacionesDiaADia,
  eliminarAeropuertoOperacionesDiaADia,
  obtenerAeropuertosOperacionesDiaADia,
} from "../api/simulator";
import { adaptAirport } from "../api/airports";

const colorByOccupancy = (pct) =>
  pct >= 85 ? "bg-danger" : pct >= 60 ? "bg-warning" : "bg-success";

export default function AirportsPage() {
  const toast = useToast();
  const { simulationPanelData, setSimulationPanelData } = useOutletContext();

  const airports = simulationPanelData?.airports ?? [];
  const loaded = simulationPanelData?.loaded;
  const loading = !loaded;
  const error = null;

  const [pedidoOpen, setPedidoOpen] = useState(false);
  const [pedidoAirport, setPedidoAirport] = useState(null);
  const [pedidoLoading, setPedidoLoading] = useState(false);

  const [bulkOpen, setBulkOpen] = useState(false);
  const [bulkAirport, setBulkAirport] = useState(null);
  const [bulkLoading, setBulkLoading] = useState(false);
  const [bulkFile, setBulkFile] = useState(null);

  const [formOpen, setFormOpen] = useState(false);
  const [formLoading, setFormLoading] = useState(false);

  const handlePedidoSubmit = async (pedido) => {
    setPedidoLoading(true);
    try {
      await procesarPedidoOperacionesDiaADia(pedido);
      const [pedidosData, maletasData] = await Promise.all([
        obtenerPedidosOperacionesDiaADia().catch(() => []),
        obtenerMaletasOperacionesDiaADia().catch(() => []),
      ]);
      setSimulationPanelData((prev) => {
        const orders = new Map(prev.orders);
        for (const o of pedidosData ?? []) orders.set(o.id ?? o.idPedido, o);
        const bags = new Map(prev.bags);
        for (const b of maletasData ?? []) bags.set(b.idMaleta, { ...b, ticksAusente: 0 });
        return { ...prev, orders, bags };
      });
      setPedidoOpen(false);
      toast.push({ type: "success", title: "Pedido enviado", message: `${pedido.cantidadMaletas} maletas desde ${pedido.idAeropuertoOrigen} a ${pedido.idAeropuertoDestino}` });
    } catch (err) {
      toast.push({ type: "error", title: "Error al enviar pedido", message: err.message });
    } finally {
      setPedidoLoading(false);
    }
  };

  const handleBulkSubmit = async (e) => {
    e.preventDefault();
    if (!bulkFile) {
      toast.push({ type: "warning", title: "Falta archivo", message: "Selecciona un .txt con los pedidos." });
      return;
    }
    setBulkLoading(true);
    try {
      const text = await bulkFile.text();
      const result = await procesarPedidosBulkOperacionesDiaADia(bulkAirport.iata, text);
      const [pedidosData, maletasData] = await Promise.all([
        obtenerPedidosOperacionesDiaADia().catch(() => []),
        obtenerMaletasOperacionesDiaADia().catch(() => []),
      ]);
      setSimulationPanelData((prev) => {
        const orders = new Map(prev.orders);
        for (const o of pedidosData ?? []) orders.set(o.id ?? o.idPedido, o);
        const bags = new Map(prev.bags);
        for (const b of maletasData ?? []) bags.set(b.idMaleta, { ...b, ticksAusente: 0 });
        return { ...prev, orders, bags };
      });
      toast.push({
        type: "success",
        title: "Carga masiva procesada",
        message: `${result.accepted ?? 0} pedidos aceptados`,
      });
      setBulkFile(null);
      setBulkOpen(false);
    } catch (err) {
      toast.push({ type: "error", title: "Error en carga masiva", message: err.message });
    } finally {
      setBulkLoading(false);
    }
  };

  const openPedido = (airport) => {
    setPedidoAirport(airport);
    setPedidoOpen(true);
  };

  const openBulk = (airport) => {
    setBulkAirport(airport);
    setBulkFile(null);
    setBulkOpen(true);
  };

  const openCreate = () => {
    setFormOpen(true);
  };

  const handleFormSubmit = async (payload) => {
    setFormLoading(true);
    try {
      await crearAeropuertoOperacionesDiaADia(payload);
      const raw = await obtenerAeropuertosOperacionesDiaADia().catch(() => []);
      const aeropuertos = Array.isArray(raw) ? raw.map(adaptAirport) : [];
      setSimulationPanelData((prev) => ({
        ...prev,
        airports: aeropuertos,
      }));
      setFormOpen(false);
      toast.push({
        type: "success",
        title: "Aeropuerto creado",
        message: `${payload.idAeropuerto} creado correctamente`,
      });
    } catch (err) {
      toast.push({ type: "error", title: "Error", message: err.message });
    } finally {
      setFormLoading(false);
    }
  };

  const handleDelete = async (airport) => {
    if (!window.confirm(`Eliminar ${airport.iata} - ${airport.city}?`)) return;
    try {
      await eliminarAeropuertoOperacionesDiaADia(airport.iata);
      const raw = await obtenerAeropuertosOperacionesDiaADia().catch(() => []);
      const aeropuertos = Array.isArray(raw) ? raw.map(adaptAirport) : [];
      setSimulationPanelData((prev) => ({
        ...prev,
        airports: aeropuertos,
      }));
      toast.push({ type: "success", title: "Aeropuerto eliminado", message: airport.iata });
    } catch (err) {
      toast.push({ type: "error", title: "Error al eliminar", message: err.message });
    }
  };

  return (
    <div className="app-scrollbar flex-1 bg-surface-0 flex flex-col min-h-0 overflow-y-auto w-full h-full p-4 sm:p-8 text-slate-200">
      <div className="flex flex-col sm:flex-row justify-between sm:items-start gap-4 mb-8 pl-12 sm:pl-14">
        <div>
          <h1 className="text-2xl sm:text-4xl font-extrabold text-white mb-2">Tabla de Aeropuertos</h1>
          <p className="text-slate-400 text-base sm:text-lg">Gestiona la capacidad y estado de los nodos logisticos globales.</p>
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
          <EmptyState title="Sin aeropuertos" message="No hay aeropuertos disponibles en la sesion activa." />
        )}
        {!loading && !error && airports.length > 0 && (
          <table className="w-full text-left border-collapse min-w-[720px]">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400 text-sm">
                <th className="py-4 px-6 font-medium">IATA</th>
                <th className="py-4 px-6 font-medium">Ciudad</th>
                <th className="py-4 px-6 font-medium">Continente</th>
                <th className="py-4 px-6 font-medium min-w-[260px]">Ocupacion</th>
                <th className="py-4 px-6 font-medium text-right">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {airports.map((apt) => {
                const capacity = apt.capacity ?? 1;
                const pct = Math.round((apt.used / capacity) * 100);
                return (
                  <tr key={apt.iata} className="border-b border-slate-800/50 hover:bg-surface-2 transition-colors group">
                    <td className="py-4 px-6 font-bold text-blue-400">{apt.iata}</td>
                    <td className="py-4 px-6">
                      <div className="flex items-center gap-2">
                        <div className="w-1.5 h-1.5 rounded-full bg-slate-500"></div>
                        {apt.city}
                      </div>
                    </td>
                    <td className="py-4 px-6 text-slate-300">{apt.continent}</td>
                    <td className="py-4 px-6">
                      <div className="flex flex-col gap-1.5">
                        <div className="flex justify-between text-xs text-slate-400 font-medium">
                          <span>{apt.used} MALETAS</span>
                          <span>{pct}%</span>
                        </div>
                        <div className="w-full h-1.5 bg-surface-2 border border-slate-800 rounded-full overflow-hidden flex items-center pr-12 relative">
                          <div>
                            <div className={`h-full ${colorByOccupancy(pct)}`} style={{ width: `${pct}%` }}></div>
                            <span className="absolute right-0 text-[10px] text-slate-400">{apt.capacity}</span>
                          </div>
                        </div>
                      </div>
                    </td>
                    <td className="py-4 px-6 text-right">
                      <div className="flex justify-end gap-1 opacity-60 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
                        <button
                          type="button"
                          onClick={() => openBulk(apt)}
                          title="Carga masiva de pedidos"
                          className="p-2 rounded-lg hover:bg-surface-2 hover:text-blue-400 transition-colors text-slate-400"
                        >
                          <FileUp className="w-4 h-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => openPedido(apt)}
                          title="Agregar pedido"
                          className="p-2 rounded-lg hover:bg-surface-2 hover:text-blue-400 transition-colors text-slate-400"
                        >
                          <Plus className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      <PedidoModal
        open={pedidoOpen}
        airports={airports}
        onClose={() => setPedidoOpen(false)}
        onSubmit={handlePedidoSubmit}
        loading={pedidoLoading}
        lockedOrigin={pedidoAirport?.iata ?? null}
      />

      <AirportFormModal
        open={formOpen}
        initialData={null}
        onClose={() => setFormOpen(false)}
        onSubmit={handleFormSubmit}
        loading={formLoading}
      />

      <BulkPedidoModal
        open={bulkOpen}
        airport={bulkAirport}
        file={bulkFile}
        loading={bulkLoading}
        onFileChange={setBulkFile}
        onClose={() => setBulkOpen(false)}
        onSubmit={handleBulkSubmit}
      />
    </div>
  );
}

function BulkPedidoModal({ open, airport, file, loading, onFileChange, onClose, onSubmit }) {
  if (!open) return null;

  return (
    <Modal open={open} onClose={onClose} title="Carga Masiva de Pedidos" maxWidth="max-w-md">
      <form onSubmit={onSubmit} className="p-6">
        <h3 className="text-xl font-bold text-white mb-1">Carga Masiva de Pedidos</h3>
        {airport && (
          <p className="text-xs text-slate-400 mb-1">
            Origen: <span className="text-blue-400 font-bold">{airport.iata} — {airport.city}</span>
          </p>
        )}
        <p className="text-xs text-slate-400 mb-6">
          Sube un archivo .txt con un pedido por linea en el formato: <code className="text-slate-300 bg-surface-2 px-1 rounded">DEST-CANT-CLIENT</code>
        </p>

        <label htmlFor="bulk-pedido-file" className="block mb-2 text-sm font-bold text-white">Archivo (.txt)</label>
        <input
          id="bulk-pedido-file"
          type="file"
          accept=".txt,text/plain"
          onChange={(e) => onFileChange(e.target.files?.[0] ?? null)}
          className="block w-full text-sm text-slate-300 file:mr-3 file:py-1.5 file:px-3 file:rounded file:border-0 file:bg-surface-3 file:text-slate-200 hover:file:bg-surface-2 cursor-pointer"
        />
        {file && <p className="mt-2 text-xs text-slate-400">{file.name} · {Math.ceil(file.size / 1024)} KB</p>}

        <div className="mt-6 flex justify-end gap-3">
          <button type="button" onClick={onClose} className="font-bold text-slate-300 hover:text-white px-4 py-2 text-sm">Cancelar</button>
          <button type="submit" disabled={loading} className="bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-bold px-6 py-2.5 rounded-lg transition-colors text-sm">
            {loading ? "Subiendo..." : "Subir Archivo"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
