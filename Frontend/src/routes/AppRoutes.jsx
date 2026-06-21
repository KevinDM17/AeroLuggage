import { Routes, Route, Navigate } from "react-router-dom";
import MainLayout from "../components/layout/MainLayout";
import HomePage from "../pages/HomePage";
import SimulatorPage from "../pages/SimulatorPage";
import PeriodSimulatorPage from "../pages/PeriodSimulatorPage";
import CollapseSimulatorPage from "../pages/CollapseSimulatorPage";
import AirportsPage from "../pages/AirportsPage";
import FlightsPage from "../pages/FlightsPage";
import FlightPlansPage from "../pages/FlightPlansPage";
import OrdersPage from "../pages/OrdersPage";

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<MainLayout />}>
        <Route index element={<HomePage />} />
        <Route path="inicio" element={<Navigate to="/" replace />} />
        <Route path="operaciones" element={<SimulatorPage />} />
        <Route path="simulator" element={<Navigate to="/operaciones" replace />} />
        <Route path="simulator/period" element={<PeriodSimulatorPage />} />
        <Route path="simulator/collapse" element={<CollapseSimulatorPage />} />

        <Route path="airports" element={<AirportsPage />} />
        <Route path="flights">
          <Route index element={<Navigate to="occurrences" replace />} />
          <Route path="occurrences" element={<FlightsPage />} />
          <Route path="plans" element={<FlightPlansPage />} />
        </Route>
        <Route path="orders" element={<OrdersPage />} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
