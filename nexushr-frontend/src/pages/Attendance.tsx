import { isCompanyMatch } from "../utils/company";
import { getWorkspaceCompany } from "../utils/workspace";
import { API_BASE, clearWebsiteSession } from "../api";
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
  sessionStartedAt?: string | null;
  closedWorkMinutes?: number;
  breakMinutes?: number;
  attendanceStatus?: string | null;
  lateWarning?: boolean;
  yellowFlag?: boolean;
  redFlag?: boolean;
  blueFlag?: boolean;
  deductedMinutes?: number;
  penaltyNote?: string | null;
  selfieCaptured?: boolean;
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

function logoutDeadlineMs(record?: { attendanceDate?: string; checkInTime?: string } | null) {
  const dateKey = String(record?.attendanceDate || record?.checkInTime || "").slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dateKey)) {
    return null;
  }
  const deadline = new Date(`${dateKey}T23:00:00+05:30`);
  return Number.isNaN(deadline.getTime()) ? null : deadline.getTime();
}

/** Office punches work on a Windows or Mac computer. A phone stays blocked. */
export function isWindowsComputer() {
  if (typeof navigator === "undefined") {
    return false;
  }
  const ua = navigator.userAgent || "";
  if (/Android|iPhone|iPad|iPod|Mobile|Windows Phone/i.test(ua)) {
    return false;
  }
  const platform = (navigator as Navigator & { userAgentData?: { platform?: string } }).userAgentData?.platform;
  if (platform) {
    return platform === "Windows" || platform === "macOS";
  }
  return /Windows NT|Macintosh|Mac OS X/i.test(ua);
}

/** A finished day has a check-out, or it is past 11:00 PM. An open check-in is not finished. */
export function dayIsComplete(
  record?: { checkInTime?: string; checkOutTime?: string; attendanceDate?: string } | null,
  now = new Date(),
) {
  if (!record?.checkInTime) {
    return false;
  }
  return Boolean(record.checkOutTime) || isCheckoutMissing(record, now);
}

/** A missed check-out is only after 11:00 PM. Signing out of the site is not a check-out. */
export function isCheckoutMissing(
  record?: { checkInTime?: string; checkOutTime?: string; attendanceDate?: string } | null,
  now = new Date(),
) {
  if (!record?.checkInTime || record.checkOutTime) {
    return false;
  }
  const deadline = logoutDeadlineMs(record);
  return deadline != null && now.getTime() >= deadline;
}

/** Checkout to show. After 11:00 PM the day is closed even if the punch was missed. */
export function shownCheckOut(
  record?: { checkInTime?: string; checkOutTime?: string; attendanceDate?: string } | null,
  now = new Date(),
) {
  if (record?.checkOutTime) {
    return record.checkOutTime;
  }
  if (!record?.checkInTime) {
    return undefined;
  }
  const deadline = logoutDeadlineMs(record);
  if (deadline != null && now.getTime() >= deadline) {
    const dateKey = String(record.attendanceDate || record.checkInTime).slice(0, 10);
    return `${dateKey}T23:00:00`;
  }
  return undefined;
}

export function clockedWorkMinutes(
  record?: (Pick<AttendanceRecord, "checkInTime" | "checkOutTime" | "breakMinutes" | "workMinutes"> & {
    attendanceDate?: string;
  }) | null,
  now = new Date(),
) {
  if (!record?.checkInTime) {
    return Math.max(0, record?.workMinutes ?? 0);
  }
  const start = new Date(record.checkInTime);
  if (Number.isNaN(start.getTime())) {
    return Math.max(0, record.workMinutes ?? 0);
  }
  const deadline = logoutDeadlineMs(record);
  const pastLogout = !record.checkOutTime && deadline != null && now.getTime() >= deadline;
  const end = record.checkOutTime
    ? new Date(record.checkOutTime)
    : pastLogout && deadline != null
      ? new Date(deadline)
      : now;
  if (Number.isNaN(end.getTime())) {
    return Math.max(0, record.workMinutes ?? 0);
  }
  const breakMinutes = Math.max(0, record.breakMinutes ?? 0);
  const checkedIn = Math.max(
    0,
    Math.floor((end.getTime() - start.getTime()) / 60000) - breakMinutes,
  );
  if (!record.checkOutTime && !pastLogout) {
    return checkedIn;
  }
  const lunchStillDue = Math.max(0, 60 - breakMinutes);
  const afterEightHours = Math.max(0, checkedIn + breakMinutes - 8 * 60);
  return Math.max(0, checkedIn - Math.min(lunchStillDue, afterEightHours));
}

