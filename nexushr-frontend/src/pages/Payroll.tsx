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

  const [ctc, setCtc] = useState("");
  const [hra, setHra] = useState("");
  const [bonus, setBonus] = useState("");
  const [overtime, setOvertime] = useState("");
  const [pf, setPf] = useState("");
  const [insurance, setInsurance] = useState("");
  const [professionalTax, setProfessionalTax] = useState("");
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
  const [downloadingId, setDownloadingId] = useState<number | null>(null);
  const [emailingId, setEmailingId] = useState<number | null>(null);

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

  const visibleRecords = isManagementRole ? companyRecords : records;

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
        ctc: toOptionalNumber(ctc),
        hra: toOptionalNumber(hra),
        bonus: toOptionalNumber(bonus),
        overtime: toOptionalNumber(overtime),
        pf: toOptionalNumber(pf),
        esi: toOptionalNumber(insurance),
        pt: toOptionalNumber(professionalTax),
        leaveDeduction: toOptionalNumber(leaveDeduction),
        otherDeductions: toOptionalNumber(otherDeductions),
      });

      setMessage("Payroll generated successfully.");

      setCtc("");
      setHra("");
      setBonus("");
      setOvertime("");
      setPf("");
      setInsurance("");
      setProfessionalTax("");
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

  async function handleDownloadPdf(target: {
    payrollId: number;
    employeeCode: string;
    payMonth: string;
    companyCode?: string;
  }) {
    setDownloadingId(target.payrollId);
    setError("");
    setMessage("");

    try {
      const pdf = await downloadPayslipPdf(target.payrollId);

      const url = window.URL.createObjectURL(pdf);
      const link = document.createElement("a");

      link.href = url;
      const companyPrefix = (target.companyCode || "CYOND")
        .trim()
        .replace(/[^A-Za-z0-9]+/g, "");

      link.download =
        `${companyPrefix}-Payslip-${target.employeeCode}-${target.payMonth}.pdf`;

      document.body.appendChild(link);
      link.click();
      link.remove();

      window.URL.revokeObjectURL(url);
      setMessage("Payslip downloaded.");
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to download payslip PDF.",
      );
    } finally {
      setDownloadingId(null);
    }
  }

  async function handleEmailPayslip(payrollId: number) {
    setEmailingId(payrollId);
    setError("");
    setMessage("");

    try {
      await emailPayslip(payrollId);
      setMessage("Payslip emailed only to this employee.");
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to email payslip.",
      );
    } finally {
      setEmailingId(null);
    }
  }


  return (
    <section className="employees-page">
      <div className="employees-header">
        <div>
          <p className="page-eyebrow">{selectedCompany === "GORLE" ? "Gorle Group" : selectedCompany === "CYOND" ? "CYOND" : selectedCompany}</p>
          <h1>{role === "EMPLOYEE" ? "My Pay" : "Payroll"}</h1>
          <p>
            {role === "EMPLOYEE"
              ? "Download your payslip."
              : "Download a payslip, or email that payslip to the employee."}
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
          <strong>{money(visibleRecords.reduce((sum, record) => sum + Number(record.grossSalary || 0), 0))}</strong>
        </article>
        <article>
          <span>Deductions</span>
          <strong>{money(visibleRecords.reduce((sum, record) => sum + Number(record.deductions || 0), 0))}</strong>
        </article>
        <article>
          <span>Net pay</span>
          <strong>{money(visibleRecords.reduce((sum, record) => sum + Number(record.netSalary || 0), 0))}</strong>
        </article>
        <article>
          <span>Flags</span>
          <strong>{visibleRecords.filter((record) => Number(record.deductions || 0) > 0).length}</strong>
        </article>
      </div>

      {isManagementRole && (
        <div className="employees-panel" id="payroll-inputs">
          <div className="panel-header">
            <div>
              <p className="page-eyebrow">Payroll</p>
            <h2>Create Salary Record</h2>
            <p>
              Pay for {payMonth} uses the working hours from the 1st through the last day. Today stays separate until that day is complete, then those hours join the month total used for the CTC.
            </p>
          </div>
        </div>

          <div className="payroll-attendance-import">
            <div>
              <strong>Generate from working hours</strong>
              <span>
                Adds check-in to check-out for every day in {payMonth}, including a day that is still open. The monthly CTC amount is paid in proportion to those hours. PF, insurance, and professional tax stay on the record.
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
                      {payMonth} runs from the 1st through day {new Date(Number(payMonth.slice(0, 4)), Number(payMonth.slice(5, 7)), 0).getDate()}. Worked {(autoComponents.workedMinutes / 60).toFixed(1)}h of {(autoComponents.expectedWorkMinutes / 60).toFixed(1)}h.
                      {ctc.trim() ? ` Yearly CTC ${money(Number(ctc))}. One month is ${money(Number(ctc) / 12)}.` : selectedEmployeeData?.baseSalary ? ` Monthly amount ${money(selectedEmployeeData.baseSalary)}.` : " Enter a yearly CTC, or save a base salary, before pay can be calculated."}
                    </span>
                    <span>
                      This amount goes into Leave Deduction {money(autoComponents.leaveDeduction)} and Overtime {money(autoComponents.overtimeAmount)}.
                    </span>
                  </div>
                )}
              </div>
            )}

            <label>
              CTC
              <input
                min="0"
                type="number"
                step="0.01"
                value={ctc}
                onChange={(event) => setCtc(event.target.value)}
                placeholder="Yearly amount"
              />
            </label>
            <p>Yearly CTC. When base salary is empty, one month of pay is this amount divided by 12.</p>

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
              Insurance
              <input
                min="0"
                type="number"
                step="0.01"
                value={insurance}
                onChange={(event) => setInsurance(event.target.value)}
                placeholder="0"
              />
            </label>

            <label>
              Professional tax
              <input
                min="0"
                type="number"
                step="0.01"
                value={professionalTax}
                onChange={(event) => setProfessionalTax(event.target.value)}
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
              {role === "EMPLOYEE"
                ? "Your salary records. Download a payslip here."
                : "Generated payroll records and payment status."}
            </p>
          </div>
          {role === "EMPLOYEE" && (
            <div className="payroll-page-actions">
              <button
                type="button"
                className="primary-action"
                onClick={() => {
                  const latest = [...records].sort((a, b) => b.payMonth.localeCompare(a.payMonth))[0];
                  if (!latest) {
                    setError("No payslip yet. It appears after pay is calculated.");
                    return;
                  }
                  void handleDownloadPdf({
                    payrollId: latest.id,
                    employeeCode: latest.employee.employeeCode,
                    payMonth: latest.payMonth,
                    companyCode: latest.employee.companyCode,
                  });
                }}
                disabled={downloadingId !== null}
              >
                {downloadingId !== null ? "Downloading..." : "Download payslip"}
              </button>
            </div>
          )}
        </div>

        {loading ? (
          <div className="employee-empty-state">
            <strong>Loading payroll...</strong>
            <span>Fetching salary records from the backend.</span>
          </div>
        ) : visibleRecords.length === 0 ? (
          <div className="employee-empty-state">
            <strong>
              {role === "EMPLOYEE"
                ? "No payslip yet"
                : `No payroll records for ${payMonth}`}
            </strong>
            <span>
              {role === "EMPLOYEE"
                ? "Your payslip appears here after pay is calculated."
                : "Calculate pay from attendance for this month."}
            </span>
          </div>
        ) : (
          <div className="table-wrapper">
            <table className="employees-table payroll-records">
              <thead>
                <tr>
                  <th>Employee</th>
                  <th>Month</th>
                  {isManagementRole && <th>Hours</th>}
                  <th>Gross Salary</th>
                  {isManagementRole && <th>CTC</th>}
                  {isManagementRole && <th>PF</th>}
                  {isManagementRole && <th>Insurance</th>}
                  {isManagementRole && <th>Professional tax</th>}
                  <th>Deductions</th>
                  <th>Net Salary</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>

              <tbody>
                {visibleRecords.map((record) => (
                  <tr key={record.id}>
                    <td>
                      <strong>{record.employee.fullName}</strong>
                      <span>
                        {record.employee.employeeCode}
                      </span>
                    </td>

                    <td>{record.payMonth}</td>

                    {isManagementRole && (
                      <td>
                        {record.workedMinutes == null
                          ? "—"
                          : `${(record.workedMinutes / 60).toFixed(1)}h`}
                      </td>
                    )}

                    <td>{money(record.grossSalary)}</td>
                    {isManagementRole && <td>{record.ctc ? money(record.ctc) : "—"}</td>}

                    {isManagementRole && <td>{money(record.pf)}</td>}
                    {isManagementRole && <td>{money(record.esi || 0)}</td>}
                    {isManagementRole && <td>{money(record.pt || 0)}</td>}

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
                      <div className="payroll-row-actions">
                      <button
                        type="button"
                        className="primary-action"
                        onClick={() =>
                          void handleDownloadPdf({
                            payrollId: record.id,
                            employeeCode: record.employee.employeeCode,
                            payMonth: record.payMonth,
                            companyCode: record.employee.companyCode,
                          })
                        }
                        disabled={downloadingId === record.id}
                      >
                        {downloadingId === record.id
                          ? "Downloading..."
                          : "Download payslip"}
                      </button>

                      {isManagementRole && (
                        <button
                          type="button"
                          className="table-action"
                          onClick={() => void handleEmailPayslip(record.id)}
                          disabled={emailingId === record.id}
                        >
                          {emailingId === record.id
                            ? "Emailing..."
                            : "Email payslip"}
                        </button>
                      )}

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
                      </div>
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
                {payslip.companyName || payslip.companyCode} · {payslip.employeeCode} · Status: {payslip.status}
              </p>
            </div>

            <div>
              <button
                type="button"
                className="primary-action"
                onClick={() =>
                  void handleDownloadPdf({
                    payrollId: payslip.payrollId,
                    employeeCode: payslip.employeeCode,
                    payMonth: payslip.payMonth,
                    companyCode: payslip.companyCode,
                  })
                }
                disabled={downloadingId === payslip.payrollId}
              >
                {downloadingId === payslip.payrollId
                  ? "Downloading..."
                  : "Download payslip"}
              </button>

              {isManagementRole && (
                <button
                  type="button"
                  className="table-action"
                  onClick={() => void handleEmailPayslip(payslip.payrollId)}
                  disabled={emailingId === payslip.payrollId}
                >
                  {emailingId === payslip.payrollId
                    ? "Emailing..."
                    : "Email payslip"}
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















