import { Component, Suspense, lazy, useEffect, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import {
  BrowserRouter,
  NavLink,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import {
  getCurrentEmployee,
  getEmployeeNotifications,
  getEmployeePayroll,
  getHolidays,
  getMyAttendanceStatus,
  register as apiRegister,
  login as apiLogin,
  getAiWorkforceDashboard,
  fetchEmployeePhoto,
  API_BASE,
  beginWebsiteSession,
  clearWebsiteSession,
  websiteSessionActive,
  type AiWorkforceDashboardResponse,
  type EmployeeResponse,
  type NotificationResponse,
} from "./api";
import {
  checkIn,
  checkOut,
  getAttendanceAudit,
  getEmployeeAttendance,
  getLeaveBalance,
  getLeaves,
  sendAttendanceHeartbeat,
  type AttendanceAuditEntry,
  type LeaveRequest,
  clockedWorkMinutes,
  isCheckoutMissing,
  isWindowsComputer,
  monthDateKeys,
  readDashboardSnapshot,
  shownCheckOut,
  writeDashboardSnapshot,
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
  ["Dashboard", "/", ["ADMIN", "HR", "MANAGER", "EMPLOYEE", "SUPER_ADMIN"]],
  ["Employees", "/employees", ["ADMIN", "HR", "MANAGER", "SUPER_ADMIN"]],
  ["Attendance", "/attendance", ["ADMIN", "HR", "MANAGER", "EMPLOYEE", "SUPER_ADMIN"]],
  ["Leave", "/leave", ["ADMIN", "HR", "MANAGER", "EMPLOYEE", "SUPER_ADMIN"]],
  ["Payroll", "/payroll", ["ADMIN", "HR", "MANAGER", "EMPLOYEE", "SUPER_ADMIN"]],
  ["Performance", "/performance", ["ADMIN", "HR", "MANAGER", "EMPLOYEE", "SUPER_ADMIN"]],
  ["AI Insights", "/ai", ["ADMIN", "HR", "MANAGER", "SUPER_ADMIN"]],
  ["Notifications", "/notifications", ["ADMIN", "HR", "MANAGER", "EMPLOYEE", "SUPER_ADMIN"]],
  ["Profile", "/profile", ["ADMIN", "HR", "MANAGER", "EMPLOYEE", "SUPER_ADMIN"]],
  ["Admin", "/admin", ["ADMIN", "SUPER_ADMIN"]],
] as const;

function isAuthenticated() {
  return websiteSessionActive();
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
  const [bellOpen, setBellOpen] = useState(false);
  const [bellItems, setBellItems] = useState<NotificationResponse[]>([]);

  useEffect(() => {
    const watch = window.setInterval(() => {
      if (!websiteSessionActive()) {
        navigate("/login", { replace: true });
      }
    }, 15000);
    return () => window.clearInterval(watch);
  }, [navigate]);

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
    clearWebsiteSession();
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
              {label === "Dashboard"
                ? "Home"
                : label === "Attendance"
                  ? role === "EMPLOYEE" ? "My Attendance" : "Attendance"
                  : label === "Leave"
                    ? role === "EMPLOYEE" ? "My Leave" : "Leave"
                    : label === "Payroll"
                      ? role === "EMPLOYEE" ? "My Pay" : "Payroll"
                      : label === "Performance"
                        ? role === "EMPLOYEE" ? "My Reviews" : "Performance"
                        : label === "AI Insights"
                          ? "Reports"
                          : label === "Admin"
                            ? "Settings"
                            : label === "Profile"
                              ? "My Profile"
                              : label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className={attendanceAlert ? "status-dot status-dot-alert" : "status-dot"}></div>
          <div>
            <strong>{attendanceAlert ? "Check in needed" : "Signed in"}</strong>
            <span>{attendanceAlert || me?.fullName || "Your account is active"}</span>
          </div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div>
            <h1>{workspaceName(workspace)}</h1>
          </div>

          <div className="user-area">
            <div className="bell-wrap">
              <button
                className="notification-button"
                type="button"
                onClick={() => {
                  const next = !bellOpen;
                  setBellOpen(next);
                  if (next && me?.id) {
                    void getEmployeeNotifications(me.id)
                      .then((items) => setBellItems(Array.isArray(items) ? items.slice(0, 8) : []))
                      .catch(() => setBellItems([]));
                  }
                }}
                aria-label="Notifications"
                aria-expanded={bellOpen}
              >
                <span aria-hidden="true">🔔</span>
              </button>
              {bellOpen && (
                <div className="bell-menu">
                  {bellItems.length === 0 ? (
                    <p>No updates yet.</p>
                  ) : (
                    bellItems.map((item) => (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => {
                          setBellOpen(false);
                          navigate("/notifications");
                        }}
                      >
                        <strong>{item.title}</strong>
                        <span>{item.message}</span>
                      </button>
                    ))
                  )}
                  <button
                    type="button"
                    className="bell-all"
                    onClick={() => {
                      setBellOpen(false);
                      navigate("/notifications");
                    }}
                  >
                    See all updates
                  </button>
                </div>
              )}
            </div>

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
              className="secondary-button"
              onClick={handleLogout}
            >
              Logout
            </button>
          </div>
        </header>

        <section className="content" key={workspace}>
          <PageGuard>
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
          </PageGuard>
        </section>
      </main>
    </div>
  );
}
function Login() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [username, setUsername] = useState("");
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [employeeCode, setEmployeeCode] = useState("");
  const [companyCode, setCompanyCode] = useState<"CYOND" | "GORLE">("CYOND");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [mode, setMode] = useState<"signin" | "create">(
    searchParams.get("create") === "1" ? "create" : "signin",
  );
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
      beginWebsiteSession();

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

  async function handleCreateProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setNotice("");

    const email = username.trim();
    const mobile = phone.trim();
    if (!fullName.trim()) {
      setError("Enter your name.");
      return;
    }
    if (!email) {
      setError("Enter your email address.");
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
      const response = await apiRegister({
        fullName: fullName.trim(),
        email,
        phone: mobile || undefined,
        password,
        companyCode,
        employeeCode: employeeCode.trim() || undefined,
      });
      setUsername(email);
      setPassword("");
      setConfirmPassword("");
      setEmployeeCode("");
      setMode("signin");
      setNotice(
        response.employeeCode
          ? `${response.message} Email: ${response.email}. Employee code: ${response.employeeCode}.`
          : response.message,
      );
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Could not create the profile.",
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
            onSubmit={mode === "create" ? handleCreateProfile : handleSubmit}
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
                {mode === "create" ? "Create your profile" : "Welcome back"}
              </h2>

              <p>
                {mode === "create"
                  ? "Enter your employee code, your own email, and a password. Admin and manager keep their role. Then sign in with that same email and password."
                  : "Sign in with your own email and password. Admin and manager save that email once, using their employee code."}
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
                    {mode === "create" ? "Could not create profile" : "Sign-in failed"}
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
              {mode === "create" && (
                <label>
                  <span>Full name</span>
                  <div className="login-input-wrap">
                    <input
                      type="text"
                      value={fullName}
                      onChange={(event) => setFullName(event.target.value)}
                      placeholder="Your name"
                      autoComplete="name"
                      required
                    />
                  </div>
                </label>
              )}

              {mode === "create" && (
                <label>
                  <span>Phone number</span>
                  <div className="login-input-wrap">
                    <input
                      type="tel"
                      value={phone}
                      onChange={(event) => setPhone(event.target.value)}
                      placeholder="10-digit mobile number"
                      autoComplete="tel"
                    />
                  </div>
                </label>
              )}

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
                        ? "name@company.com"
                        : "Email or employee code"
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
                        : "Your password"
                    }
                    autoComplete={mode === "create" ? "new-password" : "current-password"}
                    required
                  />

                </div>

              </label>

              {mode === "create" && (
                <label>
                  <span>Employee code</span>
                  <div className="login-input-wrap">
                    <input
                      type="text"
                      value={employeeCode}
                      onChange={(event) => setEmployeeCode(event.target.value)}
                      placeholder="Your employee code"
                      autoComplete="off"
                    />
                  </div>
                </label>
              )}

              {mode === "create" && (
                <label>
                  <span>Company</span>
                  <div className="login-input-wrap">
                    <select
                      value={companyCode}
                      onChange={(event) =>
                        setCompanyCode(event.target.value === "GORLE" ? "GORLE" : "CYOND")
                      }
                    >
                      <option value="CYOND">CYOND</option>
                      <option value="GORLE">Gorle Group</option>
                    </select>
                  </div>
                </label>
              )}

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
                    ? "Creating profile..."
                    : "Signing in..."
                  : mode === "create"
                    ? "Create profile"
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
                ? "Already saved your email? Sign in"
                : "Admin, manager, or employee? Set your own email"}
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

