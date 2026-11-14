import { useEffect, useState } from "react";
import {
  getAdminOverview,
  getCurrentEmployee,
  type AdminEmployeeRow,
  type EmployeeResponse,
} from "../api";
import { asWorkspaceCompany, getWorkspaceCompany } from "../utils/workspace";
import "./Dashboard.css";

function formatClock(value?: string | null) {
  if (!value) {
    return "—";
  }
  const match = value.match(/(\d{2}:\d{2})/);
  return match ? match[1] : value;
}

function isChairman(row: AdminEmployeeRow) {
  const text = `${row.designation ?? ""} ${row.department ?? ""}`.toLowerCase();
  return text.includes("chairman") || text.includes("founder");
}

function punchLine(row: AdminEmployeeRow) {
  if (!row.checkInTime) {
    return "No check-in";
  }
  if (!row.checkOutTime) {
    return `In ${formatClock(row.checkInTime)} · Still in`;
  }
  return `In ${formatClock(row.checkInTime)} · Out ${formatClock(row.checkOutTime)}`;
}

export default function AdminDashboard() {
  const company = getWorkspaceCompany();
  const [rows, setRows] = useState<AdminEmployeeRow[]>([]);
  const [me, setMe] = useState<EmployeeResponse | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 30000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    void Promise.all([
      getAdminOverview(),
      getCurrentEmployee().catch(() => null),
    ])
      .then(([overview, person]) => {
        if (cancelled) {
          return;
        }
        setRows(overview.rows ?? []);
        setMe(person);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load the admin dashboard.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [company]);

  const people = rows.filter((row) => !company || asWorkspaceCompany(row.company) === company);
  const active = people.filter((row) => row.active);
  const chairmen = active.filter(isChairman);
  const managers = active.filter((row) => row.role === "MANAGER" && !isChairman(row));
  const staff = active.filter((row) => row.role === "EMPLOYEE");
  const checkedIn = active.filter((row) => row.checkInTime);
  const missingCheckIn = active.filter((row) => !row.checkInTime);
  const missingCheckOut = active.filter((row) => row.checkInTime && !row.checkOutTime);
  const companyName = company === "GORLE" ? "Gorle Group" : "CYOND";
  const displayName = me?.fullName?.trim() || "Admin";

  function reportsOf(person: AdminEmployeeRow, chairman: boolean) {
    const direct = people.filter((row) => row.reportingManagerId === person.id);
    if (chairman && direct.length === 0) {
      return managers.filter((row) => row.company === person.company);
    }
    return direct;
  }

  return (
    <div className="hr-home admin-dashboard">
      <header className="hr-home-head">
        <div>
          <p>
            Admin · {companyName} · as of {now.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })}
          </p>
          <h2>Good day, {displayName}</h2>
        </div>
        <time dateTime={now.toISOString()}>
          {now.toLocaleDateString("en-IN", { weekday: "short", day: "2-digit", month: "short", year: "numeric" })}
        </time>
      </header>

      {error && <p className="page-alert">{error}</p>}

      <section className="hr-kpis">
        <button type="button">
          <span>Employees</span>
          <strong>{staff.length}</strong>
          <em>{companyName}</em>
        </button>
        <button type="button">
          <span>Managers</span>
          <strong>{managers.length}</strong>
          <em>team leaders</em>
        </button>
        <button type="button">
          <span>Chairman</span>
          <strong>{chairmen.length}</strong>
          <em>founder</em>
        </button>
        <button type="button">
          <span>Checked in</span>
          <strong>{checkedIn.length}</strong>
          <em>today</em>
        </button>
        <button type="button">
          <span>Missing check-in</span>
          <strong>{missingCheckIn.length}</strong>
          <em>today</em>
        </button>
        <button type="button">
          <span>Missing check-out</span>
          <strong>{missingCheckOut.length}</strong>
          <em>still in</em>
        </button>
      </section>

      <section className="hr-split">
        <article className="hr-card">
          <div className="hr-card-title">
            <h3>Chairman</h3>
          </div>
          {loading ? (
            <p className="hr-empty">Loading the admin dashboard...</p>
          ) : chairmen.length === 0 ? (
            <p className="hr-empty">No chairman in this company. A chairman is the founder, or a person whose designation says Chairman.</p>
          ) : chairmen.map((person) => (
            <Leader key={person.id} person={person} reports={reportsOf(person, true)} />
          ))}
        </article>
        <article className="hr-card">
          <div className="hr-card-title">
            <h3>Managers</h3>
          </div>
          {loading ? (
            <p className="hr-empty">Loading managers...</p>
          ) : managers.length === 0 ? (
            <p className="hr-empty">No manager in this company.</p>
          ) : managers.map((person) => (
            <Leader key={person.id} person={person} reports={reportsOf(person, false)} />
          ))}
        </article>
      </section>
    </div>
  );
}

function Leader({ person, reports }: { person: AdminEmployeeRow; reports: AdminEmployeeRow[] }) {
  return (
    <div className="admin-leader">
      <h3>{person.fullName}</h3>
      <p className="admin-leader-meta">
        {person.designation || person.role}
        {person.department ? ` · ${person.department}` : ""}
        {person.employeeCode ? ` · ${person.employeeCode}` : ""}
      </p>
      <p className="admin-leader-meta">{punchLine(person)}</p>
      <ul className="admin-leader-people">
        {reports.map((report) => (
          <li key={report.id}>
            <span>
              <strong>{report.fullName}</strong>
              {report.designation ? ` · ${report.designation}` : ""}
            </span>
            <span>{punchLine(report)}</span>
          </li>
        ))}
        {reports.length === 0 && <li>No one reports to {person.fullName}.</li>}
      </ul>
    </div>
  );
}
