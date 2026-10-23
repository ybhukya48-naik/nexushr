import { Suspense, lazy, useEffect, useState } from "react";
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
  getCurrentEmployee,
  getEmployeePayroll,
  getMyAttendanceStatus,
  createPassword,
  login as apiLogin,
  getAiWorkforceDashboard,
  fetchEmployeePhoto,
  API_BASE,
  type AiWorkforceDashboardResponse,
  type EmployeeResponse,
} from "./api";
import {
  getEmployeeAttendance,
  getLeaves,
  sendAttendanceHeartbeat,
  type AttendanceRecord,
} from "./pages/Attendance";
import {
  asWorkspaceCompany,
  clearWorkspaceCompany,
  getWorkspaceCompany,
  setWorkspaceCompany,
  workspaceLogo,
  workspaceName,
  workspaceTagline,
  type WorkspaceCompany,
} from "./utils/workspace";
const Dashboard = lazy(() => import("./pages/Dashboard"));
const Employees = lazy(() => import("./pages/Employees"));
const EmployeeProfile = lazy(() => import("./pages/EmployeeProfile"));
const Attendance = lazy(() => import("./pages/Attendance"));
const Leave = lazy(() => import("./pages/Leave"));
const Payroll = lazy(() => import("./pages/Payroll"));
const Performance = lazy(() => import("./pages/Performance"));
const Notifications = lazy(() => import("./pages/Notifications"));
const Admin = lazy(() => import("./pages/Admin"));
const SiteAttendance = lazy(() => import("./pages/SiteAttendance"));

const navItems: ReadonlyArray<
  readonly [string, string, readonly string[]]
