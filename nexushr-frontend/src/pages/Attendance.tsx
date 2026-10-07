import { API_BASE } from "../api";
import { useEffect, useState } from "react";
import {
  getCurrentEmployee,
  getEmployees,
  type EmployeeResponse,
} from "../api";

export interface AttendanceRecord {
  id: number;
  employee: EmployeeResponse;
  attendanceDate: string;
  checkInTime?: string;
  checkOutTime?: string;
  workMinutes: number;
  lateArrivalMinutes: number;
  overtimeMinutes: number;
}

export interface AttendanceMetrics {
  date: string;
  totalEmployees: number;
  present: number;
  checkedOut: number;
  late: number;
  overtime: number;
  workMinutes: number;
  overtimeMinutes: number;
}

function normalizeAttendanceDate(value?: string) {
  return String(value ?? "").trim().slice(0, 10);
}

async function attendanceRequest<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = localStorage.getItem("nexushr_token");
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(
    `${API_BASE}${path}`,
    { ...options, headers },
  );

  if (!response.ok) {
    let message = `Attendance API failed: ${response.status}`;

    try {
      const errorBody = await response.json();

      if (
        typeof errorBody?.message === "string" &&
        errorBody.message.trim()
      ) {
        message = errorBody.message;
      } else if (
        typeof errorBody?.detail === "string" &&
        errorBody.detail.trim()
      ) {
        message = errorBody.detail;
      } else if (
        typeof errorBody?.error === "string" &&
        errorBody.error.trim()
      ) {
        message = errorBody.error;
      }
    } catch {
      // Keep the HTTP status message when no JSON error body is available.
    }

    if (response.status === 401) {
      localStorage.removeItem("nexushr_token");
      localStorage.removeItem("nexushr_user");
      localStorage.removeItem("nexushr_role");
      message = "Your session has expired. Please log in again.";
    }

    if (response.status === 403) {
      message = "You do not have permission to perform this action.";
    }

    if (
      response.status === 409 &&
      message === "Attendance API failed: 409"
    ) {
      message =
        "This attendance action conflicts with the current attendance record.";
    }

    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}
export async function checkIn(employeeId?: number) {
  const endpoint =
    employeeId != null
      ? `/attendance/check-in?employeeId=${encodeURIComponent(String(employeeId))}`
      : "/attendance/check-in";

  return attendanceRequest<AttendanceRecord>(endpoint, {
    method: "POST",
  });
}

export async function checkOut(employeeId?: number) {
  const endpoint =
    employeeId != null
      ? `/attendance/check-out?employeeId=${encodeURIComponent(String(employeeId))}`
      : "/attendance/check-out";

  return attendanceRequest<AttendanceRecord>(endpoint, {
    method: "POST",
  });
}

export async function biometricCheckIn() {
  return attendanceRequest<AttendanceRecord>(
    "/attendance/biometric/check-in",
    { method: "POST" },
  );
}

export async function biometricCheckOut() {
  return attendanceRequest<AttendanceRecord>(
    "/attendance/biometric/check-out",
    { method: "POST" },
  );
}

export async function getAttendanceByDate(date: string) {
  return attendanceRequest<AttendanceRecord[]>(
    `/attendance?date=${encodeURIComponent(date)}`,
  );
}

interface AttendanceMetricsApiResponse {
  date: string;
  totalEmployees: number;
  presentEmployees: number;
  checkedOutEmployees: number;
  lateEmployees: number;
  overtimeEmployees: number;
  totalWorkMinutes: number;
  totalOvertimeMinutes: number;
}

export async function getAttendanceMetrics(
  date: string,
): Promise<AttendanceMetrics> {
  const response = await attendanceRequest<AttendanceMetricsApiResponse>(
    `/attendance/metrics?date=${encodeURIComponent(date)}`,
  );

  return {
    date: response.date,
    totalEmployees: response.totalEmployees,
    present: response.presentEmployees,
    checkedOut: response.checkedOutEmployees,
    late: response.lateEmployees,
    overtime: response.overtimeEmployees,
    workMinutes: response.totalWorkMinutes,
    overtimeMinutes: response.totalOvertimeMinutes,
  };
}

