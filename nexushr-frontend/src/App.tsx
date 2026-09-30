import { useEffect, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import {
  BrowserRouter,
  NavLink,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  getDashboardSummary,
  login as apiLogin,
  getAiWorkforceDashboard,
  type DashboardSummary,
  type AiWorkforceDashboardResponse,
} from "./api";
import Employees from "./pages/Employees";
import EmployeeProfile from "./pages/EmployeeProfile";
import Attendance from "./pages/Attendance";
import Leave from "./pages/Leave";
import Payroll from "./pages/Payroll";
import Performance from "./pages/Performance";
import Notifications from "./pages/Notifications";

const navItems: ReadonlyArray<
  readonly [string, string, readonly string[]]
> = [
  ["Dashboard", "/", ["ADMIN", "HR", "MANAGER"]],
  ["Employees", "/employees", ["ADMIN", "HR", "MANAGER"]],
  ["Attendance", "/attendance", ["ADMIN", "HR", "MANAGER", "EMPLOYEE"]],
  ["Leave", "/leave", ["ADMIN", "HR", "MANAGER", "EMPLOYEE"]],
  ["Payroll", "/payroll", ["ADMIN", "HR", "MANAGER", "EMPLOYEE"]],
  ["Performance", "/performance", ["ADMIN", "HR", "MANAGER", "EMPLOYEE"]],
  ["AI Insights", "/ai", ["ADMIN", "HR", "MANAGER"]],
  ["Notifications", "/notifications", ["ADMIN", "HR", "MANAGER", "EMPLOYEE"]],
] as const;

function isAuthenticated() {
  return Boolean(localStorage.getItem("nexushr_token"));
}

function ProtectedRoute({ children }: { children: ReactNode }) {
  const location = useLocation();

  if (!isAuthenticated()) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <>{children}</>;
}

function AppRoutes() {
  const navigate = useNavigate();

  const username =
    localStorage.getItem("nexushr_user") || "HR Manager";

  const role =
    localStorage.getItem("nexushr_role") || "HR";

  function handleLogout() {
    localStorage.removeItem("nexushr_token");
    localStorage.removeItem("nexushr_user");
    localStorage.removeItem("nexushr_role");
    navigate("/login", { replace: true });
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">N</div>
          <div>
            <strong>Cyond</strong>
            <span>Waterproofing Diagnosis & Repair Experts</span>
          </div>
        </div>

        <nav>
          {navItems
            .filter(([, , roles]) => roles.includes(role))
            .map(([label, path]) => (
            <NavLink
              key={path}
              to={path}
              end={path === "/"}
              className={({ isActive }) =>
                isActive ? "nav-link active" : "nav-link"
              }
            >
              <span>{label === "AI Insights" ? "AI" : ""}</span>
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="status-dot"></div>
          <div>
            <strong>System Online</strong>
            <span>All services operational</span>
          </div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div>
            <p className="eyebrow">ENTERPRISE HR PLATFORM</p>
            <h1>Cyond</h1>
          </div>

          <div className="user-area">
            <button
              className="notification-button"
              onClick={() => navigate("/notifications")}
              aria-label="Notifications"
            >
            </button>

            <div className="avatar">
              {role === "ADMIN" ? "AD" : role === "MANAGER" ? "MG" : "HR"}
            </div>

            <div>
              <strong>{username}</strong>
              <span>{role}</span>
            </div>

            <button
              className="primary-button"
              onClick={handleLogout}
            >
              Logout
            </button>
          </div>
        </header>

        <section className="content">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/employees" element={<Employees />} />
            <Route path="/employees/:id" element={<EmployeeProfile />} />
            <Route path="/attendance" element={<Attendance />} />
            <Route path="/leave" element={<Leave />} />
            <Route path="/payroll" element={<Payroll />} />            <Route path="/performance" element={<Performance />} />
            <Route path="/ai" element={<AIInsights />} />
            <Route path="/notifications" element={<Notifications />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </section>
      </main>
    </div>
  );
}
function Login() {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");

    if (!username.trim() || !password) {
      setError(
        "Please enter your employee code or email and password.",
      );
      return;
    }

    setLoading(true);

    try {
      const response = await apiLogin(
        username.trim(),
        password,
      );

      localStorage.setItem(
        "nexushr_token",
        response.accessToken,
      );

      localStorage.setItem(
        "nexushr_user",
        response.username,
      );

      localStorage.setItem(
        "nexushr_role",
        response.role,
      );

      navigate("/", { replace: true });
    } catch (err) {
      console.error(err);

      setError(
        err instanceof Error
          ? err.message
          : "Invalid username or password.",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-background-orb login-orb-one"></div>

      <div className="login-background-orb login-orb-two"></div>

      <div className="login-layout">

        {/* =====================================================
            LEFT BRAND PANEL
        ====================================================== */}
        <div className="login-brand-panel">

          <div className="login-brand">

            <div className="brand-mark large">
              N
            </div>

            <div>
              <strong>Cyond</strong>

              <span>
                Waterproofing Diagnosis &amp; Repair Experts
              </span>
            </div>

          </div>

          <div className="login-cover-image">

            <img
              src="/images/cyond-cover.png"
              alt="Cyond Waterproofing Diagnosis & Repair Experts"
            />

          </div>

          <div className="login-hero">

            <span className="login-badge">
              Enterprise HR Platform
            </span>

            <h1>
              Manage your
              <span> workforce smarter.</span>
            </h1>

            <p>
              A unified workspace for employee lifecycle,
              attendance, payroll, performance and workforce
              intelligence.
            </p>

          </div>

          <div className="login-features">

            <div className="login-feature">

              <div className="login-feature-icon">
                W
              </div>

              <div>
                <strong>
                  Workforce Management
                </strong>

                <span>
                  Centralized employee and lifecycle
                  operations
                </span>
              </div>

            </div>

            <div className="login-feature">

              <div className="login-feature-icon">
                I
              </div>

              <div>
                <strong>
                  Real-time Intelligence
                </strong>

                <span>
                  Insights that help HR teams make
                  better decisions
                </span>
              </div>

            </div>

            <div className="login-feature">

              <div className="login-feature-icon">
                S
              </div>

              <div>
                <strong>
                  Secure by Design
                </strong>

                <span>
                  Role-based access protected by
                  enterprise security
                </span>
              </div>

            </div>

          </div>

        </div>

        {/* =====================================================
            LOGIN FORM
        ====================================================== */}
        <div className="login-form-area">

          <form
            className="login-card"
            onSubmit={handleSubmit}
          >

            <div className="login-card-header">

              {/* MOBILE BRAND */}
              <div className="login-mobile-brand">

                <div className="brand-mark">
                  N
                </div>

                <div>
                  <strong>
                    Cyond
                  </strong>

                  <span>
                    Waterproofing Diagnosis &amp; Repair Experts
                  </span>
                </div>

              </div>

              {/* CYOND BRANDING */}
              <p className="eyebrow">
                CYOND WATERPROOFING DIAGNOSIS &amp; REPAIR
                EXPERTS
              </p>

              <h2>
                Welcome back
              </h2>

              <p>
                Sign in to continue to your Cyond workspace.
              </p>

            </div>

            {/* ERROR MESSAGE */}
            {error && (
              <div className="login-error">

                <div className="login-error-icon">
                  !
                </div>

                <div>

                  <strong>
                    Sign-in failed
                  </strong>

                  <span>
                    {error}
                  </span>

                </div>

              </div>
            )}

            {/* =================================================
                LOGIN FIELDS
            ================================================== */}
            <div className="login-fields">

              {/* EMPLOYEE CODE / EMAIL */}
              <label>

                <span>
                  Employee Code or Email
                </span>

                <div className="login-input-wrap">

                  <input
                    type="text"
                    value={username}
                    onChange={(event) =>
                      setUsername(event.target.value)
                    }
                    placeholder="Enter employee code or email"
                    autoComplete="username"
                    required
                  />

                </div>

              </label>

              {/* PASSWORD */}
              <label>

                <span>
                  Password
                </span>

                <div className="login-input-wrap">

                  <input
                    type="password"
                    value={password}
                    onChange={(event) =>
                      setPassword(event.target.value)
                    }
                    placeholder="Enter your password"
                    autoComplete="current-password"
                    required
                  />

                </div>

              </label>

            </div>

            {/* LOGIN BUTTON */}
            <button
              className="login-submit"
              type="submit"
              disabled={loading}
            >

              <span>
                {loading
                  ? "Signing in..."
                  : "Sign in to Cyond"}
              </span>

              {!loading && (
                <span className="login-submit-arrow">
                  -&gt;
                </span>
              )}

            </button>

            {/* SECURITY */}
            <div className="login-security">

              <span className="login-security-dot"></span>

              <span>
                Protected enterprise workspace
              </span>

            </div>

            {/* FOOTER */}
            <div className="login-footer">

              <span>
                Cyond
              </span>

              <span>
                Secure enterprise workspace
              </span>

              <span>
                Waterproofing Diagnosis &amp; Repair Experts
              </span>

            </div>

          </form>

        </div>

      </div>
    </div>
  );
}


/* ============================================================
   DASHBOARD
============================================================ */

function Dashboard() {
  const navigate = useNavigate();

  const [data, setData] =
    useState<DashboardSummary | null>(null);

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState("");

  useEffect(() => {
    getDashboardSummary()
      .then(setData)
      .catch((err) => {
        console.error(err);

        setError(
          err instanceof Error
            ? err.message
            : "Unable to load dashboard data from the backend.",
        );
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="page-loading">

        <div className="loading-spinner"></div>

        <strong>
          Loading workforce dashboard
        </strong>

        <span>
          Connecting to Cyond services...
        </span>

      </div>
    );
  }

  if (error) {
    return (
      <div className="page-heading">

        <div>

          <p className="eyebrow">
            OVERVIEW
          </p>

          <h2>
            Workforce Dashboard
          </h2>

          <p>
            {error}
          </p>

        </div>

      </div>
    );
  }

  if (!data) {
    return null;
  }

  const workforceRate =
    data.totalEmployees > 0
      ? Math.round(
          (data.activeEmployees /
            data.totalEmployees) *
            100,
        )
      : 0;

  return (
    <div className="dashboard-page">

      <div className="dashboard-hero">

        <div className="dashboard-hero-copy">

          <p className="eyebrow">
            WORKFORCE OVERVIEW
          </p>

          <h2>
            Good to see you.
          </h2>

          <p>
            Monitor workforce activity, employee lifecycle
            operations, leave and payroll readiness from one place.
          </p>

        </div>

        <div className="dashboard-actions">

          <button
            className="secondary-button"
            onClick={() =>
              navigate("/employees")
            }
          >
            View Employees
          </button>

          <button
            className="primary-button"
            onClick={() =>
              navigate("/employees")
            }
          >
            + Add Employee
          </button>

        </div>

      </div>

      <div className="dashboard-kpi-grid">

        <article className="dashboard-kpi">

          <div className="dashboard-kpi-top">

            <span className="dashboard-kpi-icon">
              W
            </span>

            <span className="dashboard-kpi-label">
              Workforce
            </span>

          </div>

          <strong>
            {data.totalEmployees.toLocaleString()}
          </strong>

          <p>
            Total employees
          </p>

          <span className="dashboard-kpi-meta">
            {data.activeEmployees.toLocaleString()}
            {" "}currently active
          </span>

        </article>


        <article className="dashboard-kpi">

          <div className="dashboard-kpi-top">

            <span className="dashboard-kpi-icon">
              A
            </span>

            <span className="dashboard-kpi-label">
              Active Rate
            </span>

          </div>

          <strong>
            {workforceRate}%
          </strong>

          <p>
            Workforce availability
          </p>

          <span className="dashboard-kpi-meta">
            {data.activeEmployees.toLocaleString()}
            {" "}active employees
          </span>

        </article>


        <article className="dashboard-kpi">

          <div className="dashboard-kpi-top">

            <span className="dashboard-kpi-icon">
              L
            </span>

            <span className="dashboard-kpi-label">
              Leave Requests
            </span>

          </div>

          <strong>
            {data.pendingLeaveRequests.toLocaleString()}
          </strong>

          <p>
            Awaiting action
          </p>

          <span className="dashboard-kpi-meta">
            {data.leaveRequests.toLocaleString()}
            {" "}total requests
          </span>

        </article>


        <article className="dashboard-kpi">

          <div className="dashboard-kpi-top">

            <span className="dashboard-kpi-icon">
              P
            </span>

            <span className="dashboard-kpi-label">
              Payroll
            </span>

          </div>

          <strong>
            {data.paidPayrollRecords ===
            data.payrollRecords
              ? "Ready"
              : "Processing"}
          </strong>

          <p>
            Payroll processing status
          </p>

          <span className="dashboard-kpi-meta">
            {data.paidPayrollRecords.toLocaleString()}
            {" "}of{" "}
            {data.payrollRecords.toLocaleString()}
            {" "}records paid
          </span>

        </article>

      </div>


      <div className="dashboard-grid">

        <section className="panel dashboard-workforce-panel">

          <div className="panel-header">

            <div>

              <p className="eyebrow">
                WORKFORCE HEALTH
              </p>

              <h3>
                People Operations
              </h3>

              <p>
                Current workforce lifecycle and attendance activity.
              </p>

            </div>

            <button
              className="panel-link"
              onClick={() =>
                navigate("/employees")
              }
            >
              Manage employees
            </button>

          </div>


          <div className="department-list">

            <Department
              name="Active Employees"
              count={data.activeEmployees.toLocaleString()}
              percent={`${workforceRate}%`}
            />

            <Department
              name="Pending Onboarding"
              count={data.pendingOnboarding.toLocaleString()}
              percent={
                data.totalEmployees > 0
                  ? `${Math.min(
                      100,
                      Math.round(
                        (data.pendingOnboarding /
                          data.totalEmployees) *
                          100,
                      ),
                    )}%`
                  : "0%"
              }
            />

            <Department
              name="Pending Offboarding"
              count={data.pendingOffboarding.toLocaleString()}
              percent={
                data.totalEmployees > 0
                  ? `${Math.min(
                      100,
                      Math.round(
                        (data.pendingOffboarding /
                          data.totalEmployees) *
                          100,
                      ),
                    )}%`
                  : "0%"
              }
            />

            <Department
              name="Attendance Events"
              count={data.attendanceEvents.toLocaleString()}
              percent={
                data.totalEmployees > 0
                  ? `${Math.min(
                      100,
                      Math.round(
                        (data.attendanceEvents /
                          Math.max(
                            data.totalEmployees,
                            1,
                          )) *
                          100,
                      ),
                    )}%`
                  : "0%"
              }
            />

          </div>

        </section>


        <section className="panel ai-panel">

          <div className="panel-header">

            <div>

              <p className="eyebrow">
                AI WORKFORCE INTELLIGENCE
              </p>

              <h3>
                AI Insights
              </h3>

              <p>
                Analyze attrition risk, engagement, skill gaps
                and workforce trends.
              </p>

            </div>

          </div>


          <div className="ai-highlight">

            <strong>
              {data.totalEmployees.toLocaleString()}
            </strong>

            <span>
              Employees in workforce
            </span>

          </div>


          <NavLink
            className="primary-button"
            to="/ai"
          >
            Open AI Insights
          </NavLink>

        </section>

      </div>


      <section className="panel activity-panel">

        <div className="panel-header">

          <div>

            <h3>
              HR Platform Status
            </h3>

            <p>
              Live backend service metrics
            </p>

          </div>

        </div>


        <div className="activity-list">

          <div className="activity-item">

            <strong>
              Attendance
            </strong>

            <span>
              {data.attendanceEvents.toLocaleString()}
              {" "}events recorded
            </span>

          </div>


          <div className="activity-item">

            <strong>
              Leave Management
            </strong>

            <span>
              {data.pendingLeaveRequests.toLocaleString()}
              {" "}requests pending
            </span>

          </div>


          <div className="activity-item">

            <strong>
              Payroll
            </strong>

            <span>
              {data.paidPayrollRecords.toLocaleString()}
              {" "}of{" "}
              {data.payrollRecords.toLocaleString()}
              {" "}records paid
            </span>

          </div>

        </div>

      </section>

    </div>
  );
}


/* ============================================================
   STAT
============================================================ */

function Stat({
  title,
  value,
  change,
  icon,
}: {
  title: string;
  value: string;
  change: string;
  icon: string;
}) {
  return (
    <div className="stat-card">

      <div className="stat-icon">
        {icon}
      </div>

      <p>
        {title}
      </p>

      <h3>
        {value}
      </h3>

      <span>
        {change}
      </span>

    </div>
  );
}


/* ============================================================
   DEPARTMENT
============================================================ */

function Department({
  name,
  count,
  percent,
}: {
  name: string;
  count: string;
  percent: string;
}) {
  return (
    <div className="department-row">

      <div className="department-info">

        <strong>
          {name}
        </strong>

        <span>
          {count} employees
        </span>

      </div>

      <div className="bar">

        <div
          style={{
            width: percent,
          }}
        ></div>

      </div>

      <strong>
        {percent}
      </strong>

    </div>
  );
}


/* ============================================================
   AI INSIGHTS
============================================================ */

function AIInsights() {
  const [data, setData] =
    useState<AiWorkforceDashboardResponse | null>(
      null,
    );

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState("");

  const loadAiDashboard = async () => {
    try {
      setLoading(true);
      setError("");

      const response =
        await getAiWorkforceDashboard();

      setData(response);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to load AI workforce intelligence.",
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadAiDashboard();
  }, []);

  return (
    <>
      <div className="page-heading">

        <div>

          <p className="eyebrow">
            ARTIFICIAL INTELLIGENCE
          </p>

          <h2>
            AI Workforce Intelligence
          </h2>

          <p>
            Predictive insights to support better workforce decisions.
          </p>

        </div>

        <button
          className="secondary-button"
          type="button"
          onClick={() =>
            void loadAiDashboard()
          }
          disabled={loading}
        >
          {loading
            ? "Refreshing..."
            : "Refresh"}
        </button>

      </div>


      {error && (
        <section className="panel">

          <strong>
            AI data unavailable
          </strong>

          <p>
            {error}
          </p>

        </section>
      )}


      <div className="stats-grid">

        <Stat
          title="High Attrition Risk"
          value={
            data
              ? data.highAttritionRisk.toLocaleString()
              : "0"
          }
          change="Requires attention"
          icon="!"
        />

        <Stat
          title="Medium Attrition Risk"
          value={
            data
              ? data.mediumAttritionRisk.toLocaleString()
              : "0"
          }
          change="Monitor"
          icon="M"
        />

        <Stat
          title="Avg Engagement"
          value={
            data
              ? `${data.averageEngagementScore.toFixed(1)}%`
              : "0"
          }
          change="Workforce-wide score"
          icon="E"
        />

        <Stat
          title="Active Employees"
          value={
            data
              ? data.activeEmployees.toLocaleString()
              : "0"
          }
          change={
            data
              ? `${data.inactiveEmployees} inactive`
              : "Workforce status"
          }
          icon="A"
        />

      </div>


      <section className="panel">

        <div className="panel-header">

          <div>

            <h3>
              AI Workforce Recommendations
            </h3>

            <p>
              Live workforce risk indicators generated
              from the AI analytics service.
            </p>

          </div>

        </div>


        {loading && !data ? (

          <div className="empty-state">
            Loading AI workforce intelligence...
          </div>

        ) : data?.employeeInsights?.length ? (

          <div className="recommendations-list">

            {data.employeeInsights
              .slice()
              .sort(
                (a, b) =>
                  b.attritionRisk -
                  a.attritionRisk,
              )
              .slice(0, 5)
              .map((employee) => (

                <div
                  className="recommendation"
                  key={employee.employeeId}
                >

                  <div className="recommendation-icon">
                    {employee.riskBand === "HIGH"
                      ? "!"
                      : "AI"}
                  </div>

                  <div>

                    <strong>
                      {employee.employeeName}
                    </strong>

                    <p>
                      {employee.department}
                      {" "}
                      {(employee.attritionRisk * 100).toFixed(0)}
                      % Attrition Risk
                      {" "}
                      Engagement{" "}
                      {employee.engagementScore.toFixed(1)}
                      %
                    </p>

                  </div>

                  <span
                    className={
                      employee.riskBand === "HIGH"
                        ? "badge high"
                        : "badge"
                    }
                  >
                    {employee.riskBand}
                  </span>

                </div>

              ))}

          </div>

        ) : (

          <div className="empty-state">
            No employee AI insights are currently available.
          </div>

        )}

      </section>
    </>
  );
}


/* ============================================================
   APP
============================================================ */

export default function App() {
  return (
    <BrowserRouter>

      <Routes>

        <Route
          path="/login"
          element={<Login />}
        />

        <Route
          path="*"
          element={
            <ProtectedRoute>
              <AppRoutes />
            </ProtectedRoute>
          }
        />

      </Routes>

    </BrowserRouter>
  );
}
