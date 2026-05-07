import { Routes, Route, Navigate } from "react-router-dom";
import MainLayout from "../components/layout/MainLayout";
import SimulatorPage from "../pages/SimulatorPage";
import AirportsPage from "../pages/AirportsPage";
import FlightsPage from "../pages/FlightsPage";
import OrdersPage from "../pages/OrdersPage";

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<MainLayout />}>
        {/* Make simulator the main view to match user expectations */}
        <Route index element={<SimulatorPage />} />
        <Route path="simulator" element={<Navigate to="/" replace />} />
        
        <Route path="airports" element={<AirportsPage />} />
        <Route path="flights" element={<FlightsPage />} />
        <Route path="orders" element={<OrdersPage />} />
        
        {/* Fallbacks */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
