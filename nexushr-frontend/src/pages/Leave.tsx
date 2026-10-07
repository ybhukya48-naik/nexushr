import { useEffect, useMemo, useState } from "react";
import {
  getCurrentEmployee,
  getEmployees,
  type EmployeeResponse,
} from "../api";
import {
  createLeave,
  getLeaveBalance,
  getLeaves,
  updateLeaveStatus,
  type LeaveBalance,
  type LeaveRequest,
} from "./Attendance";

export default function Leave() {
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [selectedCompany, setSelectedCompany] = useState("All Companies");
  const [leaves, setLeaves] = useState<LeaveRequest[]>([]);
  const [selectedEmployee, setSelectedEmployee] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [reason, setReason] = useState("");
  const [balance, setBalance] = useState<LeaveBalance | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const role = localStorage.getItem("nexushr_role") || "HR";

  const isEmployee = role === "EMPLOYEE";
  const companyEmployees = useMemo(
    () =>
      employees.filter(
        (employee) =>
          (employee.active === true ||
            employee.lifecycleStatus === "ACTIVE") &&
          (selectedCompany === "All Companies" ||
            employee.companyCode === selectedCompany),
      ),
    [employees, selectedCompany],
  );

  const companyLeaves = useMemo(
    () =>
      leaves.filter(
        (leave) =>
          selectedCompany === "All Companies" ||
          leave.employee.companyCode === selectedCompany,
      ),
    [leaves, selectedCompany],
  );

  async function load() {
    setError("");

    try {
      const leaveData = await getLeaves();
      setLeaves(leaveData);

      if (isEmployee) {
        const me = await getCurrentEmployee();

        setSelectedEmployee(String(me.id));
        setEmployees([me]);
        setSelectedCompany(me.companyCode || "CYOND");

        try {
          setBalance(await getLeaveBalance(me.id));
        } catch {
          // Balance is optional for rendering the page.
        }

        return;
      }

      const employeeData = await getEmployees();
      setEmployees(employeeData);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to load leave data.",
      );
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function selectEmployee(id: string) {
    setSelectedEmployee(id);
    setBalance(null);

    if (!id) return;

    try {
      setBalance(await getLeaveBalance(Number(id)));
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to load leave balance.",
      );
    }
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    setMessage("");
    setError("");

    if (!selectedEmployee || !startDate || !endDate || !reason.trim()) {
      setError("Please complete all leave request fields.");
      setSaving(false);
      return;
    }

    if (endDate < startDate) {
      setError("End date cannot be before the start date.");
      setSaving(false);
      return;
    }

    try {
      await createLeave(
        Number(selectedEmployee),
        startDate,
        endDate,
        reason.trim(),
      );

      setMessage("Leave request submitted successfully.");
      setStartDate("");
      setEndDate("");
      setReason("");

      await load();

      if (selectedEmployee) {
        try {
          setBalance(await getLeaveBalance(Number(selectedEmployee)));
        } catch {
          // Ignore optional balance refresh errors.
        }
      }
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to create leave request.",
      );
    } finally {
      setSaving(false);
    }
  }

  async function changeStatus(
    id: number,
    status: "APPROVED" | "REJECTED",
  ) {
    setMessage("");
    setError("");

    try {
      await updateLeaveStatus(id, status);
      setMessage(
        status === "APPROVED"
          ? "Leave request approved."
          : "Leave request rejected.",
      );
      await load();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to update leave status.",
      );
    }
  }




  return (
    <section className="leave-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">WORKFORCE OPERATIONS - F03</p>
          <h2>Leave Management</h2>
          <p>
            Request time off, track your balance and manage approvals.
          </p>
        </div>

        <div className="leave-header-badge">
          {isEmployee ? "My Leave" : "HR Operations"}
        </div>
      </div>

      {message && (
        <div className="payroll-message" role="status">
          {message}
        </div>
      )}

      {error && (
        <div className="payroll-error" role="alert">
          {error}
        </div>
      )}

      <div className="stats-grid">
        <Stat title="Total Requests" value={companyLeaves.length} />
        <Stat title="Pending" value={companyLeaves.filter((leave) => leave.status === "PENDING").length} />
        <Stat title="Approved" value={companyLeaves.filter((leave) => leave.status === "APPROVED").length} />
        {balance && (
          <Stat title="Days Remaining" value={balance.remainingDays} />
        )}
      </div>

      <section className="panel leave-request-panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">TIME OFF</p>
            <h3>New Leave Request</h3>
            <p>Submit a request for approval.</p>
          </div>
        </div>

        <form onSubmit={submit}>
          <div className="employee-filters">
            {!isEmployee && (
              <label>
                Company
                <select
                  value={selectedCompany}
                  onChange={(event) => {
                    setSelectedCompany(event.target.value);
                    setSelectedEmployee("");
                    setBalance(null);
                  }}
                >
                  <option value="All Companies">All Companies</option>
                  <option value="CYOND">CYOND</option>
                  <option value="GORLE">GORLE GROUP</option>
                </select>
              </label>
            )}

            <label>
              Employee
              {isEmployee ? (
                <input
                  value={
                    companyEmployees.find(
                      (employee) =>
                        String(employee.id) === selectedEmployee,
                    )?.fullName || "My Profile"
                  }
                  readOnly
                />
              ) : (
                <select
                  value={selectedEmployee}
                  onChange={(event) =>
                    void selectEmployee(event.target.value)
                  }
                  required
                >
                  <option value="">Select employee</option>
                  {companyEmployees.map((employee) => (
                    <option key={employee.id} value={employee.id}>
                      {employee.employeeCode} - {employee.fullName}
                    </option>
                  ))}
                </select>
              )}
            </label>

            <label>
              Start Date
              <input
                type="date"
                value={startDate}
                min={new Date().toISOString().slice(0, 10)}
                onChange={(event) => setStartDate(event.target.value)}
                required
              />
            </label>

            <label>
              End Date
              <input
                type="date"
                value={endDate}
                min={startDate || new Date().toISOString().slice(0, 10)}
                onChange={(event) => setEndDate(event.target.value)}
                required
              />
            </label>

            <label>
              Reason
              <input
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                placeholder="e.g. Personal work, vacation..."
                required
              />
            </label>

            <button
              className="primary-button"
              type="submit"
              disabled={saving}
            >
              {saving ? "Submitting..." : "Submit Request"}
            </button>
          </div>
        </form>
      </section>

      {balance && (
        <section className="panel">
          <div className="panel-header">
            <div>
              <p className="eyebrow">LEAVE BALANCE</p>
              <h3>Your Leave Balance</h3>
            </div>
          </div>

          <div className="stats-grid">
            <Stat
              title="Annual Entitlement"
              value={balance.annualEntitlementDays}
            />
            <Stat title="Used" value={balance.usedDays} />
            <Stat title="Remaining" value={balance.remainingDays} />
          </div>
        </section>
      )}

      <section className="panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">REQUEST HISTORY</p>
            <h3>Leave Requests</h3>
            <p>
              {companyLeaves.length === 0
                ? "No requests yet."
                : `${companyLeaves.length} request${companyLeaves.length === 1 ? "" : "s"} recorded.`}
            </p>
          </div>
        </div>

        <div className="employees-table-wrap">
          <table className="employees-table">
            <thead>
              <tr>
                <th>Employee</th>
                <th>Dates</th>
                <th>Days</th>
                <th>Reason</th>
                <th>Status</th>
                {!isEmployee && <th>Action</th>}
              </tr>
            </thead>

            <tbody>
              {companyLeaves.map((leave) => {
                const days =
                  Math.floor(
                    (new Date(leave.endDate).getTime() -
                      new Date(leave.startDate).getTime()) /
                      86400000,
                  ) + 1;

                return (
                  <tr key={leave.id}>
                    <td>
                      <strong>{leave.employee?.fullName || "-"}</strong>
                    </td>

                    <td>
                      {leave.startDate} to {leave.endDate}
                    </td>

                    <td>{days}</td>

                    <td>{leave.reason}</td>

                    <td>
                      <span
                        className={`employee-status ${
                          leave.status === "APPROVED"
                            ? "employee-status-active"
                            : leave.status === "PENDING"
                              ? "employee-status-probation"
                              : "employee-status-offboarding"
                        }`}
                      >
                        {leave.status}
                      </span>
                    </td>

                    {!isEmployee && (
                      <td>
                        {leave.status === "PENDING" ? (
                          <div
                            style={{
                              display: "flex",
                              gap: 8,
                              flexWrap: "wrap",
                            }}
                          >
                            <button
                              type="button"
                              className="table-action"
                              onClick={() =>
                                void changeStatus(
                                  leave.id!,
                                  "APPROVED",
                                )
                              }
                            >
                              Approve
                            </button>

                            <button
                              type="button"
                              className="table-action"
                              onClick={() =>
                                void changeStatus(
                                  leave.id!,
                                  "REJECTED",
                                )
                              }
                            >
                              Reject
                            </button>
                          </div>
                        ) : (
                          "-"
                        )}
                      </td>
                    )}
                  </tr>
                );
              })}

              {companyLeaves.length === 0 && (
                <tr>
                  <td colSpan={isEmployee ? 5 : 6}>
                    <div className="employee-empty-state">
                      <strong>No leave requests yet</strong>
                      <span>
                        Submit a request above to start your leave workflow.
                      </span>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </section>
  );
}

function Stat({ title, value }: { title: string; value: number }) {
  return (
    <div className="stat-card">
      <p>{title}</p>
      <h3>{value}</h3>
    </div>
  );
}
