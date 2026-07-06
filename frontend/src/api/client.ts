// ── Domain types ────────────────────────────────────────────────────────────

export type DashboardSummary = {
  totalEmployees: number;
  attendanceEvents: number;
  leaveRequests: number;
  payrollRecords: number;
};

export type Employee = {
  id: number;
  employeeCode: string;
  fullName: string;
  email: string;
  roleType: string;
  department: string;
  designation: string;
  joiningDate: string;
  baseSalary: number;
  active: boolean;
};

export type EmployeeInput = Omit<Employee, "id">;

export type LeaveRequest = {
  id: number;
  employee: { id: number; fullName?: string };
  startDate: string;
  endDate: string;
  reason: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
};

export type LeaveInput = {
  employee: { id: number };
  startDate: string;
  endDate: string;
  reason: string;
  status: string;
};

export type AttendanceRecord = {
  id: number;
  employee: { id: number; fullName?: string };
  attendanceDate: string;
  checkInTime: string | null;
  checkOutTime: string | null;
  workMinutes: number;
};

export type AttendanceInput = {
  employee: { id: number };
  attendanceDate: string;
  checkInTime: string | null;
  checkOutTime: string | null;
  workMinutes: number;
};

export type PayrollRecord = {
  id: number;
  employee: { id: number; fullName?: string };
  payMonth: string;
  grossSalary: number;
  deductions: number;
  netSalary: number;
};

export type PayrollInput = {
  employee: { id: number };
  payMonth: string;
  grossSalary: number;
  deductions: number;
  netSalary: number;
};

export type PerformanceReview = {
  id: number;
  employee: { id: number; fullName?: string };
  reviewYear: number;
  score: number;
  feedback: string;
  reviewDate: string;
};

export type PerformanceInput = {
  employee: { id: number };
  reviewYear: number;
  score: number;
  feedback: string;
  reviewDate: string;
};

export type AttritionRisk = {
  employeeId: number;
  attritionRisk: number;
  riskBand: "LOW" | "MEDIUM" | "HIGH";
  recommendation: string;
};

export type AuthResult = {
  accessToken: string;
  role: string;
  username: string;
};

// ── Token helpers ────────────────────────────────────────────────────────────

const tokenKey = "nexushr_token";
const roleKey  = "nexushr_role";
const userKey  = "nexushr_user";

export const saveToken = (token: string): void => localStorage.setItem(tokenKey, token);
export const getToken  = (): string | null => localStorage.getItem(tokenKey);
export const getRole   = (): string | null => localStorage.getItem(roleKey);
export const getUser   = (): string | null => localStorage.getItem(userKey);
export const clearAuth = (): void => {
  localStorage.removeItem(tokenKey);
  localStorage.removeItem(roleKey);
  localStorage.removeItem(userKey);
};

const authHeaders = (): HeadersInit => {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }
               : { "Content-Type": "application/json" };
};

// ── Generic fetch helper ─────────────────────────────────────────────────────

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const baseUrl = (import.meta as any).env?.VITE_API_BASE as string | undefined;
  const url =
    baseUrl && !path.startsWith("/api/")
      ? new URL(path, baseUrl).toString()
      : path;
  const response = await fetch(url, { ...init, headers: { ...authHeaders(), ...init?.headers } });
  if (!response.ok) {
    const text = await response.text().catch(() => response.statusText);
    throw new Error(text || `HTTP ${response.status}`);
  }
  // 204 No Content
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

// ── Auth ─────────────────────────────────────────────────────────────────────

export const login = async (username: string, password: string): Promise<AuthResult> => {
  const data = await apiFetch<AuthResult>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password })
  });
  saveToken(data.accessToken);
  localStorage.setItem(roleKey, data.role);
  localStorage.setItem(userKey, data.username ?? username);
  return data;
};

// ── Dashboard ────────────────────────────────────────────────────────────────

export const getDashboardSummary = (): Promise<DashboardSummary> =>
  apiFetch("/api/v1/dashboard/summary");

// ── Employees ────────────────────────────────────────────────────────────────

export const getEmployees     = (): Promise<Employee[]>             => apiFetch("/api/v1/employees");
export const createEmployee   = (e: EmployeeInput): Promise<Employee> =>
  apiFetch("/api/v1/employees", { method: "POST", body: JSON.stringify(e) });

// ── Leave ────────────────────────────────────────────────────────────────────

export const getLeaveRequests  = (): Promise<LeaveRequest[]>       => apiFetch("/api/v1/leaves");
export const createLeave       = (l: LeaveInput): Promise<LeaveRequest> =>
  apiFetch("/api/v1/leaves", { method: "POST", body: JSON.stringify(l) });
export const updateLeaveStatus = (id: number, status: string): Promise<LeaveRequest> =>
  apiFetch(`/api/v1/leaves/${id}/status?status=${status}`, { method: "PATCH" });

// ── Attendance ───────────────────────────────────────────────────────────────

export const getAttendanceByDate = (date: string): Promise<AttendanceRecord[]> =>
  apiFetch(`/api/v1/attendance?date=${date}`);
export const createAttendance    = (a: AttendanceInput): Promise<AttendanceRecord> =>
  apiFetch("/api/v1/attendance", { method: "POST", body: JSON.stringify(a) });

// ── Payroll ──────────────────────────────────────────────────────────────────

export const getPayroll    = (): Promise<PayrollRecord[]>         => apiFetch("/api/v1/payroll");
export const createPayroll = (p: PayrollInput): Promise<PayrollRecord> =>
  apiFetch("/api/v1/payroll", { method: "POST", body: JSON.stringify(p) });

// ── Performance ──────────────────────────────────────────────────────────────

export const getPerformanceReviews = (): Promise<PerformanceReview[]> => apiFetch("/api/v1/performance");
export const createPerformanceReview = (p: PerformanceInput): Promise<PerformanceReview> =>
  apiFetch("/api/v1/performance", { method: "POST", body: JSON.stringify(p) });

// ── AI ───────────────────────────────────────────────────────────────────────

export const getAttritionRisk = (employeeId: number): Promise<AttritionRisk> =>
  apiFetch(`/api/v1/ai/attrition/${employeeId}`);
