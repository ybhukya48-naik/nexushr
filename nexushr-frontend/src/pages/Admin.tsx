import { useEffect, useState } from "react";
import {
  getAdminOverview,
  setEmployeeActive,
  type AdminEmployeeRow,
  type AdminOverview,
} from "../api";
import { asWorkspaceCompany, getWorkspaceCompany } from "../utils/workspace";

function formatClock(value?: string | null) {
  if (!value) {
    return "-";
  }
  const match = value.match(/(\d{2}:\d{2})/);
  return match ? match[1] : value;
}

export default function Admin() {
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const company = getWorkspaceCompany();

  async function load() {
    setLoading(true);
    setError("");
    try {
      setOverview(await getAdminOverview());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load the admin panel.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [company]);

  async function toggle(row: AdminEmployeeRow) {
    setSavingId(row.id);
    setError("");
    try {
      await setEmployeeActive(row.id, !row.active);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to update the account.");
    } finally {
      setSavingId(null);
    }
  }

  const rows = (overview?.rows ?? []).filter((row) => {
    if (!company) {
      return true;
    }
    return asWorkspaceCompany(row.company) === company;
  });

  return (
    <div className="admin-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">ADMIN</p>
          <h2>Admin panel</h2>
          <p>Accounts, roles, and today's check-in and check-out alerts.</p>
        </div>
      </div>

      {error && <p className="page-alert">{error}</p>}

      <div className="stats-grid admin-stats">
        <article className="stat-card">
          <p>Employees</p>
          <h3>{overview?.employees ?? 0}</h3>
          <span>{overview?.active ?? 0} active</span>
        </article>
        <article className="stat-card">
          <p>Inactive accounts</p>
          <h3>{overview?.inactiveAccounts ?? 0}</h3>
          <span>Login turned off</span>
        </article>
        <article className="stat-card">
          <p>Missing check-in</p>
          <h3>{overview?.missingCheckIn ?? 0}</h3>
          <span>Today</span>
        </article>
        <article className="stat-card">
          <p>Missing check-out</p>
          <h3>{overview?.missingCheckOut ?? 0}</h3>
          <span>Today</span>
        </article>
      </div>

      <section className="panel">
        <div className="employees-table-wrap">
          <table className="employees-table">
            <thead>
              <tr>
                <th>Employee</th>
                <th>Role</th>
                <th>Company</th>
                <th>Today</th>
                <th>Check in</th>
                <th>Check out</th>
                <th>Account</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>
                    <strong>{row.fullName}</strong>
                    <span className="admin-sub">{row.employeeCode}</span>
                  </td>
                  <td>{row.role}</td>
                  <td>{row.company || "-"}</td>
                  <td>{row.attendanceStatus === "NONE" ? "No punch" : row.attendanceStatus}</td>
                  <td>{formatClock(row.checkInTime)}</td>
                  <td>{formatClock(row.checkOutTime)}</td>
                  <td>
                    <button
                      type="button"
                      className={row.active ? "secondary-button" : "primary-button"}
                      disabled={savingId === row.id}
                      onClick={() => void toggle(row)}
                    >
                      {savingId === row.id
                        ? "Saving..."
                        : row.active
                          ? "Deactivate"
                          : "Activate"}
                    </button>
                  </td>
                </tr>
              ))}
              {!loading && rows.length === 0 && (
                <tr>
                  <td colSpan={7}>No employees in this workspace.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
