import { useEffect, useMemo, useState } from "react";
import {
  createPayroll,
  getEmployees,
  getCurrentEmployee,
  getEmployeePayroll,
  getPayrollRecords,
  getPayslip,
  markPayrollPaid,
  type EmployeeResponse,
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

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const [payslip, setPayslip] = useState<PayslipResponse | null>(null);
  const [payslipLoading, setPayslipLoading] = useState(false);

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

  useEffect(() => {
    void loadData();
  }, []);

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
        hra: Number(hra || 0),
        bonus: Number(bonus || 0),
        overtime: Number(overtime || 0),
        pf: Number(pf || 0),
        leaveDeduction: Number(leaveDeduction || 0),
        otherDeductions: Number(otherDeductions || 0),
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

            <button
              type="button"
              className="table-action"
              onClick={() => setPayslip(null)}
            >
              Close
            </button>
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
