import Modal from "../ui/Modal";

export default function BulkPedidoModal({ open, airport, file, loading, onFileChange, onClose, onSubmit }) {
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