export async function getEmployeeAttendance(employeeId: number) {
  return attendanceRequest<AttendanceRecord[]>(
    `/attendance/employee/${employeeId}`,
  );
}

export interface LeaveRequest {
  id?: number;
  employee: EmployeeResponse;
  startDate: string;
  endDate: string;
  reason: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
}

export interface LeaveBalance {
  employeeId: number;
  annualEntitlementDays: number;
  usedDays: number;
  remainingDays: number;
}

export async function getLeaves() {
  return attendanceRequest<LeaveRequest[]>("/leaves");
}

export async function createLeave(
  employeeId: number,
  startDate: string,
  endDate: string,
  reason: string,
) {
  return attendanceRequest<LeaveRequest>("/leaves", {
    method: "POST",
    body: JSON.stringify({
      employee: { id: employeeId },
      startDate,
      endDate,
      reason,
    }),
  });
}

export async function updateLeaveStatus(
  id: number,
  status: "APPROVED" | "REJECTED",
) {
  return attendanceRequest<LeaveRequest>(
    `/leaves/${id}/status?status=${status}`,
    { method: "PATCH" },
  );
}

export async function getLeaveBalance(employeeId: number) {
  return attendanceRequest<LeaveBalance>(
    `/leaves/balance/${employeeId}`,
  );
}