function todayKey() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

function punchPairs(entries: AttendanceAuditEntry[], date: string, record?: AttendanceRecord | null) {
  const pairs: Array<{ checkIn: string; checkOut?: string }> = [];
  const relevant = entries
    .filter((entry) => (entry.attendanceDate ?? "").slice(0, 10) === date
      && (entry.action === "CHECK_IN" || entry.action === "CHECK_OUT"))
    .sort((a, b) => String(a.createdAt ?? "").localeCompare(String(b.createdAt ?? "")));
  for (const entry of relevant) {
    const time = formatClock(entry.detail) === "—" ? formatClock(entry.createdAt) : formatClock(entry.detail);
    if (entry.action === "CHECK_IN") {
      pairs.push({ checkIn: time });
    } else if (pairs.length > 0 && !pairs[pairs.length - 1].checkOut) {
      pairs[pairs.length - 1].checkOut = time;
    }
  }
  if (pairs.length === 0 && record?.checkInTime) {
    pairs.push({
      checkIn: formatClock(record.checkInTime),
      checkOut: record.checkOutTime ? formatClock(record.checkOutTime) : undefined,
    });
  }
  return pairs;
}

function hoursLabel(minutes?: number) {
  const value = Math.max(0, minutes ?? 0);
  return `${Math.floor(value / 60)}h ${value % 60}m`;
}

