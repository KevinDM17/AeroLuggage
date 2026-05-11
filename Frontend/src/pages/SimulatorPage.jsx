import MapDashboard from "../components/simulator/MapDashboard";
import { usePolling } from "../hooks/usePolling";
import { getStatus } from "../api/status";

export default function SimulatorPage() {
  const { data: status } = usePolling(getStatus);

  return (
    <MapDashboard
      title="Visualización de Operaciones día a día"
      date={status?.date}
      time={status?.time}
      metrics={status ?? undefined}
    />
  );
}
