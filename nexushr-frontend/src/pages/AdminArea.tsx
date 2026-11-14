import { useEffect, useState } from "react";
import { getAdminOverview, type AdminEmployeeRow } from "../api";
import { getAttendanceAudit, type AttendanceAuditEntry } from "./Attendance";
import { asWorkspaceCompany, getWorkspaceCompany } from "../utils/workspace";

function AdminCompanies() {
  const [rows, setRows] = useState<AdminEmployeeRow[]>([]);
  const [error, setError] = useState("");
  const company = getWorkspaceCompany();

  useEffect(() => {
    void getAdminOverview()
      .then((overview) => setRows(overview.rows ?? []))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "Unable to load companies."));
  }, [company]);

  const groups = new Map<string, AdminEmployeeRow[]>();
  for (const row of rows) {
    const code = asWorkspaceCompany(row.company) || row.company || "COMPANY";
    const list = groups.get(code) ?? [];
    list.push(row);
    groups.set(code, list);
  }

  return (
    <div className="admin-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Companies</p>
          <h2>Company management</h2>
          <p>A super admin can see every company. Anyone else sees only their own company.</p>
        </div>
      </div>
      {error && <p className="page-alert">{error}</p>}
      {[...groups.entries()].map(([code, people]) => (
        <section key={code} className="panel">
          <div className="panel-header">
            <div>
              <p className="eyebrow">{code === "GORLE" ? "Gorle Group" : code}</p>
              <h3>
                {people.filter((person) => person.active).length} people
                {" · "}
                {people.filter((person) => person.role === "HR" && person.active).length} HR
                {" · "}
                {people.filter((person) => person.role === "MANAGER" && person.active).length} managers
              </h3>
            </div>
          </div>
          <div className="employees-table-wrap">
            <table className="employees-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Role</th>
                  <th>Designation</th>
                  <th>Today</th>
                </tr>
              </thead>
              <tbody>
                {people.map((person) => (
                  <tr key={person.id}>
                    <td>
                      <strong>{person.fullName}</strong>
                      <span className="admin-sub">{person.employeeCode}</span>
                    </td>
                    <td>{person.role}</td>
                    <td>{person.designation || "—"}</td>
                    <td>{person.checkInTime ? "Checked in" : "No check-in"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ))}
      {groups.size === 0 && !error && <p>No company records yet.</p>}
    </div>
  );
}

function AdminRoles() {
  const rules = [
    ["Super Admin", "Every company. Employees, attendance, payroll, roles, and settings."],
    ["Company Admin", "One company only. That company's accounts, attendance, and payroll."],
    ["HR", "Employees, attendance, and payroll for their own company."],
    ["Manager", "Their team and approvals, inside their own company."],
    ["Employee", "Their own profile and attendance."],
  ];
  return (
    <div className="admin-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Access</p>
          <h2>Roles and permissions</h2>
          <p>These roles are enforced by the server. Hiding a page is not the lock.</p>
        </div>
      </div>
      <section className="panel">
        <div className="employees-table-wrap">
          <table className="employees-table">
            <thead>
              <tr>
                <th>Role</th>
                <th>What they can open</th>
              </tr>
            </thead>
            <tbody>
              {rules.map(([role, access]) => (
                <tr key={role}>
                  <td><strong>{role}</strong></td>
                  <td>{access}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function AdminAudit() {
  const [rows, setRows] = useState<AttendanceAuditEntry[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const company = getWorkspaceCompany();

  useEffect(() => {
    setLoading(true);
    void getAttendanceAudit()
      .then((entries) => setRows(Array.isArray(entries) ? entries : []))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "Unable to load the audit log."))
      .finally(() => setLoading(false));
  }, [company]);

  return (
    <div className="admin-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Audit</p>
          <h2>Audit logs</h2>
          <p>Check-in, check-out, and attendance changes. This list is for the company you can open.</p>
        </div>
      </div>
      {error && <p className="page-alert">{error}</p>}
      <section className="panel">
        <div className="employees-table-wrap">
          <table className="employees-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Who</th>
                <th>Action</th>
                <th>Detail</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => (
                <tr key={`${row.createdAt}-${index}`}>
                  <td>{row.createdAt?.replace("T", " ").slice(0, 16) || "—"}</td>
                  <td>{row.actorName || "—"}</td>
                  <td>{row.action}</td>
                  <td>{row.detail}</td>
                </tr>
              ))}
              {loading && (
                <tr>
                  <td colSpan={4}>Loading the audit log...</td>
                </tr>
              )}
              {!loading && rows.length === 0 && !error && (
                <tr>
                  <td colSpan={4}>No attendance changes recorded yet.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

export default function AdminArea({ section }: { section: "companies" | "roles" | "audit" }) {
  if (section === "roles") {
    return <AdminRoles />;
  }
  if (section === "audit") {
    return <AdminAudit />;
  }
  return <AdminCompanies />;
}
