import { Routes, Route } from "react-router-dom";
import DashboardPage from "./pages/DashboardPage";
import IncidentDetailPage from "./pages/IncidentDetailPage";

function App() {
  return (
    <Routes>
      <Route path="/" element={<DashboardPage />} />
      <Route path="/incidents/:id" element={<IncidentDetailPage />} />
    </Routes>
  );
}

export default App;
