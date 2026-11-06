import { isCompanyMatch } from "../utils/company";
import { getWorkspaceCompany } from "../utils/workspace";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  createPayroll,
  getEmployees,
  getCurrentEmployee,
  getEmployeePayroll,
  getPayrollAutoComponents,
  getPayrollRecords,
  generatePayrollFromAttendance,
  getPayslip,
  downloadPayslipPdf,
  emailPayslip,
  markPayrollPaid,
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
  const navigate = useNavigate();
  const role = (localStorage.getItem("nexushr_role") || "EMPLOYEE").toUpperCase();
  const isManagementRole = ["ADMIN", "HR", "MANAGER"].includes(role);

  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [selectedCompany, setSelectedCompany] = useState<string>(
    () => getWorkspaceCompany() ?? "CYOND",
  );
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

  const [generatingFromHours, setGeneratingFromHours] = useState(false);


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

  const companyEmployees = useMemo(
    () =>
      employees.filter(
        (employee) =>
          employee.active === true &&
          (selectedCompany === "All Companies" ||
            isCompanyMatch(employee.companyCode, selectedCompany)),
      ),
    [employees, selectedCompany],
  );

  const payrollEmployees = useMemo(
    () => companyEmployees,
    [companyEmployees],
  );

  const selectedEmployeeData = useMemo(
    () =>
      payrollEmployees.find(
        (employee) => String(employee.id) === selectedEmployee,
      ) ?? null,
    [payrollEmployees, selectedEmployee],
  );

  const companyRecords = useMemo(
    () =>
      records.filter(
        (record) =>
          record.payMonth === payMonth &&
          (selectedCompany === "All Companies" ||
            isCompanyMatch(record.employee.companyCode, selectedCompany)),
      ),
    [records, selectedCompany, payMonth],
  );

  async function loadData() {
    setLoading(true);
    setError("");

    try {
      const isManagementRole = ["ADMIN", "HR", "MANAGER"].includes(role);

      if (!isManagementRole) {
        const me = await getCurrentEmployee();

        setEmployees([me]);
        setSelectedCompany(me.companyCode || "CYOND");
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

  async function handleGenerateFromHours() {
    setGeneratingFromHours(true);
    setError("");
    setMessage("");
    try {
      const result = await generatePayrollFromAttendance(payMonth, selectedCompany);
      setMessage(result.message);
      await loadData();
      document.getElementById("payroll-records")?.scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to generate payroll from working hours.");
    } finally {
      setGeneratingFromHours(false);
    }
  }

  useEffect(() => {
    void loadData();
  }, []);

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
      const companyPrefix = (payslip.companyCode || "CYOND")
        .trim()
        .replace(/[^A-Za-z0-9]+/g, "-");

      link.download =
        `${companyPrefix}-Payslip-${payslip.employeeCode}-${payslip.payMonth}.pdf`;

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
          <p className="page-eyebrow">{selectedCompany === "GORLE" ? "Gorle Group" : selectedCompany === "CYOND" ? "CYOND" : selectedCompany}</p>
          <h1>{role === "EMPLOYEE" ? "My Pay" : "Payroll"}</h1>
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
        <div className="ux-steps" aria-label="Payroll steps">
          <button type="button" onClick={() => navigate("/attendance")}>1 Attendance</button>
          <button type="button" onClick={() => document.getElementById("payroll-inputs")?.scrollIntoView({ behavior: "smooth", block: "start" })}>2 Inputs</button>
          <button type="button" onClick={() => void handleGenerateFromHours()}>3 Calculate</button>
          <button type="button" onClick={() => document.getElementById("payroll-records")?.scrollIntoView({ behavior: "smooth", block: "start" })}>4 Review</button>
          <button type="button" onClick={() => document.getElementById("payroll-records")?.scrollIntoView({ behavior: "smooth", block: "start" })}>5 Payslips</button>
        </div>
      )}

      <div className="ux-balances">
        <article>
          <span>Employees</span>
          <strong>{companyEmployees.length}</strong>
        </article>
        <article>
          <span>Gross</span>
          <strong>{money(companyRecords.reduce((sum, record) => sum + Number(record.grossSalary || 0), 0))}</strong>
        </article>
        <article>
          <span>Deductions</span>
          <strong>{money(companyRecords.reduce((sum, record) => sum + Number(record.deductions || 0), 0))}</strong>
        </article>
        <article>
          <span>Net pay</span>
          <strong>{money(companyRecords.reduce((sum, record) => sum + Number(record.netSalary || 0), 0))}</strong>
        </article>
        <article>
          <span>Flags</span>
          <strong>{companyRecords.filter((record) => Number(record.deductions || 0) > 0).length}</strong>
        </article>
      </div>

      {isManagementRole && (
        <div className="employees-panel" id="payroll-inputs">
          <div className="panel-header">
            <div>
              <p className="page-eyebrow">Payroll</p>
            <h2>Create Salary Record</h2>
            <p>
              Pay is calculated from each employee&apos;s check-in and check-out. A spreadsheet is not used.
            </p>
          </div>
        </div>

          <div className="payroll-attendance-import">
            <div>
              <strong>Generate from working hours</strong>
              <span>
                Uses each employee&apos;s check-in and check-out for {payMonth}. A spreadsheet is not required, and this does not add employees.
              </span>
            </div>
            <button
              type="button"
              className="primary-button"
              onClick={() => void handleGenerateFromHours()}
              disabled={generatingFromHours}
            >
              {generatingFromHours ? "Calculating..." : "Calculate pay from attendance"}
            </button>
          </div>


        <form className="payroll-salary-form" onSubmit={handleGenerate}>
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

                {companyEmployees.map((employee) => (
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
                  Check-in and check-out fill Overtime and Leave Deduction below.
                </span>
              </div>
            </label>

            {selectedEmployee && (
              <div className="payroll-attendance-block">
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

                {autoComponents && (
                  <div className="payroll-attendance-summary">
                    <strong>
                      Attendance calculation for {autoComponents.employeeCode}
                    </strong>
                    <span>
                      Worked {(autoComponents.workedMinutes / 60).toFixed(1)}h of {(autoComponents.expectedWorkMinutes / 60).toFixed(1)}h, shortfall {(autoComponents.shortfallMinutes / 60).toFixed(1)}h.
                    </span>
                    <span>
                      This amount goes into Leave Deduction {money(autoComponents.leaveDeduction)} and Overtime {money(autoComponents.overtimeAmount)}.
                    </span>
                  </div>
                )}
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
            {saving ? "Saving..." : "Save this salary record"}
          </button>
          </form>
        </div>
      )}

      <div className="employees-panel" id="payroll-records">
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
        ) : companyRecords.length === 0 ? (
          <div className="employee-empty-state">
            <strong>No payroll records for {payMonth}</strong>
            <span>Import the salary Excel or generate a payroll record for this month.</span>
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
                {companyRecords.map((record) => (
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
                        View payslip
                      </button>

                      {isManagementRole && record.status !== "PAID" && (
                        <button
                          type="button"
                          className="primary-action"
                          onClick={() =>
                            void handleMarkPaid(record.id)
                          }
                        >
                          Mark paid
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
                Download payslip
              </button>

              {isManagementRole && (
                <button
                  type="button"
                  className="table-action"
                  onClick={() => void handleEmailPayslip()}
                  disabled={emailingPayslip}
                >
                  {emailingPayslip ? "Emailing..." : "Email payslip"}
                </button>
              )}

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