> = [
  ["Dashboard", "/", ["ADMIN", "HR", "MANAGER", "SUPER_ADMIN"]],
  ["Employees", "/employees", ["ADMIN", "HR", "MANAGER", "SUPER_ADMIN"]],
  ["Attendance", "/attendance", ["ADMIN", "HR", "MANAGER", "EMPLOYEE", "SUPER_ADMIN"]],
  ["Leave", "/leave", ["ADMIN", "HR", "MANAGER", "EMPLOYEE", "SUPER_ADMIN"]],
  ["Payroll", "/payroll", ["ADMIN", "HR", "MANAGER", "EMPLOYEE", "SUPER_ADMIN"]],
  ["Performance", "/performance", ["ADMIN", "HR", "MANAGER", "EMPLOYEE", "SUPER_ADMIN"]],
  ["AI Insights", "/ai", ["ADMIN", "HR", "MANAGER", "SUPER_ADMIN"]],
  ["Notifications", "/notifications", ["ADMIN", "HR", "MANAGER", "EMPLOYEE", "SUPER_ADMIN"]],
  ["Admin", "/admin", ["ADMIN", "SUPER_ADMIN"]],
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

  const [workspace, setWorkspace] = useState<WorkspaceCompany | null>(
    getWorkspaceCompany(),
  );
  const [attendanceAlert, setAttendanceAlert] = useState("");
  const [me, setMe] = useState<EmployeeResponse | null>(null);
  const [myPhoto, setMyPhoto] = useState("");

  useEffect(() => {
    let cancelled = false;
    getCurrentEmployee()
      .then((employee) => {
        if (!cancelled) {
          setMe(employee);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setMe(null);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [workspace]);

  useEffect(() => {
    if (!me?.hasProfilePhoto) {
      setMyPhoto("");
      return;
    }
    let cancelled = false;
    let objectUrl = "";
    fetchEmployeePhoto(me.id)
      .then((url) => {
        if (!cancelled && url) {
          objectUrl = url;
          setMyPhoto(url);
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [me]);

  useEffect(() => {
    const sync = () => setWorkspace(getWorkspaceCompany());
    window.addEventListener("nexushr-company", sync);
    return () => window.removeEventListener("nexushr-company", sync);
  }, []);

  useEffect(() => {
    let cancelled = false;

    function loadStatus() {
      getMyAttendanceStatus()
        .then((status) => {
          if (cancelled) {
            return;
          }
          setAttendanceAlert(status.status === "INACTIVE" ? status.message : "");
        })
        .catch(() => {
          if (!cancelled) {
            setAttendanceAlert("");
          }
        });
    }

    loadStatus();
    const timer = window.setInterval(loadStatus, 60000);
    const heartbeat = window.setInterval(() => {
      if (document.visibilityState !== "visible" || !document.hasFocus()) {
        return;
      }
      void sendAttendanceHeartbeat().catch(() => undefined);
    }, 60000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
      window.clearInterval(heartbeat);
    };
  }, [workspace]);

  function handleLogout() {
    // Logging out only clears this browser session. It does not delete attendance.
    void fetch(`${API_BASE}/auth/logout`, { method: "POST" }).catch(() => undefined);
    localStorage.removeItem("nexushr_token");
    localStorage.removeItem("nexushr_user");
    localStorage.removeItem("nexushr_role");
    clearWorkspaceCompany();
    navigate("/login", { replace: true });
  }

  function enterWorkspace(company: WorkspaceCompany) {
    setWorkspaceCompany(company);
    setWorkspace(company);
  }

  if (!workspace) {
    return (
      <div className="login-page">
        <div className="login-form-area" style={{ margin: "auto" }}>
          <div className="login-card entity-picker-card">
            <div className="login-card-header">
              <p className="eyebrow">SEPARATE WORKSPACES</p>
              <h2>Choose a company</h2>
              <p>
                CYOND and Gorle Group are separate. Open one workspace.
              </p>
            </div>
            <div className="entity-choice">
              <button
                type="button"
                className="entity-card"
                onClick={() => enterWorkspace("CYOND")}
              >
                <img src={workspaceLogo("CYOND")} alt="CYOND logo" />
                <strong>CYOND</strong>
                <span>{workspaceTagline("CYOND")}</span>
              </button>
              <button
                type="button"
                className="entity-card"
                onClick={() => enterWorkspace("GORLE")}
              >
                <img src={workspaceLogo("GORLE")} alt="Gorle Group logo" />
                <strong>Gorle Group</strong>
                <span>{workspaceTagline("GORLE")}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  const canSwitchWorkspace = role === "ADMIN" || role === "SUPER_ADMIN" || role === "HR" || role === "MANAGER";

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <img
            className="brand-logo"
            src={workspaceLogo(workspace)}
            alt={`${workspaceName(workspace)} logo`}
          />
          <div>
            <strong>{workspaceName(workspace)}</strong>
            <span>{workspaceTagline(workspace)}</span>
          </div>
        </div>
        {canSwitchWorkspace && (
          <label className="sidebar-company">
            <span>Entity</span>
            <select
              value={workspace}
              onChange={(event) =>
                enterWorkspace(asWorkspaceCompany(event.target.value))
              }
            >
              <option value="CYOND">CYOND</option>
              <option value="GORLE">Gorle Group</option>
            </select>
          </label>
        )}

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
          <div className={attendanceAlert ? "status-dot status-dot-alert" : "status-dot"}></div>
          <div>
            <strong>{attendanceAlert ? "Attendance inactive" : "System Online"}</strong>
            <span>{attendanceAlert || "All services operational"}</span>
          </div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div>
            <p className="eyebrow">{workspaceName(workspace).toUpperCase()}</p>
            <h1>{workspaceName(workspace)}</h1>
          </div>

          <div className="user-area">
            <button
              className="notification-button"
              onClick={() => navigate("/notifications")}
              aria-label="Notifications"
            >
              <span aria-hidden="true">🔔</span>
            </button>

            <button
              type="button"
              className="user-profile-link"
              onClick={() => navigate("/profile")}
            >
              <div className="avatar">
                {myPhoto ? (
                  <img src={myPhoto} alt="" />
                ) : (
                  (me?.fullName || username).slice(0, 2).toUpperCase()
                )}
              </div>
              <div>
                <strong>{me?.fullName || username}</strong>
                <span>{me?.designation || role}</span>
              </div>
            </button>

            <button
              className="primary-button"
              onClick={handleLogout}
            >
              Logout
            </button>
          </div>
        </header>

        <section className="content" key={workspace}>
          <Suspense
            fallback={
              <div className="page-loading">
                <div className="loading-spinner"></div>
                <strong>Loading page</strong>
                <span>Fetching module assets...</span>
              </div>
            }
          >
            <Routes>
              <Route
                path="/"
                element={
                  role === "EMPLOYEE" ? (
                    <EmployeeDashboard />
                  ) : (
                    <Dashboard />
                  )
                }
              />
              <Route path="/profile" element={<EmployeeProfile />} />
              <Route path="/employees" element={<Employees />} />
              <Route path="/employees/:id" element={<EmployeeProfile />} />
              <Route path="/attendance" element={<Attendance />} />
              <Route path="/leave" element={<Leave />} />
              <Route path="/payroll" element={<Payroll />} />
              <Route path="/performance" element={<Performance />} />
              <Route path="/ai" element={<AIInsights />} />
              <Route path="/notifications" element={<Notifications />} />
              <Route
                path="/admin"
                element={role === "ADMIN" ? <Admin /> : <Navigate to="/" replace />}
              />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </Suspense>
        </section>
      </main>
    </div>
  );
}
function Login() {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [mode, setMode] = useState<"signin" | "create">("signin");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setNotice("");

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

      clearWorkspaceCompany();
      navigate("/", { replace: true });
      if (response.role === "EMPLOYEE") {
        void getCurrentEmployee()
          .then((me) => setWorkspaceCompany(asWorkspaceCompany(me.companyCode)))
          .catch(() => {
            // The employee can still open a workspace if this lookup is slow.
          });
      }
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

  async function handleCreatePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setNotice("");

    const email = username.trim();
    if (!email || !password) {
      setError("Enter your email and a new password.");
      return;
    }
    if (password.length < 8) {
      setError("Password must contain at least 8 characters.");
      return;
    }
    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);
    try {
      const response = await createPassword(email, password);
      setUsername(response.email || email);
      setPassword("");
      setConfirmPassword("");
      setMode("signin");
      setNotice("Password created. Sign in with your email and password.");
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Could not create the password.",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-background-orb login-orb-one"></div>

      <div className="login-background-orb login-orb-two"></div>

      <div className="login-layout login-layout-single">

        {/* =====================================================
            LOGIN FORM
        ====================================================== */}
        <div className="login-form-area">

          <form
            className="login-card"
            onSubmit={mode === "create" ? handleCreatePassword : handleSubmit}
          >

            <div className="login-card-brand">
              <img
                src="/images/gorle-group-logo.jpeg"
                alt="Gorle Group logo"
              />
              <strong>GORLE GROUP BUSINESS SOLUTION</strong>
            </div>

            <div className="login-card-header">

              <h2>
                {mode === "create" ? "Create password" : "Welcome back"}
              </h2>

              <p>
                {mode === "create"
                  ? "Employees use their email to create a password, then sign in."
                  : "Sign in with your email and password."}
              </p>

            </div>

            {notice && (
              <p className="login-notice">{notice}</p>
            )}

            {/* ERROR MESSAGE */}
            {error && (
              <div className="login-error">

                <div className="login-error-icon">
                  !
                </div>

                <div>

                  <strong>
                    {mode === "create" ? "Could not create password" : "Sign-in failed"}
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
                  {mode === "create" ? "Email" : "Email or employee code"}
                </span>

                <div className="login-input-wrap">

                  <input
                    type={mode === "create" ? "email" : "text"}
                    value={username}
                    onChange={(event) =>
                      setUsername(event.target.value)
                    }
                    placeholder={
                      mode === "create"
                        ? "Enter your email"
                        : "Enter email or employee code"
                    }
                    autoComplete="username"
                    required
                  />

                </div>

              </label>

              {/* PASSWORD */}
              <label>

                <span>
                  {mode === "create" ? "New password" : "Password"}
                </span>

                <div className="login-input-wrap">

                  <input
                    type="password"
                    value={password}
                    onChange={(event) =>
                      setPassword(event.target.value)
                    }
                    placeholder={
                      mode === "create"
                        ? "At least 8 characters"
                        : "Enter your password"
                    }
                    autoComplete={mode === "create" ? "new-password" : "current-password"}
                    required
                  />

                </div>

              </label>

              {mode === "create" && (
                <label>
                  <span>Confirm password</span>
                  <div className="login-input-wrap">
                    <input
                      type="password"
                      value={confirmPassword}
                      onChange={(event) =>
                        setConfirmPassword(event.target.value)
                      }
                      placeholder="Repeat the password"
                      autoComplete="new-password"
                      required
                    />
                  </div>
                </label>
              )}

            </div>

            {/* LOGIN BUTTON */}
            <button
              className="login-submit"
              type="submit"
              disabled={loading}
            >

              <span>
                {loading
                  ? mode === "create"
                    ? "Creating password..."
                    : "Signing in..."
                  : mode === "create"
                    ? "Create password"
                    : "Sign in"}
              </span>

              {!loading && (
                <span className="login-submit-arrow">
                  -&gt;
                </span>
              )}

            </button>

            <button
              className="login-switch"
              type="button"
              onClick={() => {
                setMode(mode === "create" ? "signin" : "create");
                setError("");
                setNotice("");
                setPassword("");
                setConfirmPassword("");
              }}
            >
              {mode === "create"
                ? "Already created a password? Sign in"
                : "Employee? Create your password"}
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
                Gorle Group Business Solution
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


function formatClock(value?: string) {
  if (!value) {
    return "—";
  }
  const match = String(value).match(/(\d{2}):(\d{2})/);
  return match ? `${match[1]}:${match[2]}` : value;
}

function EmployeeDashboard() {
  const navigate = useNavigate();
  const [employee, setEmployee] =
    useState<EmployeeResponse | null>(null);
  const [attendance, setAttendance] = useState<AttendanceRecord | null>(null);
  const [leaveCount, setLeaveCount] = useState(0);
  const [latestPay, setLatestPay] = useState("");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    getCurrentEmployee()
      .then(async (me) => {
        setEmployee(me);
        const [records, leaves, payroll] = await Promise.all([
          getEmployeeAttendance(me.id).catch(() => []),
          getLeaves().catch(() => []),
          getEmployeePayroll(me.id).catch(() => []),
        ]);
        const latest = [...records].sort((a, b) =>
          b.attendanceDate.localeCompare(a.attendanceDate),
        )[0];
        setAttendance(latest ?? null);
        setLeaveCount(leaves.length);
        const pay = [...payroll].sort((a, b) =>
          b.payMonth.localeCompare(a.payMonth),
        )[0];
        setLatestPay(pay ? pay.payMonth : "No payslip yet");
      })
      .catch((err) => {
        console.error(err);

        setError(
          err instanceof Error
            ? err.message
            : "Unable to load your employee details.",
        );
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="page-loading">
        <div className="loading-spinner"></div>

        <strong>
          Loading your dashboard
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
            MY DASHBOARD
          </p>

          <h2>
            Unable to load your details
          </h2>

          <p>
            {error}
          </p>
        </div>
      </div>
    );
  }

  if (!employee) {
    return null;
  }

  return (
    <div className="dashboard-page employee-dashboard">

      <div className="dashboard-hero">
        <div className="dashboard-hero-copy">

          <p className="eyebrow">
            MY DASHBOARD
          </p>

          <h2>
            Welcome, {employee.fullName}
          </h2>

          <p>
            Open attendance, leave, payroll, or your profile.
          </p>

        </div>
      </div>

      <div className="employee-action-grid">
        <article className="panel employee-action-card">
          <p className="eyebrow">ATTENDANCE</p>
          <h3>Today's punches</h3>
          <p>
            {attendance
              ? `${attendance.attendanceDate} · In ${formatClock(attendance.checkInTime)} · Out ${formatClock(attendance.checkOutTime)} · ${Math.floor((attendance.closedWorkMinutes ?? attendance.workMinutes ?? 0) / 60)}h ${(attendance.closedWorkMinutes ?? attendance.workMinutes ?? 0) % 60}m`
              : "No attendance recorded yet."}
          </p>
          <button className="primary-button" type="button" onClick={() => navigate("/attendance")}>
            Check in or check out
          </button>
        </article>
        <article className="panel employee-action-card">
          <p className="eyebrow">LEAVE</p>
          <h3>My requests</h3>
          <p>{leaveCount} leave request{leaveCount === 1 ? "" : "s"} on file.</p>
          <button className="primary-button" type="button" onClick={() => navigate("/leave")}>
            Submit or view leave
          </button>
        </article>
        <article className="panel employee-action-card">
          <p className="eyebrow">PAYROLL</p>
          <h3>Payslip</h3>
          <p>{latestPay}</p>
          <button className="primary-button" type="button" onClick={() => navigate("/payroll")}>
            View payslip
          </button>
        </article>
        <article className="panel employee-action-card">
          <p className="eyebrow">ALERTS</p>
          <h3>Notifications</h3>
          <p>Missing check-in or check-out is marked inactive and sent here.</p>
          <button className="primary-button" type="button" onClick={() => navigate("/notifications")}>
            Open notifications
          </button>
        </article>
      </div>

      <section className="panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">PROFILE</p>
            <h3>{employee.designation || "Employee"}</h3>
            <p>Your details, designation, and profile picture are on your profile.</p>
          </div>
          <button className="primary-button" type="button" onClick={() => navigate("/profile")}>
            Open profile
          </button>
        </div>
      </section>

    </div>
  );
}
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
      <div className="stat-icon">{icon}</div>
      <p>{title}</p>
      <h3>{value}</h3>
      <span>{change}</span>
    </div>
  );
}

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
              AI Data Freshness
            </h3>

            <p>
              This page now shows only aggregated data generated from real employee records.
            </p>

          </div>

        </div>

        <div className="empty-state">
          {data?.generatedAt
            ? `Last generated at: ${data.generatedAt}`
            : "AI data will appear after backend analytics runs."}
        </div>

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
          path="/site"
          element={
            <Suspense fallback={<div className="page-loading">Loading site attendance</div>}>
              <SiteAttendance />
            </Suspense>
          }
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





