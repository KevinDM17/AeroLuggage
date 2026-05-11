import { Routes, Route, Navigate } from "react-router-dom";
import MainLayout from "../components/layout/MainLayout";
import SimulatorPage from "../pages/SimulatorPage";
import PeriodSimulatorPage from "../pages/PeriodSimulatorPage";
import CollapseSimulatorPage from "../pages/CollapseSimulatorPage";
import AirportsPage from "../pages/AirportsPage";
import FlightsPage from "../pages/FlightsPage";
import OrdersPage from "../pages/OrdersPage";

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<MainLayout />}>
        <Route index element={<SimulatorPage />} />
        <Route path="simulator" element={<Navigate to="/" replace />} />
        <Route path="simulator/period" element={<PeriodSimulatorPage />} />
        <Route path="simulator/collapse" element={<CollapseSimulatorPage />} />

        <Route path="airports" element={<AirportsPage />} />
        <Route path="flights" element={<FlightsPage />} />
        <Route path="orders" element={<OrdersPage />} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
