export const API_BASE = import.meta.env.VITE_API_BASE_URL || `${window.location.protocol}//${window.location.hostname}:8080/api/v1`;

export interface LoginResponse {
  accessToken: string;
  role: string;
  username: string;
}

export interface DashboardSummary {
  totalEmployees: number;
  activeEmployees: number;
  pendingOnboarding: number;
  pendingOffboarding: number;
  attendanceEvents: number;
  leaveRequests: number;
  pendingLeaveRequests: number;
  payrollRecords: number;
  paidPayrollRecords: number;
}

export type EmployeeRole =
  | "ADMIN"
  | "HR"
  | "MANAGER"
  | "EMPLOYEE";

export type EmployeeLifecycleStatus =
  | "PENDING_ONBOARDING"
  | "PENDING_APPROVAL"
  | "ACTIVE"
  | "OFFBOARDING"
  | "OFFBOARDED";

export interface EmployeeResponse {
  id: number;
  employeeCode: string;
  fullName: string;
  email: string;
  phone?: string;
  accountNumber?: string;
  roleType: EmployeeRole;
  department: string;
  designation: string;
  joiningDate: string;
  baseSalary: number;
  active: boolean;
 gender?: "MALE" | "FEMALE" | "OTHER" | "PREFER_NOT_TO_SAY";
  lifecycleStatus: EmployeeLifecycleStatus;
}

export interface EmployeeRequest {
  employeeCode: string;
  fullName: string;
  email: string;
  phone?: string;
  accountNumber?: string;
  password: string;
  roleType: EmployeeRole;
  department: string;
  designation: string;
  joiningDate: string;
  baseSalary: number;
  active: boolean;
}

export interface EmployeeLifecycleRequest {
  changedBy?: string;
  comments?: string;
  designation?: string;
  department?: string;
  baseSalary?: number;
}

export interface EmployeeDocumentResponse {
  id: number;
  employeeId: number;
  documentName: string;
  documentType: string;
  documentUrl: string;
  uploadedBy: string;
  uploadedAt: string;
}

export interface EmployeeLifecycleHistory {
  id: number;
  employee: EmployeeResponse;
  fromStatus: EmployeeLifecycleStatus;
  toStatus: EmployeeLifecycleStatus;
  action: string;
  comments: string;
  changedBy: string;
  changedAt: string;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem("nexushr_token");

  const headers = new Headers(options.headers);