export default function Attendance() {
  const getLocalDate = () => {
    const now = new Date();
    const offset = now.getTimezoneOffset();
    return new Date(now.getTime() - offset * 60000)
      .toISOString()
      .slice(0, 10);
  };

  const [date, setDate] = useState(getLocalDate());
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [metrics, setMetrics] = useState<AttendanceMetrics | null>(null);
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [selectedCompany, setSelectedCompany] = useState("All Companies");
  const [selectedEmployee, setSelectedEmployee] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [currentTime, setCurrentTime] = useState(() => new Date());
  const [attendanceLoaded, setAttendanceLoaded] = useState(false);
  const currentUsername = (
    localStorage.getItem("nexushr_user") ?? ""
  ).trim().toLowerCase();

  const currentRole = (
    localStorage.getItem("nexushr_role") ?? ""
  ).trim().toUpperCase();

  const today = getLocalDate();

  const currentEmployee = employees.find((employee) => {
    const employeeCode = employee.employeeCode?.trim().toLowerCase() ?? "";
    const email = employee.email?.trim().toLowerCase() ?? "";
    const fullName = employee.fullName?.trim().toLowerCase() ?? "";
    const roleType = employee.roleType?.trim().toUpperCase() ?? "";

    const identityMatches =
      employeeCode === currentUsername ||
      email === currentUsername ||
      fullName === currentUsername;

    const roleMatches =
      !currentRole || roleType === currentRole;

    return Boolean(employee.active) && identityMatches && roleMatches;
  });

  // Management users must explicitly select an employee before using
  // Check In / Check Out. "All employees" remains a view-only filter.
  const isManagementRole = ["ADMIN", "HR", "MANAGER"].includes(currentRole);

  const companyEmployees = employees.filter(
    (employee) =>
      employee.active === true &&
      (selectedCompany === "All Companies" ||
        employee.companyCode === selectedCompany),
  );

  const companyRecords = records.filter(
    (record) =>
      selectedCompany === "All Companies" ||
      record.employee.companyCode === selectedCompany,
  );

  const actionEmployee = isManagementRole
    ? selectedEmployee
      ? employees.find(
          (employee) =>
            employee.active === true &&
            String(employee.id) === String(selectedEmployee),
        )
      : undefined
    : currentEmployee;

  // Check In / Check Out are today-only operations, so their button state
  // must always come from today's attendance record.
  const actionEmployeeRecord =
    actionEmployee && date === today
      ? records.find(
          (record) =>
            normalizeAttendanceDate(record.attendanceDate) === normalizeAttendanceDate(today) &&
            String(record.employee.id) === String(actionEmployee.id),
        )
      : undefined;

  const isToday = date === today;
  const hasCheckedIn = Boolean(actionEmployeeRecord?.checkInTime);
  const hasCheckedOut = Boolean(actionEmployeeRecord?.checkOutTime);



  const attendanceRows = companyEmployees
    .filter((employee) => employee.active === true)
    .filter(
      (employee) =>
        !selectedEmployee ||
        String(employee.id) === String(selectedEmployee),
    )
    .map((employee) => ({
      employee,
      record: companyRecords.find(
        (record) => String(record.employee.id) === String(employee.id),
      ),
    }));


  function calculateFallbackMetrics(
    attendanceRecords: AttendanceRecord[],
  ): AttendanceMetrics {
    return {
      date,
      totalEmployees: employees.filter((employee) => employee.active === true).length,
      present: attendanceRecords.length,
      checkedOut: attendanceRecords.filter(
        (record) => Boolean(record.checkOutTime),
      ).length,
      late: attendanceRecords.filter(
        (record) => record.lateArrivalMinutes > 0,
      ).length,
      overtime: attendanceRecords.filter(
        (record) => record.overtimeMinutes > 0,
      ).length,
      workMinutes: attendanceRecords.reduce(
        (total, record) => total + (record.workMinutes || 0),
        0,
      ),
      overtimeMinutes: attendanceRecords.reduce(
        (total, record) => total + (record.overtimeMinutes || 0),
        0,
      ),
    };
  }

  async function load() {
    setLoading(true);
    setError("");
    setMessage("");
    setAttendanceLoaded(false);

    try {
      const isManagementRole = ["ADMIN", "HR", "MANAGER"].includes(
        currentRole,
      );

      if (!isManagementRole) {
        // Employee self-service:
        // resolve the authenticated employee through /employees/me,
        // then load only that employee's attendance records.
        const me = await getCurrentEmployee();

        const employeeRecords = await getEmployeeAttendance(me.id);

        console.log("[Attendance DEBUG] employee:", me.id, me.employeeCode);
        console.log("[Attendance DEBUG] today:", today);
        console.log("[Attendance DEBUG] selected date:", date);
        console.log("[Attendance DEBUG] employee records:", employeeRecords);

        const recordsForDate = employeeRecords.filter(
          (record) => normalizeAttendanceDate(record.attendanceDate) === normalizeAttendanceDate(date),
        );

        setEmployees([me]);
        setSelectedCompany(me.companyCode || "CYOND");
        setSelectedEmployee(String(me.id));
        setRecords(recordsForDate);
        setAttendanceLoaded(true);

        const employeeMetrics: AttendanceMetrics = {
          date,
          totalEmployees: 1,
          present: recordsForDate.length,
          checkedOut: recordsForDate.filter(
            (record) => Boolean(record.checkOutTime),
          ).length,
          late: recordsForDate.filter(
            (record) => record.lateArrivalMinutes > 0,
          ).length,
          overtime: recordsForDate.filter(
            (record) => record.overtimeMinutes > 0,
          ).length,
          workMinutes: recordsForDate.reduce(
            (total, record) => total + (record.workMinutes || 0),
            0,
          ),
          overtimeMinutes: recordsForDate.reduce(
            (total, record) => total + (record.overtimeMinutes || 0),
            0,
          ),
        };

        setMetrics(employeeMetrics);
        return;
      }

      // Management workflow remains unchanged.
      const [recordsResult, metricsResult, employeesResult] =
        await Promise.allSettled([
          getAttendanceByDate(date),
          getAttendanceMetrics(date),
          getEmployees(),
        ]);

      let recordsData: AttendanceRecord[] = [];
      let employeesData: EmployeeResponse[] = [];
      let metricsData: AttendanceMetrics | null = null;

      if (recordsResult.status === "fulfilled") {
        recordsData = recordsResult.value;
        setRecords(recordsData);
        setAttendanceLoaded(true);
      } else {
        setRecords([]);
      }

      if (employeesResult.status === "fulfilled") {
        employeesData = employeesResult.value;
        setEmployees(employeesData);
      }

      if (metricsResult.status === "fulfilled") {
        metricsData = metricsResult.value;
        setMetrics(metricsData);
      } else {
        const availableEmployees =
          employeesData.length > 0 ? employeesData : employees;

        setMetrics({
          ...calculateFallbackMetrics(recordsData),
          totalEmployees: availableEmployees.length,
        });
      }

      const failures: string[] = [];

      if (recordsResult.status === "rejected") {
        failures.push("attendance records");
      }

      if (metricsResult.status === "rejected") {
        console.warn("Attendance metrics unavailable; using record totals.");
      }

      if (employeesResult.status === "rejected") {
        failures.push("employee directory");
      }

      if (failures.length > 0) {
        setError(
          `Some attendance information could not be loaded: ${failures.join(
            ", ",
          )}.`,
        );
      }

      if (metricsData) {
        setMetrics(metricsData);
      }
    } catch (err) {
      console.error(err);
      setError(
        err instanceof Error
          ? err.message
          : "Unable to load attendance information right now.",
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [date]);

  // Keep the attendance duration live while an employee is checked in.
  useEffect(() => {
    const hasOpenAttendance = records.some(
      (record) =>
        Boolean(record.checkInTime) &&
        !record.checkOutTime &&
        normalizeAttendanceDate(record.attendanceDate) === normalizeAttendanceDate(today),
    );

    if (!hasOpenAttendance) {
      return;
    }

    const timer = window.setInterval(() => {
      setCurrentTime(new Date());
    }, 1000);

    return () => window.clearInterval(timer);
  }, [records, today]);

  async function runAction(
    action: () => Promise<AttendanceRecord>,
    success: string,
  ) {
    setMessage("");
    setError("");

    if (!attendanceLoaded) {
      setError(
        "Attendance records are still loading. Please refresh and try again.",
      );
      return;
    }

    try {
      await action();
      setMessage(success);
      await load();
    } catch (err) {
      console.error(err);
      setError(
        err instanceof Error
          ? err.message.replace(/^Attendance API failed:\s*/i, "")
          : "Attendance action could not be completed.",
      );
    }
  }

  const visibleRecords = companyRecords.filter(
    (record) =>
      !selectedEmployee ||
      String(record.employee.id) === selectedEmployee,
  );

  const visibleMetrics: AttendanceMetrics = selectedEmployee
    ? {
        ...calculateFallbackMetrics(visibleRecords),
        totalEmployees: visibleRecords.length > 0 ? 1 : 0,
      }
    : metrics ?? calculateFallbackMetrics(records);

  function formatTime(value?: string) {
    if (!value) {
      return "-";
    }

    const parsed = new Date(value);

    if (Number.isNaN(parsed.getTime())) {
      return value;
    }

    return parsed.toLocaleTimeString("en-IN", {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  }

  function formatDate(value: string) {
    const parsed = new Date(`${value}T00:00:00`);

    if (Number.isNaN(parsed.getTime())) {
      return value;
    }

    return parsed.toLocaleDateString("en-IN", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
  }


  function formatWorkDuration(record?: AttendanceRecord) {
    if (!record?.checkInTime) {
      return "-";
    }

    const start = new Date(record.checkInTime);

    if (Number.isNaN(start.getTime())) {
      return "-";
    }

    const end = record.checkOutTime
      ? new Date(record.checkOutTime)
      : currentTime;

    if (Number.isNaN(end.getTime())) {
      return "-";
    }

    const elapsedSeconds = Math.max(
      0,
      Math.floor((end.getTime() - start.getTime()) / 1000),
    );

    const hours = Math.floor(elapsedSeconds / 3600);
    const minutes = Math.floor((elapsedSeconds % 3600) / 60);
    const seconds = elapsedSeconds % 60;

    return [
      String(hours).padStart(2, "0"),
      String(minutes).padStart(2, "0"),
      String(seconds).padStart(2, "0"),
    ].join(":");
  }

  return (
    <>
      <div className="page-heading attendance-heading">
        <div>
          <p className="eyebrow">WORKFORCE OPERATIONS</p>
          <h2>Attendance</h2>
          <p>
            Monitor daily attendance, working hours, late arrivals and
            overtime across your workforce.
          </p>
        </div>

        <div className="attendance-actions">
          <button
            className="secondary-button"
            onClick={() =>
              void runAction(
                () => checkIn(actionEmployee?.id),
                "Check-in completed successfully.",

              )
            }
            disabled={
              loading ||
              !attendanceLoaded ||
              !isToday ||
              !actionEmployee ||
              hasCheckedIn
            }
            title={
              !isToday
                ? "Check-in is available only for today."
                : hasCheckedIn
                  ? "You have already checked in today."
                  : undefined
            }
          >
            {loading
              ? "Checking..."
              : hasCheckedIn
                ? "Checked In"
                : "Check In"}
          </button>

          <button
            className="primary-button"
            onClick={() =>
              void runAction(
                () => checkOut(actionEmployee?.id),
                "Check-out completed successfully.",

              )
            }
            disabled={
              loading ||
              !attendanceLoaded ||
              !isToday ||
              !actionEmployee ||
              !hasCheckedIn ||
              hasCheckedOut
            }
            title={
              !isToday
                ? "Check-out is available only for today."
                : !hasCheckedIn
                  ? "Check in before checking out."
                  : hasCheckedOut
                    ? "You have already checked out today."
                    : undefined
            }
          >
            {loading
              ? "Checking..."
              : hasCheckedOut
                ? "Checked Out"
                : "Check Out"}
          </button>
        </div>
      </div>

      <section className="panel attendance-toolbar-panel">
        <div className="attendance-toolbar-header">
          <div>
            <p className="eyebrow">DAILY VIEW</p>
            <h3>Attendance filters</h3>
            <p>Select a date and employee to review attendance activity.</p>
          </div>

          <button
            className="secondary-button attendance-refresh"
            onClick={() => void load()}
            disabled={loading}
          >
            {loading ? "Refreshing..." : " Refresh"}
          </button>
        </div>

        <div className="attendance-toolbar">
          <label>
            <span>Date</span>
            <input
              type="date"
              value={date}
              onChange={(event) => setDate(event.target.value)}
            />
          </label>

          <label>
            <span>Company</span>
            <select
              value={selectedCompany}
              onChange={(event) => {
                if (isManagementRole) {
                  setSelectedCompany(event.target.value);
                  setSelectedEmployee("");
                }
              }}
              disabled={!isManagementRole}
            >
              <option value="All Companies">All Companies</option>
              <option value="CYOND">CYOND</option>
              <option value="GORLE">GORLE GROUP</option>
            </select>
          </label>

          <label>
            <span>Employee</span>
            <select
              value={selectedEmployee}
              onChange={(event) => setSelectedEmployee(event.target.value)}
              disabled={!isManagementRole}
            >
              {isManagementRole && (
                <option value="">All employees</option>
              )}

              {companyEmployees.map((employee) => (
                <option key={employee.id} value={employee.id}>
                  {employee.fullName} - {employee.employeeCode}
                </option>
              ))}
            </select>
          </label>

          <div className="attendance-selected-date">
            <span>Viewing</span>
            <strong>{formatDate(date)}</strong>
          </div>
        </div>
      </section>

      {message && (
        <div className="attendance-notice attendance-notice-success">
          <strong></strong>
          <span>{message}</span>
        </div>
      )}

      {error && (
        <div className="attendance-notice attendance-notice-warning">
          <strong>!</strong>
          <span>{error}</span>
        </div>
      )}

      {metrics && (
        <div className="attendance-metrics-grid">
          <article className="attendance-metric-card">
            <div className="attendance-metric-icon">P</div>
            <span>Present</span>
            <strong>{visibleMetrics.present.toLocaleString()}</strong>
            <small>Employees with attendance</small>
          </article>

          <article className="attendance-metric-card">
            <div className="attendance-metric-icon"></div>
            <span>Checked Out</span>
            <strong>{visibleMetrics.checkedOut.toLocaleString()}</strong>
            <small>Completed work sessions</small>
          </article>

          <article className="attendance-metric-card">
            <div className="attendance-metric-icon">L</div>
            <span>Late Arrivals</span>
            <strong>{visibleMetrics.late.toLocaleString()}</strong>
            <small>Late attendance records</small>
          </article>

          <article className="attendance-metric-card">
            <div className="attendance-metric-icon">OT</div>
            <span>Overtime</span>
            <strong>{visibleMetrics.overtime.toLocaleString()}</strong>
            <small>Records with overtime</small>
          </article>
        </div>
      )}

      <section className="panel attendance-records-panel">
        <div className="panel-header attendance-records-header">
          <div>
            <p className="eyebrow">ATTENDANCE LOG</p>
            <h3>Daily Attendance Records</h3>
            <p>
              {loading
                ? "Loading attendance activity..."
                : `${attendanceRows.length} employee${
                    attendanceRows.length === 1 ? "" : "s"
                  } for ${formatDate(date)}`}
            </p>
          </div>

          {selectedEmployee && (
            <button
              className="panel-link"
              onClick={() => setSelectedEmployee("")}
            >
              Show all employees
            </button>
          )}
        </div>

        <div className="employees-table-wrap attendance-table-wrap">
          <table className="employees-table attendance-table">
            <thead>
              <tr>
                <th>Employee</th>
                <th>Date</th>
                <th>Check In</th>
                <th>Check Out</th>
                <th>Work Hours</th>
                <th>Late</th>
                <th>Overtime</th>
              </tr>
            </thead>

            <tbody>
  {attendanceRows.map(({ employee, record }) => (
    <tr key={employee.id}>
      <td>
        <div className="attendance-employee-cell">
          <strong>{employee.fullName}</strong>
          <span>{employee.employeeCode}</span>
        </div>
      </td>

      <td>{formatDate(date)}</td>

      <td>
        <span className="attendance-time">
          {record ? formatTime(record.checkInTime) : "-"}
        </span>
      </td>

      <td>
        <span className="attendance-time">
          {record ? formatTime(record.checkOutTime) : "-"}
        </span>
      </td>

      <td>
        {record ? (
          <span className="attendance-work-duration">
            {formatWorkDuration(record)}
          </span>
        ) : (
          "-"
        )}
      </td>

      <td>
        {record ? (
          <span
            className={
              record.lateArrivalMinutes > 0
                ? "attendance-value-warning"
                : "attendance-value-normal"
            }
          >
            {record.lateArrivalMinutes > 0
              ? `${record.lateArrivalMinutes} min`
              : "On time"}
          </span>
        ) : (
          <span className="attendance-value-normal">-</span>
        )}
      </td>

      <td>
        {record ? (
          <span
            className={
              record.overtimeMinutes > 0
                ? "attendance-value-positive"
                : "attendance-value-normal"
            }
          >
            {record.overtimeMinutes > 0
              ? `${record.overtimeMinutes} min`
              : "-"}
          </span>
        ) : (
          <span className="attendance-value-normal">-</span>
        )}
      </td>
    </tr>
  ))}

  {!loading && attendanceRows.length === 0 && (
    <tr>
      <td colSpan={7}>
        <div className="attendance-empty">
          <div className="attendance-empty-icon">-</div>
          <strong>No active employees found</strong>
          <span>
            There are no employees matching the selected filter.
          </span>
        </div>
      </td>
    </tr>
  )}
</tbody>
          </table>
        </div>
      </section>
    </>
  );
}