const DASHBOARD_SNAPSHOT = "nexushr_dashboard_snapshot";

export function readDashboardSnapshot(): { employee: EmployeeResponse; records: AttendanceRecord[] } | null {
  try {
    const parsed = JSON.parse(localStorage.getItem(DASHBOARD_SNAPSHOT) || "null");
    if (!parsed?.employee?.id || !Array.isArray(parsed.records)) {
      return null;
    }
    return {
      employee: parsed.employee as EmployeeResponse,
      records: parsed.records.filter((row: AttendanceRecord) => row?.attendanceDate),
    };
  } catch {
    return null;
  }
}

export function writeDashboardSnapshot(employee: EmployeeResponse, records: AttendanceRecord[]) {
  try {
    localStorage.setItem(DASHBOARD_SNAPSHOT, JSON.stringify({
      employee,
      records: records.slice(0, 40),
    }));
  } catch {
    // The page still works if the browser refuses the saved copy.
  }
}

export function readMyAttendanceCache() {
  return readDashboardSnapshot()?.records ?? [];
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
      clearWebsiteSession();
      message = "Your session has expired. Please log in again.";
    }

    if (response.status === 403 && message.startsWith("Attendance API failed:")) {
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

async function postSiteSelfie(path: string, selfie: File, failure: string) {
  const token = localStorage.getItem("nexushr_token");
  const body = new FormData();
  body.append("selfie", selfie);
  const headers = new Headers();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers,
    body,
  });
  if (!response.ok) {
    let message = failure;
    try {
      const errorBody = await response.json();
      message = errorBody?.message || errorBody?.detail || message;
    } catch {
      message = `${failure}: ${response.status}`;
    }
    throw new Error(message);
  }
  return response.json() as Promise<AttendanceRecord>;
}

export async function siteCheckIn(selfie: File) {
  return postSiteSelfie("/attendance/site-check-in", selfie, "Site check-in failed");
}

export async function siteCheckOut(selfie: File) {
  return postSiteSelfie("/attendance/site-check-out", selfie, "Site check-out failed");
}

export async function sendAttendanceHeartbeat() {
  return attendanceRequest<{ status: string }>("/attendance/heartbeat", {
    method: "POST",
  });
}

export interface AttendanceAuditEntry {
  id: number;
  employeeId: number;
  actorName: string;
  action: string;
  detail: string;
  attendanceDate?: string;
  createdAt: string;
}

