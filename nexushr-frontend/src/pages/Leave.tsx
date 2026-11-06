import { isCompanyMatch } from "../utils/company";
import { asWorkspaceCompany, getWorkspaceCompany } from "../utils/workspace";
import { useEffect, useMemo, useState } from "react";
import {
  getCurrentEmployee,
  getEmployees,
  getHolidays,
  type CompanyHoliday,
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
  const [selectedCompany, setSelectedCompany] = useState<string>(
    () => getWorkspaceCompany() ?? "CYOND",
  );
  const [leaves, setLeaves] = useState<LeaveRequest[]>([]);
  const [selectedEmployee, setSelectedEmployee] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [reason, setReason] = useState("");
  const [balance, setBalance] = useState<LeaveBalance | null>(null);
  const [leaveType, setLeaveType] = useState("CASUAL");
  const [halfDay, setHalfDay] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [tab, setTab] = useState<"mine" | "calendar" | "approvals" | "holidays" | "policy">("mine");
  const [holidays, setHolidays] = useState<CompanyHoliday[]>([]);

  const role = localStorage.getItem("nexushr_role") || "HR";

  const isEmployee = role === "EMPLOYEE";
  const companyEmployees = useMemo(
    () =>
      employees.filter(
        (employee) =>
          (employee.active === true ||
            employee.lifecycleStatus === "ACTIVE") &&
          (selectedCompany === "All Companies" ||
            isCompanyMatch(employee.companyCode, selectedCompany)),
      ),
    [employees, selectedCompany],
  );

  const companyLeaves = useMemo(
    () =>
      leaves.filter((leave) => {
        if (isEmployee) {
          return true;
        }
        const employee = leave.employee as LeaveRequest["employee"] & {
          company?: { code?: string };
        };
        const code = employee?.companyCode || employee?.company?.code || "";
        if (!code || selectedCompany === "All Companies") {
          return true;
        }
        return asWorkspaceCompany(code) === asWorkspaceCompany(selectedCompany);
      }),
    [leaves, selectedCompany, isEmployee],
  );

  async function load() {
    setError("");

    try {
      const leaveData = await getLeaves();
      setLeaves(leaveData);
      setHolidays(await getHolidays().catch(() => []));

      if (isEmployee) {
        const me = await getCurrentEmployee();

        setSelectedEmployee(String(me.id));
        setEmployees([me]);
        setSelectedCompany(me.companyCode || "CYOND");
        try {
          setBalance(await getLeaveBalance(me.id));
        } catch {
          setBalance(null);
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
    if (!id) {
      setBalance(null);
      return;
    }
    try {
      setBalance(await getLeaveBalance(Number(id)));
    } catch {
      setBalance(null);
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
        halfDay ? startDate : endDate,
        reason.trim(),
        leaveType,
        halfDay,
      );

      setMessage("Leave request submitted successfully.");
      setStartDate("");
      setEndDate("");
      setReason("");

      await load();

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
    if (status === "REJECTED" && !window.confirm("Reject this leave request?")) {
      return;
    }

    try {
      await updateLeaveStatus(id, status);
      setMessage(
        status === "APPROVED"
          ? "Leave request approved."
          : "Leave request rejected.",
      );
      await load();
      if (selectedEmployee) {
        try {
          setBalance(await getLeaveBalance(Number(selectedEmployee)));
        } catch {
          setBalance(null);
        }
      }
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to update leave status.",
      );
    }
  }




  const pending = companyLeaves.filter((leave) => leave.status === "PENDING");
  const balanceLeaves = companyLeaves.filter(
    (leave) => !selectedEmployee || String(leave.employee?.id) === selectedEmployee,
  );
  const monthNow = new Date();
  const monthKey = `${monthNow.getFullYear()}-${String(monthNow.getMonth() + 1).padStart(2, "0")}`;
  const casualThisMonth = balanceLeaves.filter((leave) =>
    (leave.status === "APPROVED" || leave.status === "PENDING")
    && (leave.leaveType || "EARNED") === "CASUAL"
    && leave.startDate.startsWith(monthKey),
  ).length;
  const paidThisMonth = balanceLeaves.filter((leave) =>
    (leave.status === "APPROVED" || leave.status === "PENDING")
    && (leave.leaveType || "EARNED") !== "CASUAL"
    && leave.startDate.startsWith(monthKey),
  ).length;
  const shown = new Date(`${startDate || "2000-01-01"}T00:00:00`);
  const end = new Date(`${endDate || startDate || "2000-01-01"}T00:00:00`);
  let workingDays = 0;
  if (startDate && endDate && end >= shown) {
    for (let day = new Date(shown); day <= end; day.setDate(day.getDate() + 1)) {
      const key = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
      const holiday = holidays.some((item) => item.holidayDate === key);
      if (day.getDay() !== 0 && !holiday) {
        workingDays += 1;
      }
    }
  }

  function typeName(value?: string) {
    if (value === "CASUAL") return "Casual";
    if (value === "SICK") return "Sick";
    if (value === "COMP_OFF") return "Comp-off";
    if (value === "LOP") return "LOP";
    return "Earned";
  }

  function taken(type: string) {
    return balanceLeaves
      .filter((leave) => leave.status === "APPROVED" && (leave.leaveType || "EARNED") === type)
      .reduce((sum, leave) => sum + (leave.halfDay && leave.startDate === leave.endDate ? 0.5 : 1), 0);
  }

  const teamOff = companyLeaves.filter((leave) =>
    leave.status === "APPROVED"
    && (leave.startDate.startsWith(monthKey) || leave.endDate.startsWith(monthKey)),
  );

  return (
    <section className="leave-page">
      <div className="ux-tabs">
        <button type="button" className={tab === "mine" ? "active" : ""} onClick={() => setTab("mine")}>{isEmployee ? "My leave" : "Leave"}</button>
        <button type="button" className={tab === "calendar" ? "active" : ""} onClick={() => setTab("calendar")}>Team calendar</button>
        <button type="button" className={tab === "approvals" ? "active" : ""} onClick={() => setTab("approvals")}>Approvals ({pending.length})</button>
        <button type="button" className={tab === "holidays" ? "active" : ""} onClick={() => setTab("holidays")}>Holidays</button>
        <button type="button" className={tab === "policy" ? "active" : ""} onClick={() => setTab("policy")}>Policy</button>
        {isEmployee && (
          <button type="button" className="ux-tabs-action" onClick={() => setTab("mine")}>Apply leave</button>
        )}
      </div>

      <div className="ux-balances">
        <article>
          <span>Casual</span>
          <strong>{Math.max(0, 1 - casualThisMonth)}</strong>
          <em>of 1 this month</em>
        </article>
        <article>
          <span>Sick</span>
          <strong>{taken("SICK")}</strong>
          <em>taken</em>
        </article>
        <article>
          <span>Earned</span>
          <strong>{Math.max(0, 1 - paidThisMonth)}</strong>
          <em>of 1 this month · {balance?.remainingDays ?? "—"} left this year</em>
        </article>
        <article>
          <span>Comp-off</span>
          <strong>{taken("COMP_OFF")}</strong>
          <em>approved</em>
        </article>
        <article>
          <span>LOP taken</span>
          <strong>{taken("LOP")}</strong>
          <em>days</em>
        </article>
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

      {tab === "calendar" && (
        <section className="panel">
          <h3>Team calendar</h3>
          <p>Approved leave this month.</p>
          {teamOff.length === 0 ? (
            <p>Nobody is on approved leave this month.</p>
          ) : (
            <ul>
              {teamOff.map((leave) => (
                <li key={leave.id}>
                  {leave.employee?.fullName || "Employee"} · {typeName(leave.leaveType)} · {leave.startDate} to {leave.endDate}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {tab === "holidays" && (
        <section className="panel">
          <div className="panel-header">
            <h3>Holidays</h3>
            <p>Company holidays. These days are not counted as leave.</p>
          </div>
          {holidays.length === 0 ? (
            <p>No holidays are saved yet.</p>
          ) : (
            <ul>
              {holidays
                .slice()
                .sort((a, b) => a.holidayDate.localeCompare(b.holidayDate))
                .map((holiday) => (
                  <li key={holiday.id}>
                    {new Date(`${holiday.holidayDate}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" })}
                    {" · "}
                    {holiday.name}
                  </li>
                ))}
            </ul>
          )}
        </section>
      )}

      {tab === "policy" && (
        <section className="panel">
          <h3>Leave policy</h3>
          <p>One casual leave and one paid leave each month. The year balance is the saved entitlement. Sundays and listed holidays are not counted.</p>
          <p>A request can be a full day or a half day. It stays pending until someone approves or rejects it.</p>
        </section>
      )}

      {(tab === "mine" || tab === "approvals") && (
      <div className={tab === "mine" && isEmployee ? "ux-split" : "ux-stack"}>
      {tab === "mine" && isEmployee && (
      <section className="panel leave-request-panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">Apply leave</p>
            <h3>Apply leave</h3>
            <p>
              {halfDay
                ? "0.5 working day. Balance changes after this request is approved."
                : workingDays > 0
                ? `${workingDays} working day${workingDays === 1 ? "" : "s"}. Sundays and holidays are not counted. Balance changes after approval.`
                : "Choose the type and the dates. Sundays and holidays are not counted."}
            </p>
          </div>
        </div>

        <form onSubmit={submit}>
          <div className="employee-filters">
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
              Type
              <select value={leaveType} onChange={(event) => setLeaveType(event.target.value)}>
                <option value="CASUAL">Casual</option>
                <option value="EARNED">Paid</option>
              </select>
            </label>

            <label>
              Day
              <select
                value={halfDay ? "half" : "full"}
                onChange={(event) => setHalfDay(event.target.value === "half")}
              >
                <option value="full">Full day</option>
                <option value="half">Half day</option>
              </select>
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
              {saving ? "Sending..." : "Apply leave"}
            </button>
          </div>
        </form>
      </section>
      )}

      <section className="panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">{tab === "approvals" ? "Approvals" : isEmployee ? "My requests" : "Leave"}</p>
            <h3>{tab === "approvals" ? "Waiting for a decision" : "Leave requests"}</h3>
            <p>
              {companyLeaves.length === 0
                ? isEmployee
                  ? "No leave requests yet. Apply leave to add one."
                  : "No leave requests yet."
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
              {(tab === "approvals" ? pending : companyLeaves).map((leave) => {
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
                      <div>{leave.leaveType === "COMP_OFF" ? "Comp-off" : leave.leaveType === "LOP" ? "LOP" : leave.leaveType === "SICK" ? "Sick" : leave.leaveType === "CASUAL" ? "Casual" : "Earned"}{leave.halfDay ? " · half day" : ""}</div>
                    </td>

                    <td>
                      <span>
                        {new Date(`${leave.startDate}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" })}
                        {" to "}
                        {new Date(`${leave.endDate}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" })}
                      </span>
                      {leave.status === "PENDING" && leave.endDate < new Date().toISOString().slice(0, 10) && (
                        <span>Dates passed</span>
                      )}
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
                        {leave.status === "APPROVED" ? "Approved" : leave.status === "REJECTED" ? "Rejected" : "Pending"}
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
                              Approve leave
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
                              Reject leave
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

              {(tab === "approvals" ? pending : companyLeaves).length === 0 && (
                <tr>
                  <td colSpan={isEmployee ? 5 : 6}>
                    <div className="employee-empty-state">
                      <strong>No leave requests yet</strong>
                      <span>
                        {isEmployee ? "Apply leave to add one." : "Requests show here when an employee applies."}
                      </span>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
      </div>
      )}
    </section>
  );
}


