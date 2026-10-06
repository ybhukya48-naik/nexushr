import { useEffect, useState } from "react";
import "./EmployeeProfile.css";
import { useNavigate, useParams } from "react-router-dom";
import {
  getEmployee,
  updateEmployee,
  type EmployeeRequest,
  type EmployeeResponse,
  type EmployeeLifecycleStatus,
  type EmployeeRole,
} from "../api";

function statusLabel(status?: EmployeeLifecycleStatus) {
  switch (status) {
    case "ACTIVE":
      return "Active";
    case "PENDING_ONBOARDING":
      return "Pending Onboarding";
    case "PENDING_APPROVAL":
      return "Pending Approval";
    case "OFFBOARDING":
      return "Offboarding";
    case "OFFBOARDED":
      return "Offboarded";
    default:
      return status || "Unknown";
  }
}

function statusClass(status?: EmployeeLifecycleStatus) {
  switch (status) {
    case "ACTIVE":
      return "employee-status employee-status-active";
    case "OFFBOARDING":
      return "employee-status employee-status-offboarding";
    case "PENDING_ONBOARDING":
      return "employee-status employee-status-probation";
    case "PENDING_APPROVAL":
      return "employee-status employee-status-notice";
    default:
      return "employee-status";
  }
}

function roleLabel(role: EmployeeRole) {
  switch (role) {
    case "ADMIN":
      return "Admin";
    case "HR":
      return "HR";
    case "MANAGER":
      return "Manager";
    case "EMPLOYEE":
      return "Employee";
    default:
      return role;
  }
}