export async function getAttendanceAudit(employeeId?: number) {
  const query = employeeId == null ? "" : `?employeeId=${encodeURIComponent(String(employeeId))}`;
  return attendanceRequest<AttendanceAuditEntry[]>(`/attendance/audit${query}`);
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

export async function getAttendanceRange(from: string, to: string) {
  return attendanceRequest<AttendanceRecord[]>(
    `/attendance/range?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
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

export function HoursTotals({ records, now }: { records: AttendanceRecord[]; now: Date }) {
  const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
  const today = `${month}-${String(now.getDate()).padStart(2, "0")}`;
  const todayRow = records.find((row) => String(row.attendanceDate ?? "").slice(0, 10) === today);
  const monthMinutes = records
    .filter((row) => {
      const date = String(row.attendanceDate ?? "").slice(0, 10);
      return date >= `${month}-01` && date <= today;
    })
    .reduce((sum, row) => sum + clockedWorkMinutes(row, now), 0);
  const historyMinutes = records.reduce((sum, row) => sum + clockedWorkMinutes(row, now), 0);
  const asHours = (minutes: number) => `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
  return (
    <div className="hours-totals">
      <article>
        <span>Today working hours</span>
        <strong>{asHours(clockedWorkMinutes(todayRow, now))}</strong>
      </article>
      <article>
        <span>Month working hours</span>
        <strong>{asHours(monthMinutes)}</strong>
      </article>
      <article>
        <span>History total working hours</span>
        <strong>{asHours(historyMinutes)}</strong>
      </article>
    </div>
  );
}

export function monthDateKeys(count = 30) {
  const dates: string[] = [];
  const now = new Date();
  for (let offset = count - 1; offset >= 0; offset -= 1) {
    const date = new Date(now.getFullYear(), now.getMonth(), now.getDate() - offset);
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    dates.push(`${date.getFullYear()}-${month}-${day}`);
  }
  return dates;
}

export interface LeaveRequest {
  id?: number;
  employee: EmployeeResponse;
  startDate: string;
  endDate: string;
  reason: string;
  leaveType?: "CASUAL" | "SICK" | "EARNED" | "COMP_OFF" | "LOP" | string;
  halfDay?: boolean;
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
  leaveType = "EARNED",
  halfDay = false,
) {
  return attendanceRequest<LeaveRequest>("/leaves", {
    method: "POST",
    body: JSON.stringify({
      employee: { id: employeeId },
      startDate,
      endDate,
      reason,
      leaveType,
      halfDay,
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
  const [selectedCompany, setSelectedCompany] = useState<string>(
    () => getWorkspaceCompany() ?? "CYOND",
  );
  const [selectedEmployee, setSelectedEmployee] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [currentTime, setCurrentTime] = useState(() => new Date());
  const [rowFilter, setRowFilter] = useState("All");
  const [attendanceLoaded, setAttendanceLoaded] = useState(false);
  const [selfie, setSelfie] = useState<File | null>(null);
  const [history, setHistory] = useState<AttendanceRecord[]>(() => {
    const role = (localStorage.getItem("nexushr_role") ?? "").trim().toUpperCase();
    if (["ADMIN", "HR", "MANAGER", "SUPER_ADMIN"].includes(role)) {
      return [];
    }
    return readMyAttendanceCache();
  });
  const [audit, setAudit] = useState<AttendanceAuditEntry[]>([]);
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
  const isManagementRole = ["ADMIN", "HR", "MANAGER", "SUPER_ADMIN"].includes(currentRole);
  const canPunch = currentRole === "EMPLOYEE";
  const siteVisitor = Boolean(currentEmployee?.siteVisitor);
  const officePunchAllowed = isWindowsComputer() || siteVisitor;
  const historyEmployeeId = isManagementRole
    ? selectedEmployee
    : (currentEmployee ? String(currentEmployee.id) : "");

  useEffect(() => {
    if (!isManagementRole) {
      return;
    }
    if (!historyEmployeeId) {
      setHistory([]);
      setAudit([]);
      return;
    }
    const id = Number(historyEmployeeId);
    void getEmployeeAttendance(id).then(setHistory).catch(() => setHistory([]));
    void getAttendanceAudit(id).then(setAudit).catch(() => setAudit([]));
  }, [historyEmployeeId, message, isManagementRole]);

  const historyPerson = employees.find(
    (employee) => String(employee.id) === String(historyEmployeeId),
  );

  const companyEmployees = employees.filter(
    (employee) =>
      employee.active === true &&
      (selectedCompany === "All Companies" ||
        isCompanyMatch(employee.companyCode, selectedCompany)),
  );

  const companyRecords = records.filter(
    (record) =>
      selectedCompany === "All Companies" ||
      isCompanyMatch(record.employee.companyCode, selectedCompany),
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
  const sessionOpen = Boolean(
    actionEmployeeRecord?.checkInTime && !actionEmployeeRecord?.checkOutTime,
  );
  const canResume = Boolean(actionEmployeeRecord?.checkOutTime);



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

  const visibleRows = attendanceRows.filter(({ record }) => {
    if (rowFilter === "Present") {
      return Boolean(record?.checkInTime);
    }
    if (rowFilter === "Late") {
      return (record?.lateArrivalMinutes ?? 0) > 0;
    }
    if (rowFilter === "No check-in") {
      return !record?.checkInTime;
    }
    return true;
  });


  function calculateFallbackMetrics(
    attendanceRecords: AttendanceRecord[],
  ): AttendanceMetrics {
    return {
      date,
      totalEmployees: employees.filter((employee) => employee.active === true).length,
      present: attendanceRecords.filter((record) => Boolean(record.checkInTime)).length,
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

  async function load(options?: { keepMessage?: boolean }) {
    setLoading(true);
    setError("");
    if (!options?.keepMessage) {
      setMessage("");
    }
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
        writeDashboardSnapshot(me, employeeRecords);
        setHistory(employeeRecords);

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
          present: recordsForDate.filter((record) => Boolean(record.checkInTime)).length,
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
      const saved = await action();
      const exactTime = formatTime(
        saved.checkOutTime || saved.sessionStartedAt || saved.checkInTime,
      );
      setRecords((current) => {
        const savedDate = normalizeAttendanceDate(saved.attendanceDate);
        const savedEmployeeId = String(saved.employee?.id ?? "");
        const rest = current.filter(
          (record) =>
            String(record.employee.id) !== savedEmployeeId ||
            normalizeAttendanceDate(record.attendanceDate) !== savedDate,
        );
        return [saved, ...rest];
      });
      setMessage(`${success.replace(/\.$/, "")} at ${exactTime}.`);
      setSelfie(null);
      await load({ keepMessage: true });
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

  const visibleMetrics: AttendanceMetrics = {
    ...calculateFallbackMetrics(selectedEmployee ? visibleRecords : companyRecords),
    totalEmployees: selectedEmployee
      ? 1
      : companyEmployees.filter((employee) => employee.active !== false).length,
  };

  function formatTime(value?: string) {
    if (!value) {
      return "-";
    }

    const match = String(value).match(/(\d{2}):(\d{2}):(\d{2})/);
    if (!match) {
      return value;
    }

    return `${match[1]}:${match[2]}:${match[3]}`;
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


  function formatAsHours(totalMinutes: number) {
    const safe = Math.max(0, Math.round(totalMinutes));
    const hours = Math.floor(safe / 60);
    const minutes = safe % 60;
    if (minutes === 0) {
      return `${hours}h`;
    }
    return `${hours}h ${minutes}m`;
  }

  function elapsedSeconds(record?: AttendanceRecord) {
    if (!record?.checkInTime) {
      return null;
    }

    const start = new Date(record.checkInTime);
    if (Number.isNaN(start.getTime())) {
      return null;
    }

    const end = record.checkOutTime
      ? new Date(record.checkOutTime)
      : currentTime;
    if (Number.isNaN(end.getTime())) {
      return null;
    }

    return Math.max(0, Math.floor((end.getTime() - start.getTime()) / 1000));
  }

  function workAndBreak(record?: AttendanceRecord) {
    if (!record?.checkInTime) {
      return { work: "-", breakTime: "-" };
    }

    const closedSeconds = Math.max(0, record.closedWorkMinutes ?? 0) * 60;
    const breakSeconds = Math.max(0, record.breakMinutes ?? 0) * 60;
    const lunchSeconds = 60 * 60;
    const open = Boolean(record.checkInTime) && !record.checkOutTime;
    const payable = (checkedInSeconds: number) => {
      const lunchStillDue = Math.max(0, lunchSeconds - breakSeconds);
      const afterEightHours = Math.max(0, checkedInSeconds + breakSeconds - 8 * 60 * 60);
      const automaticLunch = Math.min(lunchStillDue, afterEightHours);
      return Math.max(0, checkedInSeconds - automaticLunch);
    };

    if (open) {
      const start = new Date(record.checkInTime);
      const sinceCheckIn = Number.isNaN(start.getTime())
        ? 0
        : Math.max(0, Math.floor((currentTime.getTime() - start.getTime()) / 1000));
      return {
        work: formatAsHours(Math.floor(Math.max(0, sinceCheckIn - breakSeconds) / 60)),
        breakTime: formatAsHours(Math.floor(breakSeconds / 60)),
      };
    }

    if ((record.closedWorkMinutes ?? 0) > 0 || (record.breakMinutes ?? 0) > 0) {
      return {
        work: formatAsHours(Math.floor(payable(closedSeconds) / 60)),
        breakTime: formatAsHours(Math.floor(breakSeconds / 60)),
      };
    }

    const elapsed = elapsedSeconds(record);
    return {
      work: elapsed == null ? "-" : formatAsHours(Math.floor(payable(elapsed) / 60)),
      breakTime: formatAsHours(0),
    };
  }

  return (
    <>
      <div className="page-heading attendance-heading">
        <div>
          <p className="eyebrow">{isManagementRole ? "Attendance" : "My Attendance"}</p>
          <h2>{isManagementRole ? "Attendance" : "My Attendance"}</h2>
          <p>
            {canPunch
              ? "Check in when you arrive and check out when you leave. Start lunch to pause hours. End lunch to continue them. Lunch is not a warning."
              : "Company attendance. Check in and check out stay on each person's own Home."}
          </p>
        </div>

        {canPunch && (
        <div className="attendance-actions">
          {!officePunchAllowed && (
            <p>Check in and check out work on a Windows or Mac computer. A phone cannot punch. Signing out of the site does not check you out.</p>
          )}
          {siteVisitor && (
            <label className="secondary-button">
              Site selfie
              <input
                type="file"
                accept="image/*"
                capture="user"
                style={{ display: "block", marginTop: 8 }}
                onChange={(event) => setSelfie(event.target.files?.[0] ?? null)}
              />
            </label>
          )}
          <button
            className="secondary-button"
            onClick={() =>
              void runAction(
                () => siteVisitor
                  ? siteCheckIn(selfie as File)
                  : checkIn(),
                "Check-in completed successfully.",
              )
            }
            disabled={
              !officePunchAllowed ||
              loading ||
              !attendanceLoaded ||
              !isToday ||
              !actionEmployee ||
              sessionOpen ||
              (siteVisitor && !selfie)
            }
            title={
              sessionOpen
                ? "Already checked in. Check out before checking in again."
                : siteVisitor && !selfie
                  ? "Site visitors add a selfie, then check in."
                  : canResume
                    ? "Check in again. Work time resumes and the lunch gap is break time."
                    : undefined
            }
          >
            {loading
              ? "Checking..."
              : sessionOpen
                ? `Checked in ${formatTime(actionEmployeeRecord?.checkInTime)}`
                : canResume
                  ? "Check in again"
                  : "Check in"}
          </button>

          <button
            className="primary-button"
            onClick={() =>
              void runAction(
                () => siteVisitor
                  ? siteCheckOut(selfie as File)
                  : checkOut(),
                "Check-out completed successfully.",

              )
            }
            disabled={
              !officePunchAllowed ||
              loading ||
              !attendanceLoaded ||
              !isToday ||
              !actionEmployee ||
              !sessionOpen ||
              (siteVisitor && !selfie)
            }
            title={
              !officePunchAllowed
                ? "Check-out works on a Windows or Mac computer. A phone cannot punch."
                : !isToday
                ? "Check-out is available only for today."
                : !sessionOpen
                  ? "Check in before checking out."
                  : siteVisitor && !selfie
                    ? "Site visitors take a selfie, then check out."
                    : undefined
            }
          >
            {loading
              ? "Checking..."
              : sessionOpen
                ? "Check out"
                : actionEmployeeRecord?.checkOutTime
                  ? `Checked out ${formatTime(actionEmployeeRecord.checkOutTime)}`
                  : "Check out"}
          </button>

          <button
            className="secondary-button"
            onClick={() =>
              void runAction(
                () => siteVisitor && canPunch
                  ? siteCheckOut(selfie as File)
                  : checkOut(),
                "Lunch started. Working hours are paused.",
              )
            }
            disabled={
              !officePunchAllowed ||
              loading ||
              !attendanceLoaded ||
              !isToday ||
              !actionEmployee ||
              !sessionOpen ||
              (siteVisitor && canPunch && !selfie)
            }
            title={
              !officePunchAllowed
                ? "Lunch works on a Windows or Mac computer. A phone cannot punch."
                : !actionEmployee
                ? "Select an employee, then check out for lunch."
                : !sessionOpen
                  ? "Check in first. Lunch check-out pauses working hours."
                  : "Check out for lunch. The system is inactive while you are away."
            }
          >
            Start lunch
          </button>

          <button
            className="secondary-button"
            onClick={() =>
              void runAction(
                () => siteVisitor && canPunch
                  ? siteCheckIn(selfie as File)
                  : checkIn(),
                "Lunch ended. Working hours continue.",
              )
            }
            disabled={
              !officePunchAllowed ||
              loading ||
              !attendanceLoaded ||
              !isToday ||
              !actionEmployee ||
              sessionOpen ||
              !canResume ||
              (siteVisitor && canPunch && !selfie)
            }
            title={
              !actionEmployee
                ? "Select an employee, then check in after lunch."
                : sessionOpen
                  ? "Check out for lunch before checking in again."
                  : !canResume
                    ? "Check in for the day before the lunch check-in."
                    : "Check in after lunch. Working hours continue."
            }
          >
            End lunch
          </button>
        </div>
        )}
      </div>

      <section className="panel attendance-toolbar-panel">
        <div className="attendance-toolbar-header">
          <div>
            <p className="eyebrow">Today</p>
            <h3>Find a day</h3>
            <p>Choose a date and employee.</p>
          </div>

          <button
            className="secondary-button attendance-refresh"
            onClick={() => void load()}
            disabled={loading}
          >
            {loading ? "Loading..." : "Refresh"}
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

          <label>
            <span>Status</span>
            <select value={rowFilter} onChange={(event) => setRowFilter(event.target.value)}>
              <option>All</option>
              <option>Present</option>
              <option>Late</option>
              <option>No check-in</option>
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

      {canPunch && (
        <div className="ux-clock">
          <div>
            <span>{formatDate(date)} · General shift 9:30–6:30 · {sessionOpen ? "Checked in" : "Not checked in"}</span>
            <strong>
              {String(Math.floor((actionEmployeeRecord ? clockedWorkMinutes(actionEmployeeRecord, currentTime) : 0) / 60)).padStart(2, "0")}
              :
              {String((actionEmployeeRecord ? clockedWorkMinutes(actionEmployeeRecord, currentTime) : 0) % 60).padStart(2, "0")}
            </strong>
            <em>{sessionOpen ? "Hours since check in, including the lunch hour until you start lunch." : "Check in to start the day."}</em>
          </div>
        </div>
      )}

      {metrics && (
        <div className="ux-balances">
          <article>
            <span>Present</span>
            <strong>{visibleMetrics.present.toLocaleString()}</strong>
          </article>
          <article>
            <span>Absent</span>
            <strong>{Math.max(0, visibleMetrics.totalEmployees - visibleMetrics.present).toLocaleString()}</strong>
          </article>
          <article>
            <span>Late</span>
            <strong>{visibleMetrics.late.toLocaleString()}</strong>
          </article>
          <article>
            <span>Missing out</span>
            <strong>{Math.max(0, visibleMetrics.totalEmployees - visibleMetrics.present).toLocaleString()}</strong>
          </article>
          <article>
            <span>On site</span>
            <strong>{companyRecords.filter((row) => row.checkInTime && row.employee?.siteVisitor).length}</strong>
          </article>
          <article>
            <span>Checked out</span>
            <strong>{visibleMetrics.checkedOut.toLocaleString()}</strong>
          </article>
        </div>
      )}

      <section className="panel attendance-records-panel">
        <div className="panel-header attendance-records-header">
          <div>
            <p className="eyebrow">Today</p>
            <h3>Who is in today</h3>
            <p>
              {loading
                ? "Loading attendance activity..."
                : `${attendanceRows.length} employee${
                    attendanceRows.length === 1 ? "" : "s"
                  } for ${formatDate(date)}`}
            </p>
          </div>

          <button
            type="button"
            className="panel-link"
            onClick={() => {
              const lines = ["Employee,Code,Date,Check in,Check out,Work,Late"];
              for (const { employee, record } of visibleRows) {
                const cells = [
                  employee.fullName,
                  employee.employeeCode,
                  date,
                  record?.checkInTime ?? "",
                  record?.checkOutTime ?? "",
                  record ? workAndBreak(record).work : "",
                  record?.checkInTime
                    ? (record.lateArrivalMinutes > 0 ? formatAsHours(record.lateArrivalMinutes) : "On time")
                    : "No check-in",
                ].map((value) => `"${String(value).replaceAll("\"", "\"\"")}"`);
                lines.push(cells.join(","));
              }
              const url = URL.createObjectURL(new Blob([lines.join("\n")], { type: "text/csv" }));
              const link = document.createElement("a");
              link.href = url;
              link.download = `attendance-${date}.csv`;
              link.click();
              URL.revokeObjectURL(url);
            }}
          >
            Export
          </button>
          {selectedEmployee && (
            <button
              className="panel-link"
              onClick={() => setSelectedEmployee("")}
            >
              View all
            </button>
          )}
        </div>

        <div className="employees-table-wrap attendance-table-wrap">
          <table className="employees-table attendance-table">
            <thead>
              <tr>
                <th>Employee</th>
                <th>Date</th>
                <th>Check in</th>
                <th>Check out</th>
                <th>Work Hours</th>
                <th>Break</th>
                <th>Late</th>
                <th>Overtime</th>
              </tr>
            </thead>

            <tbody>
  {visibleRows.map(({ employee, record }) => (
    <tr key={employee.id}>
      <td>
        <div className="attendance-employee-cell">
          {isManagementRole ? (
            <button
              type="button"
              className="panel-link"
              onClick={() => setSelectedEmployee(String(employee.id))}
            >
              <strong>{employee.fullName}</strong>
            </button>
          ) : (
            <strong>{employee.fullName}</strong>
          )}
          <span>{employee.employeeCode}</span>
          {record?.lateWarning && <span className="attendance-inactive">Late</span>}
          {record?.yellowFlag && <span className="attendance-inactive">Yellow</span>}
          {record?.redFlag && <span className="attendance-inactive">Red</span>}
          {record?.blueFlag && <span className="attendance-inactive">Blue</span>}
          {record?.selfieCaptured && <span className="attendance-inactive">Selfie</span>}
        </div>
      </td>

      <td>{formatDate(date)}</td>

      <td>
        {record?.checkInTime ? (
          <span className="attendance-time">{formatTime(record.checkInTime)}</span>
        ) : (
          "—"
        )}
      </td>

      <td>
        {record?.checkOutTime ? (
          <span className="attendance-time">{formatTime(record.checkOutTime)}</span>
        ) : record?.checkInTime ? (
          <span className="attendance-time">Still in</span>
        ) : (
          "—"
        )}
      </td>

      <td>
        {record?.checkInTime ? (
          <span className="attendance-work-duration">{workAndBreak(record).work}</span>
        ) : (
          "—"
        )}
      </td>

      <td>
        {record ? workAndBreak(record).breakTime : "-"}
      </td>

      <td>
        {record?.checkInTime ? (
          <span
            className={
              record.lateArrivalMinutes > 0
                ? "attendance-value-warning"
                : "attendance-value-normal"
            }
          >
            {record.lateArrivalMinutes > 0
              ? formatAsHours(record.lateArrivalMinutes)
              : "On time"}
          </span>
        ) : (
          <span className="attendance-value-normal">No check-in</span>
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
              ? formatAsHours(record.overtimeMinutes)
              : "-"}
          </span>
        ) : (
          <span className="attendance-value-normal">-</span>
        )}
      </td>
    </tr>
  ))}

  {!loading && visibleRows.length === 0 && (
    <tr>
      <td colSpan={8}>
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

      <section className="panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">Last 30 days</p>
            <h3>{historyPerson ? `${historyPerson.fullName} · last 30 days` : "Last 30 days"}</h3>
            {historyEmployeeId ? (
              <HoursTotals records={history} now={currentTime} />
            ) : (
              <p>Choose one employee. Each person's today, month, and history stay separate.</p>
            )}
          </div>
        </div>
        <div className="employees-table-wrap month-history">
          <table className="employees-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Check in</th>
                <th>Check out</th>
                <th>Working hours</th>
                <th>Flags</th>
                <th>Deduction</th>
              </tr>
            </thead>
            <tbody>
              {historyEmployeeId ? monthDateKeys().slice().reverse().map((date) => {
                const row = history.find((item) => item.attendanceDate?.slice(0, 10) === date);
                const checkOut = shownCheckOut(row, currentTime);
                return (
                <tr key={date}>
                  <td>{formatDate(date)}</td>
                  <td>{row ? formatTime(row.checkInTime) : "—"}</td>
                  <td>{checkOut ? formatTime(checkOut) : row?.checkInTime ? "Still in" : "—"}</td>
                  <td>{row?.checkInTime ? `${formatAsHours(clockedWorkMinutes(row, currentTime))} · ${dayIsComplete(row, currentTime) ? "Complete" : "Still in"}` : "No check-in"}</td>
                  <td>
                    {[
                      row?.lateWarning ? "Late" : "",
                      row?.yellowFlag ? "Yellow" : "",
                      row?.redFlag ? "Red" : "",
                      row?.blueFlag ? "Blue" : "",
                    ].filter(Boolean).join(", ") || "-"}
                  </td>
                  <td>{row?.deductedMinutes ? formatAsHours(row.deductedMinutes) : "-"}</td>
                </tr>
                );
              }) : (
                <tr>
                  <td colSpan={6}>Select an employee to see the last 30 days.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {isManagementRole && (
        <section className="panel">
          <div className="panel-header">
            <div>
              <p className="eyebrow">Audit</p>
              <h3>Who changed attendance</h3>
              <p>Check-in, check-out, and flags are recorded with the person who made the change.</p>
            </div>
          </div>
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
                {audit.map((entry) => (
                  <tr key={entry.id}>
                    <td>{formatTime(entry.createdAt)}</td>
                    <td>{entry.actorName}</td>
                    <td>{entry.action}</td>
                    <td>{entry.detail}</td>
                  </tr>
                ))}
                {audit.length === 0 && (
                  <tr>
                    <td colSpan={4}>No attendance changes recorded yet.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </>
  );
}







