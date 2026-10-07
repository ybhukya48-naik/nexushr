import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { FormEvent } from "react";
import {
  createEmployee,
  getEmployees,
  updateEmployee,
  deleteEmployee,
  type EmployeeRequest,
  type EmployeeResponse,
  type EmployeeLifecycleStatus,
  type EmployeeRole,
} from "../api";

function statusLabel(status: EmployeeLifecycleStatus) {
  switch (status) {
    case "ACTIVE":
      return "Active";
    case "PENDING_ONBOARDING":
      return "Onboarding";
    case "PENDING_APPROVAL":
      return "Pending Approval";
    case "OFFBOARDING":
      return "Offboarding";
    case "OFFBOARDED":
      return "Offboarded";
    default:
      return status;
  }
}

function statusClass(status: EmployeeLifecycleStatus) {
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

function initials(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");
}

const emptyForm: EmployeeRequest = {
  employeeCode: "",
  fullName: "",
  email: "",
  phone: "",
  accountNumber: "",
  password: "",
  roleType: "EMPLOYEE",
  department: "",
  designation: "",
  joiningDate: "",
  baseSalary: 0,
  active: true,
  companyCode: "CYOND",
};

function getStoredRole() {
  return localStorage.getItem("nexushr_role") || "EMPLOYEE";
}

function canManageEmployees(role: string) {
  return role === "ADMIN" || role === "HR" || role === "MANAGER";
}

export default function Employees() {
  const navigate = useNavigate();

  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [search, setSearch] = useState("");
  const [department, setDepartment] = useState("All Departments");
  const [status, setStatus] = useState("All Statuses");
  const [company, setCompany] = useState("All Companies");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [form, setForm] = useState<EmployeeRequest>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [role] = useState(getStoredRole);

  const managementAccess = canManageEmployees(role);
  const canEditDeleteEmployees = role === "HR";

  const [editingEmployeeId, setEditingEmployeeId] =
    useState<number | null>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] =
    useState(false);
  const [employeeToDelete, setEmployeeToDelete] =
    useState<EmployeeResponse | null>(null);

  async function loadEmployees() {
    if (!managementAccess) {
      setEmployees([]);
      setLoading(false);
      setError("");
      return;
    }

    setLoading(true);
    setError("");

    try {
      const data = await getEmployees();
      setEmployees(data);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to load employees.",
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let cancelled = false;

    if (!managementAccess) {
      setEmployees([]);
      setLoading(false);
      setError("");
      return;
    }

    setLoading(true);
    setError("");

    getEmployees()
      .then((data) => {
        if (!cancelled) {
          setEmployees(data);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(
            err instanceof Error
              ? err.message
              : "Unable to load employees.",
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
  }, [managementAccess]);

  const departments = useMemo(
    () =>
      Array.from(
        new Set(
          employees
            .map((employee) => employee.department)
            .filter(Boolean),
        ),
      ).sort(),
    [employees],
  );

  const filteredEmployees = useMemo(() => {
    const query = search.trim().toLowerCase();

    return employees.filter((employee) => {
      const matchesSearch =
        !query ||
        employee.fullName.toLowerCase().includes(query) ||
        employee.employeeCode.toLowerCase().includes(query) ||
        employee.email.toLowerCase().includes(query) ||
        employee.designation.toLowerCase().includes(query);

      const matchesDepartment =
        department === "All Departments" ||
        employee.department === department;

      const matchesStatus =
        status === "All Statuses" ||
        statusLabel(employee.lifecycleStatus) === status;

      const matchesCompany =
        company === "All Companies" || employee.companyCode === (company === "CYOND" ? "CYOND" : "GORLE");

      return (        matchesSearch &&
        matchesDepartment &&
        matchesStatus &&
        matchesCompany
      );
    });
  }, [employees, search, department, status, company]);

  const activeCount = employees.filter(
    (employee) => employee.lifecycleStatus === "ACTIVE",
  ).length;

  const onboardingCount = employees.filter(
    (employee) =>
      employee.lifecycleStatus === "PENDING_ONBOARDING" ||
      employee.lifecycleStatus === "PENDING_APPROVAL",
  ).length;

  const offboardingCount = employees.filter(
    (employee) => employee.lifecycleStatus === "OFFBOARDING",
  ).length;

  const offboardedCount = employees.filter(
    (employee) => employee.lifecycleStatus === "OFFBOARDED",
  ).length;

  function updateForm(
    field: keyof EmployeeRequest,
    value: string | number | boolean,
  ) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }));
  }

  function handleEditEmployee(employee: EmployeeResponse) {
    if (!canEditDeleteEmployees) {
      return;
    }

    setSaveError("");
    setEditingEmployeeId(employee.id);

    setForm({
      employeeCode: employee.employeeCode,
      fullName: employee.fullName,
      email: employee.email,
      phone: employee.phone ?? "",
      accountNumber: employee.accountNumber ?? "",
      password: "",
      roleType: employee.roleType,
      department: employee.department,
      designation: employee.designation,
      joiningDate: employee.joiningDate,
      baseSalary: employee.baseSalary,
      active: employee.active,
    });

    setShowCreateForm(true);
  }

  function handleDeleteEmployee(employee: EmployeeResponse) {
    if (!canEditDeleteEmployees) {
      return;
    }

    setEmployeeToDelete(employee);
    setShowDeleteConfirm(true);
  }

  async function confirmDeleteEmployee() {
    if (!canEditDeleteEmployees || !employeeToDelete) {
      return;
    }

    setSaving(true);
    setSaveError("");

    try {
      await deleteEmployee(employeeToDelete.id);

      setShowDeleteConfirm(false);
      setEmployeeToDelete(null);

      await loadEmployees();
    } catch (err) {
      setSaveError(
        err instanceof Error
          ? err.message
          : "Unable to delete employee.",
      );
    } finally {
      setSaving(false);
    }
  }

  async function handleCreateEmployee(event: FormEvent) {
    event.preventDefault();

    if (!managementAccess || saving) {
      return;
    }

    setSaving(true);
    setSaveError("");

    const employeeData: EmployeeRequest = {
      ...form,
      employeeCode: form.employeeCode.trim(),
      fullName: form.fullName.trim(),
      email: form.email.trim().toLowerCase(),
      phone: form.phone?.trim() || "",
      accountNumber: form.accountNumber?.trim() || "",
      department: form.department.trim(),
      designation: form.designation.trim(),
    };

    try {
      if (editingEmployeeId !== null) {
        await updateEmployee(editingEmployeeId, employeeData);
      } else {
        await createEmployee(employeeData);
      }

      setForm({ ...emptyForm });
      setShowCreateForm(false);
      setEditingEmployeeId(null);

      await loadEmployees();
    } catch (err) {
      setSaveError(
        err instanceof Error
          ? err.message
          : editingEmployeeId !== null
            ? "Unable to update employee."
            : "Unable to create employee.",
      );
    } finally {
      setSaving(false);
    }
  }

  if (!managementAccess) {
    return (
      <section className="employees-page">
        <div className="employees-header">
          <div>
            <p className="page-eyebrow">Employee Lifecycle</p>
            <h1>Employees</h1>
            <p>Employee directory access is restricted to authorized HR users.</p>
          </div>
        </div>

        <div className="employees-panel">
          <div className="employee-empty-state" role="status">
            <strong>Employee management access required</strong>
            <span>
              Your account does not have permission to view or manage the
              organization-wide employee directory.
            </span>
            <button
              type="button"
              className="table-action"
              onClick={() => navigate("/")}
            >
              Return to Dashboard
            </button>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="employees-page">
      <div className="employees-header">
        <div>
          <p className="page-eyebrow">Employee Lifecycle</p>
          <h1>Employees</h1>
          <p>Manage your workforce and employee lifecycle.</p>
        </div>

        <button
          type="button"
          className="primary-action"
          onClick={() => {
            setSaveError("");
            setShowCreateForm(true);
          }}
        >
          <span aria-hidden="true">+</span>
          Add Employee
        </button>
      </div>

      <div className="employee-summary-grid">
        <div className="employee-summary-card">
          <span>Total Employees</span>
          <strong>{employees.length}</strong>
        </div>

        <div className="employee-summary-card">
          <span>Active</span>
          <strong>{activeCount}</strong>
        </div>

        <div className="employee-summary-card">
          <span>Onboarding</span>
          <strong>{onboardingCount}</strong>
        </div>

        <div className="employee-summary-card">
          <span>Offboarding</span>
          <strong>{offboardingCount}</strong>
        </div>

        <div className="employee-summary-card">
          <span>Offboarded</span>
          <strong>{offboardedCount}</strong>
        </div>
      </div>

      {showCreateForm && (
        <form
          className="employees-panel"
          onSubmit={handleCreateEmployee}
        >
          <div className="employees-header">
            <div>
              <p className="page-eyebrow">
                {editingEmployeeId !== null
                  ? "Edit Employee"
                  : "Create Employee"}
              </p>
              <h2>
                {editingEmployeeId !== null
                  ? "Edit Employee"
                  : "Add Employee"}
              </h2>
            </div>

            <button
              type="button"
              className="table-action"
              onClick={() => {
                if (!saving) {
                  setShowCreateForm(false);
                  setSaveError("");
                }
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
                  updateForm("fullName", event.target.value)
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
                  updateForm("email", event.target.value)
                }
              />
            </label>

            <label>
              Phone Number
              <input
                required
                type="tel"
                inputMode="tel"
                autoComplete="tel"
                value={form.phone}
                onChange={(event) =>
                  updateForm("phone", event.target.value)
                }
              />
            </label>

            <label>
              Bank Account Number
              <input
                required
                type="text"
                inputMode="numeric"
                autoComplete="off"
                value={form.accountNumber}
                onChange={(event) =>
                  updateForm("accountNumber", event.target.value)
                }
              />
            </label>

            <label>
              Password
              <input
                required
                type="password"
                value={form.password}
                onChange={(event) =>
                  updateForm("password", event.target.value)
                }
              />
            </label>

            <label>
              Company
              <select
                value={form.companyCode || "CYOND"}
                onChange={(event) =>
                  updateForm("companyCode", event.target.value)
                }
              >
                <option value="CYOND">CYOND</option>
                <option value="GORLE">GORLE GROUP</option>
              </select>
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
                  updateForm("active", event.target.checked)
                }
              />
            </label>
          </div>

          <button
            type="submit"
            className="primary-action"
            disabled={saving}
          >
            {saving
              ? editingEmployeeId !== null
                ? "Updating..."
                : "Creating..."
              : editingEmployeeId !== null
                ? "Update Employee"
                : "Create Employee"}
          </button>
        </form>
      )}

      <div className="employees-panel">
        <div className="employee-filters">
          <label className="employee-search">
            <span className="sr-only">Search employees</span>
            <input
              type="search"
              placeholder="Search employees..."
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>

          <label>
            <span className="sr-only">Filter by department</span>
            <select
              value={department}
              onChange={(event) =>
                setDepartment(event.target.value)
              }
            >
              <option>All Departments</option>
              {departments.map((item) => (
                <option key={item}>{item}</option>
              ))}
            </select>
          </label>

          <label>
            <span className="sr-only">Filter by company</span>
            <select
              value={company}
              onChange={(event) => setCompany(event.target.value)}
            >
              <option>All Companies</option>
              <option>CYOND</option>
              <option>GORLE GROUP</option>
            </select>
          </label>

          <label>
            <span className="sr-only">Filter by status</span>
            <select
              value={status}
              onChange={(event) => setStatus(event.target.value)}
            >
              <option>All Statuses</option>
              <option>Active</option>
              <option>Onboarding</option>
              <option>Pending Approval</option>
              <option>Offboarding</option>
              <option>Offboarded</option>
            </select>
          </label>
        </div>

        {loading && (
          <div className="employee-empty-state">
            <strong>Loading employees...</strong>
            <span>Fetching employee records from NexusHR.</span>
          </div>
        )}

        {!loading && error && (
          <div
            className="employee-empty-state"
            role="alert"
          >
            <strong>Unable to load employees</strong>
            <span>{error}</span>
            <button
              type="button"
              className="table-action"
              onClick={() => void loadEmployees()}
            >
              Retry
            </button>
          </div>
        )}

        {!loading && !error && (
          <>
            <div className="employees-table-wrapper">
              <table className="employees-table">
                <thead>
                  <tr>
                    <th>Employee</th>
                    <th>Company</th>
                    <th>Department</th>
                    <th>Designation</th>
                    <th>Role</th>
                    <th>Joining Date</th>
                    <th>Status</th>
                    <th>Salary</th>
                    <th>Actions</th>
                  </tr>
                </thead>

                <tbody>
                  {filteredEmployees.map((employee) => (
                    <tr key={employee.id}>
                      <td>
                        <div className="employee-name-cell">
                          <div className="employee-avatar">
                            {initials(employee.fullName)}
                          </div>

                          <div>
                            <strong>{employee.fullName}</strong>
                            <span>{employee.employeeCode}</span>
                          </div>
                        </div>
                      </td>

                      <td><strong>{employee.companyName}</strong></td>
                      <td>{employee.department}</td>
                      <td>{employee.designation}</td>
                      <td>{roleLabel(employee.roleType)}</td>
                      <td>{employee.joiningDate}</td>
                      <td>
                        <span
                          className={statusClass(
                            employee.lifecycleStatus,
                          )}
                        >
                          {statusLabel(
                            employee.lifecycleStatus,
                          )}
                        </span>
                      </td>
                      <td>
                        {`\u20B9`}{employee.baseSalary.toLocaleString("en-IN")}
                      </td>
                      <td>
                        <div className="employee-actions">
                          <button
                            type="button"
                            className="table-action"
                            onClick={() => navigate(`/employees/${employee.id}`)}
                          >
                            View
                          </button>

                          {canEditDeleteEmployees && (
                            <>
                              <button
                                type="button"
                                className="table-action"
                                onClick={() => handleEditEmployee(employee)}
                                disabled={saving}
                              >
                                Edit
                              </button>

                              <button
                                type="button"
                                className="table-action"
                                onClick={() => handleDeleteEmployee(employee)}
                                disabled={saving}
                              >
                                Delete
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}

                  {filteredEmployees.length === 0 && (
                    <tr>
                      <td colSpan={9}>
                        <div className="employee-empty-state">
                          <strong>No employees found</strong>
                          <span>
                            Try changing your search or filter criteria.
                          </span>
                        </div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            <div className="employees-footer">
              Showing <strong>{filteredEmployees.length}</strong> of{" "}
              <strong>{employees.length}</strong> employees
            </div>
          </>
        )}
      </div>

      {showDeleteConfirm && employeeToDelete && (
        <div className="employees-panel">
          <div className="employees-header">
            <div>
              <p className="page-eyebrow">Employee Lifecycle</p>
              <h2>Delete Employee</h2>
              <p>
                Are you sure you want to delete{" "}
                <strong>{employeeToDelete.fullName}</strong>?
              </p>
            </div>
          </div>

          <div className="employee-empty-state">
            <span>
              This action will remove the employee from the organization-wide
              employee directory.
            </span>

            {saveError && (
              <span role="alert">
                {saveError}
              </span>
            )}

            <div className="employee-actions">
              <button
                type="button"
                className="table-action"
                onClick={() => {
                  if (!saving) {
                    setShowDeleteConfirm(false);
                    setEmployeeToDelete(null);
                    setSaveError("");
                  }
                }}
                disabled={saving}
              >
                Cancel
              </button>

              <button
                type="button"
                className="table-action"
                onClick={() => void confirmDeleteEmployee()}
                disabled={saving}
              >
                {saving ? "Deleting..." : "Confirm Delete"}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
