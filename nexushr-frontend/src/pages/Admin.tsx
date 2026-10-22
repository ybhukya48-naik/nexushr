import { useEffect, useState } from "react";
import {
  getAdminOverview,
  setEmployeeActive,
  setEmployeeRole,
  setReportingManager,
  setSiteVisitor,
  getHolidays,
  addHoliday,
  deleteHoliday,
  type AdminEmployeeRow,
  type AdminOverview,
  type CompanyHoliday,
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
  const [holidays, setHolidays] = useState<CompanyHoliday[]>([]);
  const [holidayDate, setHolidayDate] = useState("");
  const [holidayName, setHolidayName] = useState("");
  const company = getWorkspaceCompany();

  async function load() {
    setLoading(true);
    setError("");
    try {
      setOverview(await getAdminOverview());
      setHolidays(await getHolidays());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load the admin panel.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [company]);

  async function markSiteVisitor(row: AdminEmployeeRow) {
    setSavingId(row.id);
    setError("");
    try {
      await setSiteVisitor(row.id, !row.siteVisitor);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to update site attendance.");
    } finally {
      setSavingId(null);
    }
  }

  async function changeRole(row: AdminEmployeeRow, role: string) {
    if (!role || role === row.role) {
      return;
    }
    setSavingId(row.id);
    setError("");
    try {
      await setEmployeeRole(row.id, role);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to change the role.");
    } finally {
      setSavingId(null);
    }
  }

  async function changeManager(row: AdminEmployeeRow, managerId: string) {
    setSavingId(row.id);
    setError("");
    try {
      await setReportingManager(row.id, managerId ? Number(managerId) : null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to set the reporting manager.");
    } finally {
      setSavingId(null);
    }
  }

  async function saveHoliday() {
    setError("");
    try {
      await addHoliday(holidayDate, holidayName, company ?? undefined);
      setHolidayDate("");
      setHolidayName("");
      setHolidays(await getHolidays());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to save the holiday.");
    }
  }

  async function removeHoliday(id: number) {
    setError("");
    try {
      await deleteHoliday(id);
      setHolidays(await getHolidays());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to remove the holiday.");
    }
  }

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
          <p>Super admin and admin access. Accounts, site visitors, roles, and today's flags.</p>
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
                <th>Reporting manager</th>
                <th>Company</th>
                <th>Today</th>
                <th>Check in</th>
                <th>Check out</th>
                <th>Account</th>
                <th>Site visitor</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>
                    <strong>{row.fullName}</strong>
                    <span className="admin-sub">{row.employeeCode}</span>
                  </td>
                  <td>
                    <select
                      value={row.role}
                      disabled={savingId === row.id}
                      onChange={(event) => void changeRole(row, event.target.value)}
                    >
                      <option value="EMPLOYEE">Employee</option>
                      <option value="MANAGER">Manager</option>
                      <option value="HR">HR</option>
                      <option value="ADMIN">Admin</option>
                      <option value="SUPER_ADMIN">Super admin</option>
                    </select>
                  </td>
                  <td>
                    <select
                      value={row.reportingManagerId ?? ""}
                      disabled={savingId === row.id}
                      onChange={(event) => void changeManager(row, event.target.value)}
                    >
                      <option value="">No manager</option>
                      {rows
                        .filter((manager) => manager.id !== row.id)
                        .map((manager) => (
                          <option key={manager.id} value={manager.id}>
                            {manager.fullName}
                          </option>
                        ))}
                    </select>
                  </td>
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
                  <td>
                    <button
                      type="button"
                      className="secondary-button"
                      disabled={savingId === row.id}
                      onClick={() => void markSiteVisitor(row)}
                    >
                      {row.siteVisitor ? "Site visitor" : "Office"}
                    </button>
                  </td>
                </tr>
              ))}
              {!loading && rows.length === 0 && (
                <tr>
                  <td colSpan={9}>No employees in this workspace.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">HOLIDAYS</p>
            <h3>Company holidays</h3>
            <p>
              Sundays, Republic Day, Independence Day, Gandhi Jayanti, and Christmas are already off.
              Add any other holiday here. It is not a working day and it is not deducted.
            </p>
          </div>
        </div>
        <div className="employee-filters">
          <label>
            Date
            <input type="date" value={holidayDate} onChange={(event) => setHolidayDate(event.target.value)} />
          </label>
          <label>
            Name
            <input value={holidayName} onChange={(event) => setHolidayName(event.target.value)} />
          </label>
          <button
            type="button"
            className="primary-button"
            disabled={!holidayDate || !holidayName.trim()}
            onClick={() => void saveHoliday()}
          >
            Add holiday
          </button>
        </div>
        <ul>
          {holidays.map((holiday) => (
            <li key={holiday.id}>
              {holiday.holidayDate} — {holiday.name}
              {holiday.companyCode ? ` (${holiday.companyCode})` : " (both companies)"}
              <button type="button" className="secondary-button" onClick={() => void removeHoliday(holiday.id)}>
                Remove
              </button>
            </li>
          ))}
          {holidays.length === 0 && <li>No extra holidays saved.</li>}
        </ul>
      </section>
    </div>
  );
}
