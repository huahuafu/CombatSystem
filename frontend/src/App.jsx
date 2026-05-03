import React from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import AppShell from "./layouts/AppShell";
import CampaignCenterPage from "./pages/CampaignCenterPage";
import RebuildCommanderPage from "./pages/RebuildCommanderPage";
import RunCenterPage from "./pages/RunCenterPage";
import SimulationDashboardPage from "./pages/SimulationDashboardPage";

export default function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/app/commander-next" element={<RebuildCommanderPage />} />
        <Route path="/app/simulation-dashboard" element={<SimulationDashboardPage />} />
        <Route path="/app/run-center" element={<RunCenterPage />} />
        <Route path="/app/campaign-center" element={<CampaignCenterPage />} />
        <Route path="/commander-next" element={<RebuildCommanderPage />} />
        <Route path="/simulation-dashboard" element={<SimulationDashboardPage />} />
        <Route path="/run-center" element={<RunCenterPage />} />
        <Route path="/campaign-center" element={<CampaignCenterPage />} />
        <Route path="*" element={<Navigate to="/app/commander-next" replace />} />
      </Routes>
    </AppShell>
  );
}
