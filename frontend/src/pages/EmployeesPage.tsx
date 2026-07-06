import { useEffect, useState } from "react";
import { getEmployees, createEmployee, type Employee, type EmployeeInput } from "../api/client";
import { PageShell } from "../components/PageShell";

const blank: EmployeeInput = {
  employeeCode: "", fullName: "", email: "", roleType: "EMPLOYEE",
  department: "", designation: "", joiningDate: "", baseSalary: 0, active: true,
};

export const EmployeesPage = () => {
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [form, setForm] = useState<EmployeeInput>(blank);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const load = () => getEmployees().then(setEmployees).catch((e) => setError(e.message));
  useEffect(() => { load(); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await createEmployee(form);
      setForm(blank);
      setShowForm(false);
      await load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Save failed");
    } finally {
      setSaving(false);
    }
  };

  const set = (field: keyof EmployeeInput) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [field]: field === "baseSalary" ? Number(e.target.value) : e.target.value }));

  return (
    <PageShell title="Employees">
      {error && <p className="error-msg">{error}</p>}
      <button className="btn-primary mb" onClick={() => setShowForm((v) => !v)}>
        {showForm ? "Cancel" : "+ Add Employee"}
      </button>

      {showForm && (
        <form onSubmit={handleSubmit} className="form form-grid panel mb">
          <label>Code<input required value={form.employeeCode} onChange={set("employeeCode")} /></label>
          <label>Full Name<input required value={form.fullName} onChange={set("fullName")} /></label>
          <label>Email<input required type="email" value={form.email} onChange={set("email")} /></label>
          <label>Role
            <select value={form.roleType} onChange={set("roleType")}>
              {["ADMIN","HR","MANAGER","EMPLOYEE"].map((r) => <option key={r}>{r}</option>)}
            </select>
          </label>
          <label>Department<input required value={form.department} onChange={set("department")} /></label>
          <label>Designation<input required value={form.designation} onChange={set("designation")} /></label>
          <label>Joining Date<input required type="date" value={form.joiningDate} onChange={set("joiningDate")} /></label>
          <label>Base Salary<input required type="number" min="0" value={form.baseSalary} onChange={set("baseSalary")} /></label>
          <button type="submit" className="btn-primary span-2" disabled={saving}>{saving ? "Saving…" : "Create"}</button>
        </form>
      )}

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr><th>Code</th><th>Name</th><th>Email</th><th>Role</th><th>Department</th><th>Designation</th><th>Salary</th><th>Status</th></tr>
          </thead>
          <tbody>
            {employees.map((emp) => (
              <tr key={emp.id}>
                <td>{emp.employeeCode}</td>
                <td>{emp.fullName}</td>
                <td>{emp.email}</td>
                <td><span className="badge">{emp.roleType}</span></td>
                <td>{emp.department}</td>
                <td>{emp.designation}</td>
                <td>₹{emp.baseSalary.toLocaleString()}</td>
                <td><span className={emp.active ? "badge badge-green" : "badge badge-red"}>{emp.active ? "Active" : "Inactive"}</span></td>
              </tr>
            ))}
            {employees.length === 0 && <tr><td colSpan={8} className="empty">No employees found.</td></tr>}
          </tbody>
        </table>
      </div>
    </PageShell>
  );
};