function EmployeeDashboard() {
  const navigate = useNavigate();
  const cachedDashboard = readDashboardSnapshot();
  const [employee, setEmployee] =
    useState<EmployeeResponse | null>(cachedDashboard?.employee ?? null);
  const [records, setRecords] = useState<AttendanceRecord[]>(cachedDashboard?.records ?? []);
  const [audit, setAudit] = useState<AttendanceAuditEntry[]>([]);
  const [leaveCount, setLeaveCount] = useState(0);
  const [myLeaves, setMyLeaves] = useState<LeaveRequest[]>([]);
  const [leaveDays, setLeaveDays] = useState<number | null>(null);
  const [nextHoliday, setNextHoliday] = useState("None listed");
  const [latestPay, setLatestPay] = useState("");

  const [loading, setLoading] = useState(!cachedDashboard?.employee);
  const [detailsLoading, setDetailsLoading] = useState((cachedDashboard?.records.length ?? 0) === 0);
  const [error, setError] = useState("");
  const [punching, setPunching] = useState(false);
  const [punchNote, setPunchNote] = useState("");

  useEffect(() => {
    let cancelled = false;
    getCurrentEmployee()
      .then((me) => {
        if (cancelled) {
          return;
        }
        setEmployee(me);
        setLoading(false);
        return Promise.all([
          getEmployeeAttendance(me.id).catch(() => []),
          getAttendanceAudit(me.id).catch(() => []),
          getLeaves().catch(() => []),
          getEmployeePayroll(me.id).catch(() => []),
          getLeaveBalance(me.id).catch(() => null),
          getHolidays().catch(() => []),
        ]).then(([history, punches, leaves, payroll, balance, holidays]) => {
          if (cancelled) {
            return;
          }
          const savedHistory = (Array.isArray(history) ? history : []).filter((row) => row?.attendanceDate).sort((a, b) =>
            b.attendanceDate.localeCompare(a.attendanceDate),
          );
          setRecords(savedHistory);
          writeDashboardSnapshot(me, savedHistory);
          setAudit(Array.isArray(punches) ? punches : []);
          setLeaveCount(Array.isArray(leaves) ? leaves.length : 0);
          setMyLeaves(Array.isArray(leaves) ? leaves : []);
          setLeaveDays(balance && typeof balance.remainingDays === "number" ? balance.remainingDays : null);
          const upcoming = (Array.isArray(holidays) ? holidays : [])
            .filter((holiday) => holiday?.holidayDate && holiday.holidayDate >= todayKey())
            .sort((a, b) => a.holidayDate.localeCompare(b.holidayDate))[0];
          setNextHoliday(
            upcoming
              ? `${new Date(`${upcoming.holidayDate}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short" })} · ${upcoming.name}`
              : "None listed",
          );
          const pay = (Array.isArray(payroll) ? payroll : [])
            .filter((item) => item?.payMonth)
            .sort((a, b) => b.payMonth.localeCompare(a.payMonth))[0];
          setLatestPay(pay ? pay.payMonth : "No payslip yet");
        });
      })
      .catch((err) => {
        console.error(err);
        if (!cancelled && !readDashboardSnapshot()?.employee) {
          setError(
            err instanceof Error
              ? err.message
              : "Unable to load your employee details.",
          );
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
          setDetailsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
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
    return (
      <section className="panel">
        <h2>Home</h2>
        <p>Your details did not load. Reload the page to open Home again.</p>
        <button className="primary-button" type="button" onClick={() => window.location.reload()}>Reload</button>
      </section>
    );
  }

  const signedInEmployee = employee;

  async function punchDay() {
    const todayRecord = records.find((row) => String(row.attendanceDate ?? "").slice(0, 10) === todayKey());
    const open = Boolean(todayRecord?.checkInTime && !todayRecord?.checkOutTime);
    setPunching(true);
    setPunchNote("");
    try {
      await (open ? checkOut(signedInEmployee.id) : checkIn(signedInEmployee.id));
      const history = await getEmployeeAttendance(signedInEmployee.id);
      const savedHistory = [...history].sort((a, b) => b.attendanceDate.localeCompare(a.attendanceDate));
      setRecords(savedHistory);
      writeDashboardSnapshot(signedInEmployee, savedHistory);
      setPunchNote(open ? "Checked out." : "Checked in.");
    } catch (err) {
      setPunchNote(
        err instanceof Error
          ? err.message.replace(/^Attendance API failed:\s*/i, "")
          : "Check-in could not be saved.",
      );
    } finally {
      setPunching(false);
    }
  }

  async function punchLunch(kind: "out" | "in") {
    setPunching(true);
    setPunchNote("");
    try {
      await (kind === "out" ? checkOut(signedInEmployee.id) : checkIn(signedInEmployee.id));
      const history = await getEmployeeAttendance(signedInEmployee.id);
      const savedHistory = [...history].sort((a, b) => b.attendanceDate.localeCompare(a.attendanceDate));
      setRecords(savedHistory);
      writeDashboardSnapshot(signedInEmployee, savedHistory);
      setPunchNote(
        kind === "out"
          ? "Lunch started. Working hours are paused."
          : "Lunch ended. Working hours continue.",
      );
    } catch (err) {
      setPunchNote(
        err instanceof Error
          ? err.message.replace(/^Attendance API failed:\s*/i, "")
          : "Lunch punch could not be saved.",
      );
    } finally {
      setPunching(false);
    }
  }

  return (
    <div className="dashboard-page employee-dashboard">

      <div className="dashboard-hero">
        <div className="dashboard-hero-copy">

          <p className="eyebrow">Home</p>
          <h2>Good day, {employee.fullName}</h2>
          <p>
            {new Date().toLocaleDateString("en-IN", { weekday: "short", day: "2-digit", month: "short" })}
            {" · "}General shift 9:30–6:30
            {" · "}Days left {leaveDays ?? "—"}
            {" · "}Next holiday {nextHoliday}
          </p>

        </div>
      </div>

      {(() => {
        const today = todayKey();
        const todayRecord = records.find((row) => String(row.attendanceDate ?? "").slice(0, 10) === today);
        const pairs = punchPairs(audit, today, todayRecord);
        const month = today.slice(0, 7);
        const monthRows = records.filter((row) => String(row.attendanceDate ?? "").startsWith(month));
        const monthMinutes = monthRows.reduce((sum, row) => sum + clockedWorkMinutes(row), 0);
        const warnings = monthRows.filter((row) => row.yellowFlag).length;
        const halfDay = warnings >= 3 ? Math.round(Number(employee.baseSalary || 0) / 26 / 2) : 0;
        const workday = new Date().getDay() !== 0;
        const notices = [
          workday && !todayRecord?.checkInTime ? "Check-in is missing today." : "",
          todayRecord?.checkInTime && !todayRecord.checkOutTime && !isCheckoutMissing(todayRecord)
            ? "You are still checked in. Signing out of the website does not check you out. If you do not check out, logout is automatic at 11:00 PM."
            : "",
          isCheckoutMissing(todayRecord)
            ? "Check-out was missed. Automatic logout at 11:00 PM keeps the attendance record."
            : "",
          todayRecord?.yellowFlag
            ? "Desktop was inactive for more than 15 minutes. This is a warning."
            : "",
          warnings >= 3
            ? `3 inactivity warnings this month. A half day, ₹${halfDay.toLocaleString("en-IN")}, is cut from salary.`
            : "",
        ].filter(Boolean);
        const monthStamp = today.slice(0, 7);
        const savedLeaves = myLeaves.filter((leave) => leave && leave.startDate);
        const casualUsed = savedLeaves.filter((leave) =>
          (leave.status === "APPROVED" || leave.status === "PENDING")
          && leave.leaveType === "CASUAL"
          && leave.startDate.startsWith(monthStamp),
        ).length;
        const sickTaken = savedLeaves.filter((leave) => leave.status === "APPROVED" && leave.leaveType === "SICK").length;
        const compOff = savedLeaves.filter((leave) => leave.status === "APPROVED" && leave.leaveType === "COMP_OFF").length;
        const week = monthDateKeys(7).map((date) => ({
          label: new Date(`${date}T00:00:00`).toLocaleDateString("en-IN", { weekday: "short" }),
          present: records.some((row) => String(row.attendanceDate ?? "").slice(0, 10) === date && row.checkInTime),
        }));
        const checkedIn = Boolean(todayRecord?.checkInTime && !todayRecord?.checkOutTime);
        return (
          <>
            <section className="home-board">
              <article className="home-clock-card">
                <span>{new Date().toLocaleDateString("en-IN", { weekday: "short", day: "2-digit", month: "short" })} · General shift 9:30–6:30</span>
                <strong>{hoursLabel(clockedWorkMinutes(todayRecord))}</strong>
                <em>worked today</em>
                <div className="attendance-actions">
                  <button className="primary-button" type="button" disabled={punching || !isWindowsComputer()} onClick={() => void punchDay()}>
                    {checkedIn ? "Check out" : "Check in"}
                  </button>
                  <button className="secondary-button" type="button" disabled={punching || !isWindowsComputer() || !todayRecord?.checkInTime || Boolean(todayRecord?.checkOutTime)} onClick={() => void punchLunch("out")}>
                    Start lunch
                  </button>
                  <button className="secondary-button" type="button" disabled={punching || !isWindowsComputer() || !todayRecord?.checkOutTime} onClick={() => void punchLunch("in")}>
                    End lunch
                  </button>
                </div>
              </article>
              <article className="home-side">
                <span>Pending on you</span>
                {notices.length === 0 ? <em>Nothing is waiting.</em> : notices.map((notice) => <em key={notice}>{notice}</em>)}
              </article>
              <div className="home-tiles">
                <article>
                  <span>Casual leave</span>
                  <strong>{Math.max(0, 1 - casualUsed)}</strong>
                  <em>left this month</em>
                </article>
                <article>
                  <span>Sick leave</span>
                  <strong>{sickTaken}</strong>
                  <em>taken</em>
                </article>
                <article>
                  <span>Comp-off</span>
                  <strong>{compOff}</strong>
                  <em>approved</em>
                </article>
                <article className="home-actions">
                  <span>Quick actions</span>
                  <button type="button" onClick={() => navigate("/leave")}>Apply leave ({leaveCount})</button>
                  <button type="button" onClick={() => navigate("/payroll")}>Payslip{latestPay ? ` · ${latestPay}` : ""}</button>
                  <button type="button" onClick={() => navigate("/attendance")}>My attendance</button>
                  <button type="button" onClick={() => navigate("/notifications")}>Open updates</button>
                </article>
              </div>
              <article className="home-wide">
                <span>This week</span>
                <div className="home-week">
                  {week.map((day) => (
                    <i key={day.label} className={day.present ? "in" : ""} title={day.label}>{day.label.slice(0, 1)}</i>
                  ))}
                </div>
              </article>
              <article className="home-wide">
                <span>Next holiday</span>
                <strong>{nextHoliday}</strong>
              </article>
              <article className="home-wide">
                <span>Announcements</span>
                <em>No announcements yet.</em>
              </article>
              <article className="home-wide">
                <span>Celebrations</span>
                <em>No birthdays or work anniversaries this week.</em>
              </article>
            </section>
            <section className="panel">
              <div className="panel-header">
                <div>
                  <p className="eyebrow">Today</p>
                  <h3>Check in and check out</h3>
                  <p>A day is 9 hours: 8 hours of work and 1 hour of lunch. Start lunch when you leave, and end lunch when you are back.</p>
                </div>
              </div>
              {!isWindowsComputer() && (
                <p>Check in and check out work only on a Windows computer. Signing out of the website does not check you out.</p>
              )}
              {punchNote && <p>{punchNote}</p>}
              {notices.length > 0 && (
                <ul className="dash-notices">
                  {notices.map((notice) => <li key={notice}>{notice}</li>)}
                </ul>
              )}
              {detailsLoading ? (
                <p>Loading today's hours...</p>
              ) : pairs.length === 0 ? (
                <p>No check-in yet today.</p>
              ) : (
                <ul className="dash-punches">
                  {pairs.map((pair, index) => (
                    <li key={`${pair.checkIn}-${index}`}>
                      Check in {pair.checkIn} · Check out {pair.checkOut ?? "still in"}
                    </li>
                  ))}
                </ul>
              )}
              <p>
                Working hours today: {hoursLabel(clockedWorkMinutes(todayRecord))}
                {" · "}
                This month: {hoursLabel(monthMinutes)}
                {halfDay > 0 ? ` · Half-day cut ₹${halfDay.toLocaleString("en-IN")}` : ""}
              </p>
            </section>
            <section className="panel">
              <div className="panel-header">
                <div>
                  <p className="eyebrow">HISTORY</p>
                  <h3>Last 30 days</h3>
                  <p>Check-in, check-out, and the working hours used for salary.</p>
                </div>
              </div>
              <div className="employees-table-wrap month-history">
                <table className="employees-table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Check in</th>
                      <th>Check out</th>
                      <th>Working hours</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detailsLoading ? (
                      <tr>
                        <td colSpan={4}>Loading the last 30 days...</td>
                      </tr>
                    ) : monthDateKeys().slice().reverse().map((date) => {
                      const row = records.find((item) => item.attendanceDate?.slice(0, 10) === date);
                      const pairs = punchPairs(audit, date, row);
                      const checkOut = shownCheckOut(row);
                      return (
                        <tr key={date}>
                          <td>{new Date(`${date}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" })}</td>
                          <td>{pairs.length > 0 ? pairs.map((pair) => pair.checkIn).join(", ") : "—"}</td>
                          <td>{!row?.checkInTime ? "—" : !checkOut ? "Still in" : pairs.length > 0 ? pairs.map((pair) => pair.checkOut ?? "23:00").join(", ") : formatClock(checkOut)}</td>
                          <td>{row?.checkInTime ? hoursLabel(clockedWorkMinutes(row)) : "No check-in"}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </section>
          </>
        );
      })()}

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
            Reports
          </p>

          <h2>
            Reports
          </h2>

          <p>
            Headcount, attendance, and review counts for this company.
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
          Refresh
        </button>

      </div>


      {error && (
        <section className="panel">

          <strong>
            Reports are unavailable
          </strong>

          <p>
            {error}
          </p>

        </section>
      )}


      <div className="stats-grid">

        <Stat
          title="Active employees"
          value={data ? data.activeEmployees.toLocaleString() : "0"}
          change={data ? `${data.inactiveEmployees} not active` : "Waiting for records"}
          icon="A"
        />

        <Stat
          title="Needs attention"
          value={data ? data.highAttritionRisk.toLocaleString() : "0"}
          change="From saved reviews"
          icon="!"
        />

        <Stat
          title="Watch closely"
          value={data ? data.mediumAttritionRisk.toLocaleString() : "0"}
          change="From saved reviews"
          icon="M"
        />

        <Stat
          title="Engagement"
          value={data ? `${data.averageEngagementScore.toFixed(1)}%` : "0"}
          change="From saved reviews"
          icon="E"
        />

      </div>

      {(!data || (data.activeEmployees === 0 && data.highAttritionRisk === 0 && data.averageEngagementScore === 0)) && (
        <section className="panel">
          <h3>No reports yet</h3>
          <p>Counts appear after attendance and reviews are saved.</p>
        </section>
      )}
    </>
  );
}


/* ============================================================
   APP
============================================================ */

class PageGuard extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    if (this.state.failed) {
      return (
        <div className="page-heading">
          <h2>This page hit a problem</h2>
          <p>Your attendance is still saved. Reload the page to continue.</p>
          <button className="primary-button" type="button" onClick={() => window.location.reload()}>
            Reload
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

export default function App() {
  return (
    <BrowserRouter>

      <Routes>

        <Route
          path="/login"
          element={<PageGuard><Login /></PageGuard>}
        />

        <Route
          path="/site"
          element={
            <PageGuard>
              <Suspense fallback={<div className="page-loading">Loading site attendance</div>}>
                <SiteAttendance />
              </Suspense>
            </PageGuard>
          }
        />

        <Route
          path="*"
          element={
            <ProtectedRoute>
              <PageGuard>
                <AppRoutes />
              </PageGuard>
            </ProtectedRoute>
          }
        />

      </Routes>

    </BrowserRouter>
  );
}