  if (!(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    if (response.status === 401) {
      localStorage.removeItem("nexushr_token");
      localStorage.removeItem("nexushr_user");
      localStorage.removeItem("nexushr_role");
    }

    let message = `API request failed: ${response.status}`;

    try {
      const errorBody = await response.json();

      if (typeof errorBody === "string" && errorBody.trim()) {
        message = errorBody;
      } else if (
        errorBody &&
        typeof errorBody === "object" &&
        typeof errorBody.message === "string" &&
        errorBody.message.trim()
      ) {
        message = errorBody.message;
      } else if (
        errorBody &&
        typeof errorBody === "object" &&
        typeof errorBody.error === "string" &&
        errorBody.error.trim()
      ) {
        message = errorBody.error;
      }
    } catch {
      // Keep the HTTP status message when the response has no JSON body.
    }

    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

export async function login(
  username: string,
  password: string,
): Promise<LoginResponse> {
  const response = await fetch(`${API_BASE}/auth/login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ username, password }),
  });

  if (!response.ok) {
    throw new Error("Invalid username or password");
  }

  return response.json();
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  phone?: string;
}

export interface RegisterResponse {
  employeeCode: string;
  email: string;
  message: string;
}

export async function register(
  requestData: RegisterRequest,
): Promise<RegisterResponse> {
  const response = await fetch(`${API_BASE}/auth/register`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(requestData),
  });

  if (!response.ok) {
    let message = `Registration failed: ${response.status}`;

    try {
      const errorBody = await response.json();

      if (
        errorBody &&
        typeof errorBody === "object" &&
        typeof errorBody.message === "string" &&
        errorBody.message.trim()
      ) {
        message = errorBody.message;
      }
    } catch {
      // Keep the HTTP status message when the response has no JSON body.
    }

    throw new Error(message);
  }

  return response.json();
}
export function getDashboardSummary() {
  return request<DashboardSummary>("/dashboard/summary");
}

export function getEmployees() {
  return request<EmployeeResponse[]>("/employees");
}

export function getCurrentEmployee() {
  return request<EmployeeResponse>("/employees/me");
}

export function getEmployee(id: number) {
  return request<EmployeeResponse>(`/employees/${id}`);
}

export function createEmployee(employee: EmployeeRequest) {
  return request<EmployeeResponse>("/employees", {
    method: "POST",
    body: JSON.stringify(employee),
  });
}

export function updateEmployee(
  id: number,
  employee: EmployeeRequest,
) {
  return request<EmployeeResponse>(`/employees/${id}`, {
    method: "PUT",
    body: JSON.stringify(employee),
  });
}

export function deleteEmployee(id: number) {
  return request<void>(`/employees/${id}`, {
    method: "DELETE",
  });
}

export function submitOnboarding(
  id: number,
  requestData: EmployeeLifecycleRequest = {},
) {
  return request<EmployeeResponse>(
    `/employees/${id}/submit-onboarding`,
    {
      method: "POST",
      body: JSON.stringify(requestData),
    },
  );
}

export function approveEmployee(
  id: number,
  requestData: EmployeeLifecycleRequest = {},
) {
  return request<EmployeeResponse>(`/employees/${id}/approve`, {
    method: "POST",
    body: JSON.stringify(requestData),
  });
}

export function transferEmployee(
  id: number,
  requestData: EmployeeLifecycleRequest,
) {
  return request<EmployeeResponse>(`/employees/${id}/transfer`, {
    method: "POST",
    body: JSON.stringify(requestData),
  });
}

export function promoteEmployee(
  id: number,
  requestData: EmployeeLifecycleRequest,
) {
  return request<EmployeeResponse>(`/employees/${id}/promote`, {
    method: "POST",
    body: JSON.stringify(requestData),
  });
}

export function startOffboarding(
  id: number,
  requestData: EmployeeLifecycleRequest = {},
) {
  return request<EmployeeResponse>(
    `/employees/${id}/start-offboarding`,
    {
      method: "POST",
      body: JSON.stringify(requestData),
    },
  );
}

export function resignEmployee(
  id: number,
  requestData: EmployeeLifecycleRequest = {},
) {
  return request<EmployeeResponse>(`/employees/${id}/resign`, {
    method: "POST",
    body: JSON.stringify(requestData),
  });
}

export function completeOffboarding(
  id: number,
  requestData: EmployeeLifecycleRequest = {},
) {
  return request<EmployeeResponse>(
    `/employees/${id}/complete-offboarding`,
    {
      method: "POST",
      body: JSON.stringify(requestData),
    },
  );
}

export function getEmployeeLifecycleHistory(id: number) {
  return request<EmployeeLifecycleHistory[]>(
    `/employees/${id}/lifecycle-history`,
  );
}

export interface NotificationSendRequest {
  employeeId: number;
  title: string;
  message: string;
  notificationType: string;
  channel: string;
  recipientEmail?: string;
  recipientPhone?: string;
  scheduledAt?: string;
}

export function sendNotification(requestData: NotificationSendRequest) {
  return request("/notifications/send", {
    method: "POST",
    body: JSON.stringify(requestData),
  });
}
export function getEmployeeDocuments(employeeId: number) {
  return request<EmployeeDocumentResponse[]>(
    `/employees/${employeeId}/documents`,
  );
}

export interface Employee {
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
}

export interface PerformanceReview {
  id?: number;
  employee?: Employee;
  score: number;
  feedback: string;
  reviewDate: string;
  reviewYear: number;
}

export interface PerformanceGoal {
  id?: number;
  employee?: Employee;
  title: string;
  description?: string;
  targetScore?: number;
  achievedScore?: number;
  completed?: boolean;
}

export interface PerformanceFeedback {
  id?: number;
  employee?: Employee;
  reviewer?: Employee;
  feedback?: string;
  comments?: string;
  rating?: number;
  createdAt?: string;
  submittedAt?: string;
}

export interface PerformanceScorecard {
  employeeId: number;
  reviewCount: number;
  averageScore: number;
  highestScore: number;
  lowestScore: number;
  latestScore: number;
  trend: string;
  reviews: PerformanceReview[];
}

export interface PerformanceDashboard {
  employeeId: number;
  scorecard: PerformanceScorecard;
  reviews: PerformanceReview[];
  goals: PerformanceGoal[];
  feedback: PerformanceFeedback[];
}

export interface PerformanceFeedbackRequest {
  employee: {
    id: number;
  };
  reviewer: {
    id: number;
  };
  rating: number;
  comments: string;
}

export async function createPerformanceFeedback(
  requestData: PerformanceFeedbackRequest,
): Promise<PerformanceFeedback> {
  return request<PerformanceFeedback>("/performance/feedback", {
    method: "POST",
    body: JSON.stringify(requestData),
  });
}

export async function getPerformanceDashboard(
  employeeId: number
): Promise<PerformanceDashboard> {
  return request<PerformanceDashboard>(
    `/performance/dashboard/${employeeId}`
  );
}

export type NotificationType =
  | "GENERAL"
  | "APPROVAL"
  | "REMINDER"
  | "ANNOUNCEMENT";

export type NotificationChannel =
  | "IN_APP"
  | "EMAIL"
  | "SMS"
  | "BOTH";

export type NotificationDeliveryStatus =
  | "PENDING"
  | "SENT"
  | "FAILED";

export interface NotificationResponse {
  id: number;
  employeeId: number;
  title: string;
  message: string;
  notificationType: NotificationType;
  channel: NotificationChannel;
  deliveryStatus: NotificationDeliveryStatus;
  recipientEmail?: string;
  recipientPhone?: string;
  read: boolean;
  sentAt?: string;
  failureReason?: string;
  scheduledAt?: string;
  createdAt?: string;
}

export function getEmployeeNotifications(employeeId: number) {
  return request<NotificationResponse[]>(
    `/notifications/employee/${employeeId}`,
  );
}

export function getUnreadEmployeeNotifications(employeeId: number) {
  return request<NotificationResponse[]>(
    `/notifications/employee/${employeeId}/unread`,
  );
}

export function markNotificationAsRead(id: number) {
  return request<NotificationResponse>(
    `/notifications/${id}/read`,
    {
      method: "PATCH",
    },
  );
}

export type PayrollStatus =
  | "GENERATED"
  | "PAID";

export interface PayrollRequest {
  employeeId: number;
  payMonth: string;
  basicSalary?: number;
  hra?: number;
  bonus?: number;
  overtime?: number;
  pf?: number;
  leaveDeduction?: number;
  otherDeductions?: number;
  autoFromAttendance?: boolean;
}

export interface AttendanceImportRowError {
  rowNumber: number;
  employeeCode: string;
  message: string;
}

export interface AttendanceImportResponse {
  payMonth: string;
  totalRows: number;
  importedCount: number;
  updatedCount: number;
  skippedCount: number;
  errors: AttendanceImportRowError[];
}

export interface AttendanceMonthlyEmployeeSummary {
  employeeId: number;
  employeeCode: string;
  fullName: string;
  department: string;
  workedMinutes: number;
  expectedMinutes: number;
  shortfallMinutes: number;
  workedHours: number;
  expectedHours: number;
  shortfallHours: number;
}

export interface AttendanceMonthlySummaryResponse {
  payMonth: string;
  standardDailyMinutes: number;
  businessDays: number;
  totalEmployees: number;
  totalWorkedMinutes: number;
  totalExpectedMinutes: number;
  totalShortfallMinutes: number;
  employees: AttendanceMonthlyEmployeeSummary[];
}

export interface PayrollAutoComponentsResponse {
  employeeId: number;
  employeeCode: string;
  payMonth: string;
  businessDays: number;
  expectedWorkMinutes: number;
  workedMinutes: number;
  shortfallMinutes: number;
  overtimeMinutes: number;
  leaveDeduction: number;
  overtimeAmount: number;
}

export interface PayrollRecord {
  id: number;
  employee: EmployeeResponse;
  payMonth: string;
  basicSalary: number;
  hra: number;
  bonus: number;
  overtime: number;
  grossSalary: number;
  taxAmount: number;
  pf: number;
  leaveDeduction: number;
  otherDeductions: number;
  deductions: number;
  netSalary: number;
  status: PayrollStatus;
}

export interface PayslipResponse {
  payrollId: number;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  payMonth: string;
  basicSalary: number;
  hra: number;
  bonus: number;
  overtime: number;
  grossSalary: number;
  taxAmount: number;
  pf: number;
  leaveDeduction: number;
  otherDeductions: number;
  deductions: number;
  netSalary: number;
  status: PayrollStatus;
}

export function getPayrollRecords() {
  return request<PayrollRecord[]>("/payroll");
}

export function getPayrollRecord(id: number) {
  return request<PayrollRecord>(`/payroll/${id}`);
}

export function getEmployeePayroll(employeeId: number) {
  return request<PayrollRecord[]>(
    `/payroll/employee/${employeeId}`,
  );
}

export function createPayroll(payroll: PayrollRequest) {
  return request<PayrollRecord>("/payroll", {
    method: "POST",
    body: JSON.stringify(payroll),
  });
}

export function getPayrollAutoComponents(
  employeeId: number,
  payMonth: string,
) {
  return request<PayrollAutoComponentsResponse>(
    `/payroll/auto-components?employeeId=${employeeId}&payMonth=${encodeURIComponent(payMonth)}`,
  );
}

export function getAttendanceMonthlySummary(payMonth: string) {
  return request<AttendanceMonthlySummaryResponse>(
    `/attendance/monthly-summary?payMonth=${encodeURIComponent(payMonth)}`,
  );
}

export function importAttendanceExcel(
  payMonth: string,
  file: File,
) {
  const body = new FormData();
  body.append("file", file);

  return request<AttendanceImportResponse>(
    `/attendance/import-excel?payMonth=${encodeURIComponent(payMonth)}`,
    {
      method: "POST",
      body,
    },
  );
}

export function markPayrollPaid(id: number) {
  return request<PayrollRecord>(
    `/payroll/${id}/mark-paid`,
    {
      method: "POST",
    },
  );
}

export function getPayslip(id: number) {
  return request<PayslipResponse>(
    `/payroll/${id}/payslip`,
  );
}

export async function downloadPayslipPdf(id: number): Promise<Blob> {
  const token = localStorage.getItem("nexushr_token");

  const headers = new Headers();

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(
    `${API_BASE}/payroll/${id}/payslip/pdf`,
    {
      method: "GET",
      headers,
    },
  );

  if (!response.ok) {
    if (response.status === 401) {
      localStorage.removeItem("nexushr_token");
      localStorage.removeItem("nexushr_user");
      localStorage.removeItem("nexushr_role");
    }

    throw new Error(
      `Unable to download payslip PDF: ${response.status}`,
    );
  }

  return response.blob();
}

export interface AiAttritionResponse {
  employeeId: number;
  attritionRisk: number;
  riskBand: string;
  recommendation: string;
}

export interface AiSkillGapResponse {
  employeeId: number;
  employeeName: string;
  department: string;
  designation: string;
  averagePerformance: number;
  goalCompletionRate: number;
  averageFeedbackRating: number;
  skillGaps: string[];
  priority: string;
}

export interface AiEngagementResponse {
  employeeId: number;
  employeeName: string;
  engagementScore: number;
  engagementLevel: string;
  attendanceScore: number;
  workTimeScore: number;
  performanceScore: number;
  feedbackScore: number;
  approvedLeaveRequests: number;
}

export interface AiRecommendationResponse {
  employeeId: number;
  employeeName: string;
  priority: string;
  attritionRisk: number;
  riskBand: string;
  engagement: AiEngagementResponse;
  skillGap: AiSkillGapResponse;
  actions: string[];
  aiRecommendation: string;
}

export interface AiEmployeeInsight {
  employeeId: number;
  employeeName: string;
  department: string;
  attritionRisk: number;
  riskBand: string;
  engagementScore: number;
}

export interface AiWorkforceDashboardResponse {
  totalEmployees: number;
  activeEmployees: number;
  inactiveEmployees: number;
  highAttritionRisk: number;
  mediumAttritionRisk: number;
  lowAttritionRisk: number;
  averageEngagementScore: number;
  departmentDistribution: Record<string, number>;
  employeeInsights: AiEmployeeInsight[];
  generatedAt: string;
}

export function getAiAttrition(employeeId: number) {
  return request<AiAttritionResponse>(
    `/ai/attrition/${employeeId}`,
  );
}

export function getAiSkillGap(employeeId: number) {
  return request<AiSkillGapResponse>(
    `/ai/skill-gap/${employeeId}`,
  );
}

export function getAiEngagement(employeeId: number) {
  return request<AiEngagementResponse>(
    `/ai/engagement/${employeeId}`,
  );
}

export function getAiRecommendations(employeeId: number) {
  return request<AiRecommendationResponse>(
    `/ai/recommendations/${employeeId}`,
  );
}
export function getAiWorkforceDashboard() {
  return request<AiWorkforceDashboardResponse>(
    "/ai/workforce-dashboard",
  );
}


export async function emailPayslip(id: number): Promise<string> {
  const token = localStorage.getItem("nexushr_token");

  const headers = new Headers();

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(
    `${API_BASE}/payroll/${id}/payslip/email`,
    {
      method: "POST",
      headers,
    },
  );

  if (!response.ok) {
    if (response.status === 401) {
      localStorage.removeItem("nexushr_token");
      localStorage.removeItem("nexushr_user");
      localStorage.removeItem("nexushr_role");
    }

    const errorText = await response.text();

    throw new Error(
      errorText || `Unable to email payslip: ${response.status}`,
    );
  }

  return response.text();
}
