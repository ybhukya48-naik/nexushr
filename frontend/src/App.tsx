import { BrowserRouter, Routes, Route, Navigate, Outlet } from "react-router-dom";
import { getToken } from "./api/client";
import { Nav } from "./components/Nav";
import { LoginPage }      from "./pages/LoginPage";
import { DashboardPage }  from "./pages/DashboardPage";
import { EmployeesPage }  from "./pages/EmployeesPage";
import { LeavePage }      from "./pages/LeavePage";
import { AttendancePage } from "./pages/AttendancePage";
import { PayrollPage }    from "./pages/PayrollPage";
import { PerformancePage} from "./pages/PerformancePage";
import { AiPage }         from "./pages/AiPage";

const ProtectedLayout = () => {
  if (!getToken()) return <Navigate to="/login" replace />;
  return (
    <>
      <Nav />
      <main className="app-main">
        <Outlet />
      </main>
    </>
  );
};

export const App = () => (
  <BrowserRouter>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedLayout />}>
        <Route path="/dashboard"   element={<DashboardPage />} />
        <Route path="/employees"   element={<EmployeesPage />} />
        <Route path="/leave"       element={<LeavePage />} />
        <Route path="/attendance"  element={<AttendancePage />} />
        <Route path="/payroll"     element={<PayrollPage />} />
        <Route path="/performance" element={<PerformancePage />} />
        <Route path="/ai"          element={<AiPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  </BrowserRouter>
);
