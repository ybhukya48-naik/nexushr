import { useEffect, useMemo, useState } from "react";
import {
  createPayroll,
  getAttendanceMonthlySummary,
  getEmployees,
  getCurrentEmployee,
  getEmployeePayroll,
  getPayrollAutoComponents,
  getPayrollRecords,
  importAttendanceExcel,
  getPayslip,
  downloadPayslipPdf,
  emailPayslip,
  markPayrollPaid,
  type AttendanceImportResponse,
  type AttendanceMonthlySummaryResponse,
  type EmployeeResponse,
  type PayrollAutoComponentsResponse,
  type PayrollRecord,
  type PayslipResponse,
} from "../api";

function money(value: number) {
  return `${String.fromCharCode(0x20B9)}${Number(value || 0).toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

function currentMonth() {
  return new Date().toISOString().slice(0, 7);
}

function toOptionalNumber(value: string): number | undefined {
  const trimmed = value.trim();

  if (!trimmed) {
    return undefined;
  }

  return Number(trimmed);
}

export default function Payroll() {
  const role = (localStorage.getItem("nexushr_role") || "EMPLOYEE").toUpperCase();
  const isManagementRole = ["ADMIN", "HR", "MANAGER"].includes(role);

  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [records, setRecords] = useState<PayrollRecord[]>([]);
  const [selectedEmployee, setSelectedEmployee] = useState("");
  const [payMonth, setPayMonth] = useState(currentMonth());

  const [hra, setHra] = useState("");
  const [bonus, setBonus] = useState("");
  const [overtime, setOvertime] = useState("");
  const [pf, setPf] = useState("");
  const [leaveDeduction, setLeaveDeduction] = useState("");
  const [otherDeductions, setOtherDeductions] = useState("");
  const [autoFromAttendance, setAutoFromAttendance] = useState(true);

  const [attendanceFile, setAttendanceFile] = useState<File | null>(null);
  const [importingAttendance, setImportingAttendance] = useState(false);
  const [attendanceImportResult, setAttendanceImportResult] =
    useState<AttendanceImportResponse | null>(null);
  const [attendanceSummary, setAttendanceSummary] =
    useState<AttendanceMonthlySummaryResponse | null>(null);

  const [autoComponents, setAutoComponents] =
    useState<PayrollAutoComponentsResponse | null>(null);
  const [autoComponentsLoading, setAutoComponentsLoading] = useState(false);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const [payslip, setPayslip] = useState<PayslipResponse | null>(null);
  const [payslipLoading, setPayslipLoading] = useState(false);
  const [emailingPayslip, setEmailingPayslip] = useState(false);

  const payrollEmployees = useMemo(
    () => employees.filter((employee) => employee.active === true),
    [employees],
  );

  const selectedEmployeeData = useMemo(
    () =>
      payrollEmployees.find(
        (employee) => String(employee.id) === selectedEmployee,
      ) ?? null,
    [payrollEmployees, selectedEmployee],
  );

  async function loadData() {
    setLoading(true);
    setError("");

    try {
      const isManagementRole = ["ADMIN", "HR", "MANAGER"].includes(role);

      if (!isManagementRole) {
        const me = await getCurrentEmployee();

        setEmployees([me]);
        setSelectedEmployee(String(me.id));

        const payrollData = await getEmployeePayroll(me.id);

        setRecords(payrollData);
        return;
      }

      const [employeeData, payrollData] = await Promise.all([
        getEmployees(),
        getPayrollRecords(),
      ]);

      setEmployees(employeeData);
      setRecords(payrollData);
    } catch (err) {
      console.error("Payroll load failed:", err);

      setError(
        err instanceof Error
          ? err.message
          : "Unable to load payroll data.",
      );
    } finally {
      setLoading(false);
    }
  }

  async function loadAttendanceSummary(targetMonth: string) {
    if (!isManagementRole) {
      return;
    }

    try {
      const summary = await getAttendanceMonthlySummary(targetMonth);
      setAttendanceSummary(summary);
    } catch (err) {
      console.error("Attendance summary load failed:", err);
    }
  }

  async function loadAutoComponents(
    employeeId: string,
    targetMonth: string,
  ) {
    if (!isManagementRole || !employeeId) {
      setAutoComponents(null);
      return;
    }

    setAutoComponentsLoading(true);

    try {
      const components = await getPayrollAutoComponents(
        Number(employeeId),
        targetMonth,
      );

      setAutoComponents(components);

      if (autoFromAttendance) {
        setOvertime(String(components.overtimeAmount));
        setLeaveDeduction(String(components.leaveDeduction));
      }
    } catch (err) {
      console.error("Auto payroll component load failed:", err);
      setAutoComponents(null);
    } finally {
      setAutoComponentsLoading(false);
    }
  }

  async function handleAttendanceImport(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!attendanceFile) {
      setError("Please choose an attendance Excel file to import.");
      return;
    }

    setImportingAttendance(true);
    setError("");
    setMessage("");

    try {
      const result = await importAttendanceExcel(payMonth, attendanceFile);

      setAttendanceImportResult(result);
      setMessage("Attendance imported successfully.");

      await loadAttendanceSummary(payMonth);

      if (selectedEmployee) {
        await loadAutoComponents(selectedEmployee, payMonth);
      }
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to import attendance Excel.",
      );
    } finally {
      setImportingAttendance(false);
    }
  }

  useEffect(() => {
    void loadData();
  }, []);

  useEffect(() => {
    if (!isManagementRole) {
      return;
    }

    void loadAttendanceSummary(payMonth);
  }, [payMonth, isManagementRole]);

  useEffect(() => {
    if (!isManagementRole || !selectedEmployee) {
      return;
    }

    void loadAutoComponents(selectedEmployee, payMonth);
  }, [selectedEmployee, payMonth, isManagementRole, autoFromAttendance]);

  async function handleGenerate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!isManagementRole) {
      setError("Only HR, Admin or Manager users can generate payroll.");
      return;
    }

    if (!selectedEmployee) {
      setError("Please select an employee.");
      return;
    }

    setSaving(true);
    setError("");
    setMessage("");

    try {
      await createPayroll({
        employeeId: Number(selectedEmployee),
        payMonth,
        autoFromAttendance,
        hra: toOptionalNumber(hra),
        bonus: toOptionalNumber(bonus),
        overtime: toOptionalNumber(overtime),
        pf: toOptionalNumber(pf),
        leaveDeduction: toOptionalNumber(leaveDeduction),
        otherDeductions: toOptionalNumber(otherDeductions),
      });

      setMessage("Payroll generated successfully.");

      setHra("");
      setBonus("");
      setOvertime("");
      setPf("");
      setLeaveDeduction("");
      setOtherDeductions("");

      await loadData();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to generate payroll.",
      );
    } finally {
      setSaving(false);
    }
  }

  async function handleMarkPaid(id: number) {
    if (!isManagementRole) {
      setError("Only HR, Admin or Manager users can mark payroll as paid.");
      return;
    }

    setError("");
    setMessage("");

    try {
      await markPayrollPaid(id);
      setMessage("Payroll marked as paid.");
      await loadData();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to mark payroll as paid.",
      );
    }
  }

  async function handlePayslip(id: number) {
    setPayslipLoading(true);
    setError("");

    try {
      const data = await getPayslip(id);
      setPayslip(data);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to load payslip.",
      );
    } finally {
      setPayslipLoading(false);
    }
  }

  async function handleDownloadPdf() {
    if (!payslip) {
      return;
    }

    setError("");

    try {
      const pdf = await downloadPayslipPdf(payslip.payrollId);

      const url = window.URL.createObjectURL(pdf);
      const link = document.createElement("a");

      link.href = url;
      link.download =
        `CYOND-Payslip-${payslip.employeeCode}-${payslip.payMonth}.pdf`;

      document.body.appendChild(link);
      link.click();
      link.remove();

      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to download payslip PDF.",
      );
    }
  }
  async function handleEmailPayslip() {
    if (!payslip) {
      return;
    }

    setEmailingPayslip(true);
    setError("");

    try {
      const result = await emailPayslip(payslip.payrollId);

      setMessage(result);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to email payslip.",
      );
    } finally {
      setEmailingPayslip(false);
    }
  }


  return (
    <section className="employees-page">
      <div className="employees-header">
        <div>
          <p className="page-eyebrow">F04 - Payroll</p>
          <h1>Payroll Management</h1>
          <p>
            Generate payroll, track salary payments and view employee
            payslips.
          </p>
        </div>
      </div>

      {message && (
        <div role="status" className="payroll-message">
          {message}
        </div>
      )}

      {error && (
        <div role="alert" className="payroll-error">
          {error}
        </div>
      )}

      {isManagementRole && (
        <div className="employees-panel">
          <div className="panel-header">
            <div>
              <p className="page-eyebrow">Generate Payroll</p>
            <h2>Create Salary Record</h2>
            <p>
              Payroll is generated using the employee base salary and
              optional salary components.
            </p>
            <button
              type="button"
              className="secondary-button"
              onClick={() => void loadData()}
              disabled={loading}
            >
              {loading ? "Refreshing..." : "Refresh Employees"}
            </button>
          </div>
        </div>

          <form
            className="payroll-attendance-import"
            onSubmit={handleAttendanceImport}
          >
            <label>
              Attendance Excel (month-wise)
              <input
                type="file"
                accept=".xlsx,.xls"
                onChange={(event) =>
                  setAttendanceFile(event.target.files?.[0] ?? null)
                }
              />
            </label>

            <button
              type="submit"
              className="secondary-button"
              disabled={importingAttendance}
            >
              {importingAttendance ? "Importing..." : "Import Attendance"}
            </button>
          </form>

          {attendanceImportResult && (
            <div className="payroll-attendance-summary">
              <strong>
                Import result ({attendanceImportResult.payMonth})
              </strong>

              <span>
                Imported {attendanceImportResult.importedCount}, updated {attendanceImportResult.updatedCount}, skipped {attendanceImportResult.skippedCount}.
              </span>

              {attendanceImportResult.errors.length > 0 && (
                <span>
                  {attendanceImportResult.errors.length} rows had validation issues.
                </span>
              )}
            </div>
          )}

          {attendanceSummary && (
            <div className="payroll-attendance-summary">
              <strong>
                Attendance month summary ({attendanceSummary.payMonth})
              </strong>

              <span>
                {attendanceSummary.totalEmployees} employees, {(
                  attendanceSummary.totalWorkedMinutes / 60
                ).toFixed(1)} worked hours, {(
                  attendanceSummary.totalShortfallMinutes / 60
                ).toFixed(1)} shortfall hours.
              </span>
            </div>
          )}

        <form onSubmit={handleGenerate}>
          <div className="employee-filters">
            <label>
              Employee
              <select
                required
                value={selectedEmployee}
                onChange={(event) =>
                  setSelectedEmployee(event.target.value)
                }
              >
                <option value="">Choose an employee</option>

                {payrollEmployees.map((employee) => (
                  <option key={employee.id} value={employee.id}>
                    {employee.employeeCode} - {employee.fullName}
                  </option>
                ))}
              </select>
            </label>

            {selectedEmployeeData && (
              <div className="payroll-employee-summary">
                <div>
                  <span>Department</span>
                  <strong>{selectedEmployeeData.department}</strong>
                </div>
                <div>
                  <span>Designation</span>
                  <strong>{selectedEmployeeData.designation}</strong>
                </div>
                <div>
                  <span>Base Salary</span>
                  <strong>{money(selectedEmployeeData.baseSalary)}</strong>
                </div>
                <div>
                  <span>Status</span>
                  <strong>
                    {(selectedEmployeeData.lifecycleStatus ?? "UNKNOWN").replaceAll(
                      "_",
                      " ",
                    )}
                  </strong>
                </div>
              </div>
            )}

            <label>
              Pay Month
              <input
                required
                type="month"
                value={payMonth}
                onChange={(event) => setPayMonth(event.target.value)}
              />
            </label>

            <label className="payroll-auto-toggle">
              Auto from Attendance
              <div className="payroll-auto-toggle-row">
                <input
                  type="checkbox"
                  checked={autoFromAttendance}
                  onChange={(event) =>
                    setAutoFromAttendance(event.target.checked)
                  }
                />
                <span>
                  Use imported attendance to auto-calculate overtime and leave deduction
                </span>
              </div>
            </label>

            {selectedEmployee && (
              <button
                type="button"
                className="secondary-button"
                onClick={() =>
                  void loadAutoComponents(selectedEmployee, payMonth)
                }
                disabled={autoComponentsLoading}
              >
                {autoComponentsLoading
                  ? "Calculating..."
                  : "Refresh Attendance Calculation"}
              </button>
            )}

            {autoComponents && (
              <div className="payroll-attendance-summary">
                <strong>
                  Attendance calculation for {autoComponents.employeeCode}
                </strong>
                <span>
                  Worked {(autoComponents.workedMinutes / 60).toFixed(1)}h of {(autoComponents.expectedWorkMinutes / 60).toFixed(1)}h, shortfall {(autoComponents.shortfallMinutes / 60).toFixed(1)}h.
                </span>
                <span>
                  Auto leave deduction {money(autoComponents.leaveDeduction)} | Auto overtime {money(autoComponents.overtimeAmount)}
                </span>
              </div>
            )}

            <label>
              HRA
              <input
                min="0"
                type="number"
                step="0.01"
                value={hra}
                onChange={(event) => setHra(event.target.value)}
                placeholder="0"
              />
            </label>

            <label>
              Bonus
              <input
                min="0"
                type="number"
                step="0.01"
                value={bonus}
                onChange={(event) => setBonus(event.target.value)}
                placeholder="0"
              />
            </label>

            <label>
              Overtime
              <input
                min="0"
                type="number"
                step="0.01"
                value={overtime}
                onChange={(event) => setOvertime(event.target.value)}
                placeholder="0"
              />
            </label>

            <label>
              PF
              <input
                min="0"
                type="number"
                step="0.01"
                value={pf}
                onChange={(event) => setPf(event.target.value)}
                placeholder="0"
              />
            </label>

            <label>
              Leave Deduction
              <input
                min="0"
                type="number"
                step="0.01"
                value={leaveDeduction}
                onChange={(event) =>
                  setLeaveDeduction(event.target.value)
                }
                placeholder="0"
              />
            </label>

            <label>
              Other Deductions
              <input
                min="0"
                type="number"
                step="0.01"
                value={otherDeductions}
                onChange={(event) =>
                  setOtherDeductions(event.target.value)
                }
                placeholder="0"
              />
            </label>
          </div>

          <button
            type="submit"
            className="primary-action"
            disabled={saving || payrollEmployees.length === 0}
          >
            {saving ? "Generating..." : "Generate Payroll"}
          </button>
          </form>
        </div>
      )}

      <div className="employees-panel">
        <div className="panel-header">
          <div>
            <p className="page-eyebrow">Payroll Records</p>
            <h2>Salary Records</h2>
            <p>
              Generated payroll records and payment status.
            </p>
          </div>
        </div>

        {loading ? (
          <div className="employee-empty-state">
            <strong>Loading payroll...</strong>
            <span>Fetching salary records from the backend.</span>
          </div>
        ) : records.length === 0 ? (
          <div className="employee-empty-state">
            <strong>No payroll records</strong>
            <span>Generate the first payroll record above.</span>
          </div>
        ) : (
          <div className="table-wrapper">
            <table className="employees-table">
              <thead>
                <tr>
                  <th>Employee</th>
                  <th>Month</th>
                  <th>Gross Salary</th>
                  <th>Deductions</th>
                  <th>Net Salary</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>

              <tbody>
                {records.map((record) => (
                  <tr key={record.id}>
                    <td>
                      <strong>{record.employee.fullName}</strong>
                      <span>
                        {record.employee.employeeCode}
                      </span>
                    </td>

                    <td>{record.payMonth}</td>

                    <td>{money(record.grossSalary)}</td>

                    <td>{money(record.deductions)}</td>

                    <td>
                      <strong>{money(record.netSalary)}</strong>
                    </td>

                    <td>
                      <span
                        className={
                          record.status === "PAID"
                            ? "employee-status employee-status-active"
                            : "employee-status employee-status-probation"
                        }
                      >
                        {record.status}
                      </span>
                    </td>

                    <td>
                      <button
                        type="button"
                        className="table-action"
                        onClick={() =>
                          void handlePayslip(record.id)
                        }
                        disabled={payslipLoading}
                      >
                        Payslip
                      </button>

                      {isManagementRole && record.status !== "PAID" && (
                        <button
                          type="button"
                          className="primary-action"
                          onClick={() =>
                            void handleMarkPaid(record.id)
                          }
                        >
                          Mark Paid
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {payslip && (
        <div className="employees-panel">
          <div className="panel-header">
            <div>
              <p className="page-eyebrow">Payslip</p>
              <h2>
                {payslip.employeeName} - {payslip.payMonth}
              </h2>
              <p>
                {payslip.employeeCode} - Status: {payslip.status}
              </p>
            </div>

            <div>
              <button
                type="button"
                className="primary-action"
                onClick={() => void handleDownloadPdf()}
              >
                Download PDF
              </button>

              <button
                type="button"
                className="table-action"
                onClick={() => void handleEmailPayslip()}
                disabled={emailingPayslip}
              >
                {emailingPayslip ? "Emailing..." : "Email Payslip"}
              </button>

              <button
                type="button"
                className="table-action"
                onClick={() => setPayslip(null)}
              >
                Close
              </button>
            </div>
          </div>

          <div className="employee-summary-grid">
            <div className="employee-summary-card">
              <span>Basic Salary</span>
              <strong>{money(payslip.basicSalary)}</strong>
            </div>

            <div className="employee-summary-card">
              <span>HRA</span>
              <strong>{money(payslip.hra)}</strong>
            </div>

            <div className="employee-summary-card">
              <span>Bonus</span>
              <strong>{money(payslip.bonus)}</strong>
            </div>

            <div className="employee-summary-card">
              <span>Overtime</span>
              <strong>{money(payslip.overtime)}</strong>
            </div>

            <div className="employee-summary-card">
              <span>Gross Salary</span>
              <strong>{money(payslip.grossSalary)}</strong>
            </div>

            <div className="employee-summary-card">
              <span>Tax</span>
              <strong>{money(payslip.taxAmount)}</strong>
            </div>

            <div className="employee-summary-card">
              <span>PF</span>
              <strong>{money(payslip.pf)}</strong>
            </div>

            <div className="employee-summary-card">
              <span>Leave Deduction</span>
              <strong>{money(payslip.leaveDeduction)}</strong>
            </div>

            <div className="employee-summary-card">
              <span>Other Deductions</span>
              <strong>{money(payslip.otherDeductions)}</strong>
            </div>

            <div className="employee-summary-card">
              <span>Total Deductions</span>
              <strong>{money(payslip.deductions)}</strong>
            </div>

            <div className="employee-summary-card">
              <span>Net Salary</span>
              <strong>{money(payslip.netSalary)}</strong>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}











