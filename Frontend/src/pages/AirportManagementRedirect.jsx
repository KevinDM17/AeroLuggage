import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { LoadingState } from "../components/ui/States";
import { listAirports } from "../api/airports";

const STORAGE_KEY = "managedAirport";

export default function AirportManagementRedirect() {
  const [target, setTarget] = useState(null);

  useEffect(() => {
    let cancelled = false;
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      setTarget(saved);
      return;
    }
    listAirports()
      .then((data) => {
        if (cancelled) return;
        const arr = Array.isArray(data) ? data : [];
        const sorted = [...arr].sort((a, b) => (a.iata ?? "").localeCompare(b.iata ?? ""));
        const first = sorted[0]?.iata;
        if (first) {
          localStorage.setItem(STORAGE_KEY, first);
          setTarget(first);
        }
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, []);

  if (!target) {
    return (
      <div className="flex-1 bg-surface-0 flex flex-col min-h-0 w-full h-full p-4 sm:p-8 text-slate-200">
        <LoadingState label="Cargando aeropuerto..." />
      </div>
    );
  }

  return <Navigate to={`/gestion-aeropuerto/${target}`} replace />;
}
