import { useEffect, useRef, useState } from "react";
import "./EmployeeProfile.css";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import {
  fetchEmployeePhoto,
  getCurrentEmployee,
  getEmployee,
  updateEmployee,
  uploadEmployeePhoto,
  type EmployeeRequest,
  type EmployeeResponse,
  type EmployeeLifecycleStatus,
  type EmployeeRole,
} from "../api";
import {
  clockedWorkMinutes,
  getEmployeeAttendance,
  monthDateKeys,
  shownCheckOut,
  type AttendanceRecord,
} from "./Attendance";

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

function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? "")).toUpperCase();
}

export default function EmployeeProfile() {
  const navigate = useNavigate();
  const location = useLocation();
  const { id } = useParams();
  const mine = location.pathname === "/profile";
  const canSeePay = mine || ["HR", "ADMIN", "SUPER_ADMIN"].includes(localStorage.getItem("nexushr_role") || "");
  const employeeId = Number(id);
  const pictureInput = useRef<HTMLInputElement>(null);

  const [employee, setEmployee] = useState<EmployeeResponse | null>(null);
  const [photoUrl, setPhotoUrl] = useState("");
  const [photoError, setPhotoError] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");

  const [form, setForm] = useState<EmployeeRequest | null>(null);
  const [month, setMonth] = useState<AttendanceRecord[]>([]);
  const [monthLoading, setMonthLoading] = useState(true);

  useEffect(() => {
    if (!mine && (!Number.isInteger(employeeId) || employeeId <= 0)) {
      return;
    }

    let cancelled = false;

    const load = mine ? getCurrentEmployee() : getEmployee(employeeId);

    load
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
          companyCode: data.companyCode || "CYOND",
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
  }, [employeeId, mine]);

  useEffect(() => {
    const targetId = mine ? employee?.id : employeeId;
    if (!targetId) {
      return;
    }
    let cancelled = false;
    setMonthLoading(true);
    getEmployeeAttendance(targetId)
      .then((rows) => {
        if (!cancelled) {
          setMonth(Array.isArray(rows) ? rows : []);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setMonth([]);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setMonthLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [employee?.id, employeeId, mine]);

  useEffect(() => {
    if (!employee?.hasProfilePhoto) {
      setPhotoUrl("");
      return;
    }
    let cancelled = false;
    let objectUrl = "";
    fetchEmployeePhoto(employee.id)
      .then((url) => {
        if (cancelled || !url) {
          return;
        }
        objectUrl = url;
        setPhotoUrl(url);
      })
      .catch(() => {
        if (!cancelled) {
          setPhotoUrl("");
        }
      });
    return () => {
      cancelled = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [employee]);

  async function handlePicture(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !employee) {
      return;
    }
    setPhotoError("");
    try {
      const updated = await uploadEmployeePhoto(mine ? "me" : employee.id, file);
      setEmployee(updated);
    } catch (err) {
      setPhotoError(err instanceof Error ? err.message : "Could not save the profile picture.");
    }
  }

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
      companyCode: employee.companyCode || "CYOND",
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
        companyCode: updated.companyCode || "CYOND",
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

  if (!mine && (!Number.isInteger(employeeId) || employeeId <= 0)) {
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
      <div className="employees-header profile-hero">
        <div className="profile-identity">
          <button
            type="button"
            className="profile-picture"
            onClick={() => pictureInput.current?.click()}
          >
            {photoUrl ? <img src={photoUrl} alt="" /> : <span>{initials(employee.fullName)}</span>}
          </button>
          <div>
            <p className="page-eyebrow">{employee.employeeCode}</p>
            <h1>{employee.fullName}</h1>
            <p>{employee.designation || "No designation yet"}</p>
            <button
              type="button"
              className="table-action"
              onClick={() => pictureInput.current?.click()}
            >
              Add profile picture
            </button>
            {photoError && <span className="profile-picture-error">{photoError}</span>}
            <input
              ref={pictureInput}
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              hidden
              onChange={(event) => void handlePicture(event)}
            />
          </div>
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

            <p className="employee-password-note">
              Password is not shown here. The employee creates it on the sign-in page with this email.
            </p>

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
            </label>            <label>
              Company
              <select
                value={form.companyCode || "CYOND"}
                onChange={(event) =>
                  updateForm(
                    "companyCode",
                    event.target.value,
                  )
                }
              >
                <option value="CYOND">CYOND</option>
                <option value="GORLE">GORLE GROUP</option>
              </select>
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

            {canSeePay && (
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
            )}

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
            </label>            <label>
              Company
              <input
                value={employee.companyName || employee.companyCode || "CYOND"}
                readOnly
              />
            </label>

            <label>
              Designation
              <input value={employee.designation} readOnly />
            </label>

            <label>
              Joining Date
              <input value={employee.joiningDate} readOnly />
            </label>

            {canSeePay && (
            <label>
              Base Salary
              <input
                value={`\u20B9${employee.baseSalary.toLocaleString("en-IN")}`}
                readOnly
              />
            </label>
            )}

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
        <div className="employees-header">
          <div>
            <p className="page-eyebrow">ATTENDANCE</p>
            <h2>{employee.fullName} · last 30 days</h2>
            <p>Only this employee's check-in, check-out, and working hours.</p>
          </div>
        </div>
        <div className="employees-table-wrap">
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
              {monthLoading ? (
                <tr>
                  <td colSpan={4}>Loading this month...</td>
                </tr>
              ) : monthDateKeys().slice().reverse().map((date) => {
                const row = month.find((item) => item.attendanceDate?.slice(0, 10) === date);
                const checkOut = shownCheckOut(row);
                const minutes = row?.checkInTime ? clockedWorkMinutes(row) : 0;
                const shown = new Date(`${date}T00:00:00`);
                return (
                  <tr key={date}>
                    <td>{Number.isNaN(shown.getTime()) ? date : shown.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" })}</td>
                    <td>{row?.checkInTime ? row.checkInTime.slice(11, 19) : "—"}</td>
                    <td>{checkOut ? checkOut.slice(11, 19) : row?.checkInTime ? "Still in" : "—"}</td>
                    <td>{row?.checkInTime ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : "No check-in"}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      <div className="employees-panel">
        <div className="panel-header">
          <div>
            <p className="page-eyebrow">
              Lifecycle
            </p>
            <h2>Lifecycle Actions</h2>
            <p>
              Status changes are kept on this employee record.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}