export default function EmployeeProfile() {
  const navigate = useNavigate();
  const { id } = useParams();

  const employeeId = Number(id);

  const [employee, setEmployee] = useState<EmployeeResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");

  const [form, setForm] = useState<EmployeeRequest | null>(null);

  useEffect(() => {
    if (!Number.isInteger(employeeId) || employeeId <= 0) {
      return;
    }

    let cancelled = false;

    getEmployee(employeeId)
      .then((data) => {
        if (cancelled) {
          return;
        }

        setEmployee(data);

        setForm({
          employeeCode: data.employeeCode,
          fullName: data.fullName,
          email: data.email,
          password: "",
          roleType: data.roleType,
          department: data.department,
          designation: data.designation,
          joiningDate: data.joiningDate,
          baseSalary: data.baseSalary,
          active: data.active,
        });
      })
      .catch((err) => {
        if (!cancelled) {
          setError(
            err instanceof Error
              ? err.message
              : "Unable to load employee profile.",
          );
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
  }, [employeeId]);

  function updateForm(
    field: keyof EmployeeRequest,
    value: string | number | boolean,
  ) {
    setForm((current) =>
      current
        ? {
            ...current,
            [field]: value,
          }
        : current,
    );
  }

  function startEditing() {
    if (!employee) {
      return;
    }

    setSaveError("");

    setForm({
      employeeCode: employee.employeeCode,
      fullName: employee.fullName,
      email: employee.email,
      password: "",
      roleType: employee.roleType,
      department: employee.department,
      designation: employee.designation,
      joiningDate: employee.joiningDate,
      baseSalary: employee.baseSalary,
      active: employee.active,
    });

    setEditing(true);
  }

  async function handleSave(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!form) {
      return;
    }

    setSaving(true);
    setSaveError("");

    try {
      const updated = await updateEmployee(employeeId, form);

      setEmployee(updated);

      setForm({
        employeeCode: updated.employeeCode,
        fullName: updated.fullName,
        email: updated.email,
        password: "",
        roleType: updated.roleType,
        department: updated.department,
        designation: updated.designation,
        joiningDate: updated.joiningDate,
        baseSalary: updated.baseSalary,
        active: updated.active,
      });

      setEditing(false);
    } catch (err) {
      setSaveError(
        err instanceof Error
          ? err.message
          : "Unable to update employee.",
      );
    } finally {
      setSaving(false);
    }
  }

  if (!Number.isInteger(employeeId) || employeeId <= 0) {
    return (
      <section className="panel empty-module">
        <div className="module-icon">!</div>
        <h3>Invalid employee</h3>
        <p>The employee ID in the URL is not valid.</p>
        <button type="button" className="primary-button" onClick={() => navigate("/employees")}>
          Back to Employees
        </button>
      </section>
    );
  }

  if (loading) {
    return (
      <section className="employees-page">
        <div className="employee-empty-state">
          <strong>Loading employee profile...</strong>
          <span>Preparing employee information.</span>
        </div>
      </section>
    );
  }

  if (error || !employee) {
    return (
      <section className="employees-page">
        <div className="employee-empty-state" role="alert">
          <strong>Unable to load employee</strong>
          <span>{error || "Employee record was not found."}</span>
          <button
            type="button"
            className="table-action"
            onClick={() => navigate("/employees")}
          >
            Back to Employees
          </button>
        </div>
      </section>
    );
  }

  return (
    <section className="employees-page">
      <div className="employees-header">
        <div>
          <p className="page-eyebrow">Employee Lifecycle</p>
          <h1>{employee.fullName}</h1>
          <p>
            Employee profile and employment information.
          </p>
        </div>

        <div>
          <button
            type="button"
            className="table-action"
            onClick={() => navigate("/employees")}
          >
            Back
          </button>{" "}

          {!editing && (
            <button
              type="button"
              className="primary-action"
              onClick={startEditing}
            >
              Edit Employee
            </button>
          )}
        </div>
      </div>

      <div className="employee-summary-grid">
        <div className="employee-summary-card">
          <span>Employee Code</span>
          <strong>{employee.employeeCode}</strong>
        </div>

        <div className="employee-summary-card">
          <span>Role</span>
          <strong>{roleLabel(employee.roleType)}</strong>
        </div>

        <div className="employee-summary-card">
          <span>Department</span>
          <strong>{employee.department}</strong>
        </div>

        <div className="employee-summary-card">
          <span>Status</span>
          <strong>
            <span className={statusClass(employee.lifecycleStatus)}>
              {statusLabel(employee.lifecycleStatus)}
            </span>
          </strong>
        </div>
      </div>

      {editing && form ? (
        <form
          className="employees-panel"
          onSubmit={handleSave}
        >
          <div className="employees-header">
            <div>
              <p className="page-eyebrow">
                Edit Employee
              </p>
              <h2>Edit Employee</h2>
              <p>
                Update the employee's employment information.
              </p>
            </div>

            <button
              type="button"
              className="table-action"
              onClick={() => {
                setSaveError("");
                setEditing(false);
              }}
              disabled={saving}
            >
              Cancel
            </button>
          </div>

          {saveError && (
            <div role="alert">
              {saveError}
            </div>
          )}

          <div className="employee-filters">
            <label>
              Employee Code
              <input
                required
                value={form.employeeCode}
                onChange={(event) =>
                  updateForm(
                    "employeeCode",
                    event.target.value,
                  )
                }
              />
            </label>

            <label>
              Full Name
              <input
                required
                value={form.fullName}
                onChange={(event) =>
                  updateForm(
                    "fullName",
                    event.target.value,
                  )
                }
              />
            </label>

            <label>
              Email
              <input
                required
                type="email"
                value={form.email}
                onChange={(event) =>
                  updateForm(
                    "email",
                    event.target.value,
                  )
                }
              />
            </label>

            <label>
              New Password
              <input
                type="password"
                placeholder="Enter password if changing it"
                value={form.password}
                onChange={(event) =>
                  updateForm(
                    "password",
                    event.target.value,
                  )
                }
              />
            </label>

            <label>
              Role
              <select
                value={form.roleType}
                onChange={(event) =>
                  updateForm(
                    "roleType",
                    event.target.value as EmployeeRole,
                  )
                }
              >
                <option value="EMPLOYEE">Employee</option>
                <option value="MANAGER">Manager</option>
                <option value="HR">HR</option>
                <option value="ADMIN">Admin</option>
              </select>
            </label>

            <label>
              Department
              <input
                required
                value={form.department}
                onChange={(event) =>
                  updateForm(
                    "department",
                    event.target.value,
                  )
                }
              />
            </label>

            <label>
              Designation
              <input
                required
                value={form.designation}
                onChange={(event) =>
                  updateForm(
                    "designation",
                    event.target.value,
                  )
                }
              />
            </label>

            <label>
              Joining Date
              <input
                required
                type="date"
                value={form.joiningDate}
                onChange={(event) =>
                  updateForm(
                    "joiningDate",
                    event.target.value,
                  )
                }
              />
            </label>

            <label>
              Base Salary
              <input
                required
                min="0"
                type="number"
                value={form.baseSalary}
                onChange={(event) =>
                  updateForm(
                    "baseSalary",
                    Number(event.target.value),
                  )
                }
              />
            </label>

            <label>
              Active
              <input
                type="checkbox"
                checked={form.active}
                onChange={(event) =>
                  updateForm(
                    "active",
                    event.target.checked,
                  )
                }
              />
            </label>
          </div>

          <button
            type="submit"
            className="primary-action"
            disabled={saving}
          >
            {saving ? "Saving..." : "Save Employee"}
          </button>
        </form>
      ) : (
        <div className="employees-panel">
          <div className="panel-header">
            <div>
              <p className="page-eyebrow">
                Personal & Employment Details
              </p>
              <h2>Employee Information</h2>
              <p>
                Core information recorded for this employee.
              </p>
            </div>
          </div>

          <div className="employee-filters">
            <label>
              Full Name
              <input value={employee.fullName} readOnly />
            </label>

            <label>
              Employee Code
              <input value={employee.employeeCode} readOnly />
            </label>

            <label>
              Email
              <input value={employee.email} readOnly />
            </label>

            <label>
              Mobile Number
              <input
                value={employee.phone || "Not provided"}
                readOnly
              />
            </label>

            <label>
              Role
              <input
                value={roleLabel(employee.roleType)}
                readOnly
              />
            </label>

            <label>
              Department
              <input value={employee.department} readOnly />
            </label>

            <label>
              Designation
              <input value={employee.designation} readOnly />
            </label>

            <label>
              Joining Date
              <input value={employee.joiningDate} readOnly />
            </label>

            <label>
              Base Salary
              <input
                value={employee.baseSalary.toLocaleString("en-IN")}
                readOnly
              />
            </label>

            <label>
              Account Status
              <input
                value={employee.active ? "Active" : "Inactive"}
                readOnly
              />
            </label>

            <label>
              Lifecycle Status
              <input
                value={statusLabel(employee.lifecycleStatus)}
                readOnly
              />
            </label>
          </div>
        </div>
      )}

      <div className="employees-panel">
        <div className="panel-header">
          <div>
            <p className="page-eyebrow">
              Lifecycle
            </p>
            <h2>Lifecycle Actions</h2>
            <p>
              Onboarding, approval, transfer, promotion,
              resignation and offboarding will be added in
              the next F01 steps.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
