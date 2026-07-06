import { useEffect, useState } from "react";
import { getPayroll, createPayroll, type PayrollRecord, type PayrollInput } from "../api/client";
import { PageShell } from "../components/PageShell";

const blank: PayrollInput = { employee: { id: 0 }, payMonth: "", grossSalary: 0, deductions: 0, netSalary: 0 };

export const PayrollPage = () => {
  const [records, setRecords] = useState<PayrollRecord[]>([]);
  const [form, setForm] = useState<PayrollInput>(blank);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const load = () => getPayroll().then(setRecords).catch((e) => setError(e.message));
  useEffect(() => { load(); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await createPayroll(form);
      setForm(blank);
      setShowForm(false);
      await load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Save failed");
    } finally {
      setSaving(false);
    }
  };

  const num = (field: keyof PayrollInput) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [field]: Number(e.target.value) }));

  return (
    <PageShell title="Payroll">
      {error && <p className="error-msg">{error}</p>}
      <button className="btn-primary mb" onClick={() => setShowForm((v) => !v)}>
        {showForm ? "Cancel" : "+ Add Record"}
      </button>

      {showForm && (
        <form onSubmit={handleSubmit} className="form form-grid panel mb">
          <label>Employee ID<input required type="number" min="1" value={form.employee.id || ""} onChange={(e) => setForm((f) => ({ ...f, employee: { id: Number(e.target.value) } }))} /></label>
          <label>Pay Month<input required value={form.payMonth} placeholder="e.g. 2026-07" onChange={(e) => setForm((f) => ({ ...f, payMonth: e.target.value }))} /></label>
          <label>Gross Salary<input required type="number" min="0" value={form.grossSalary} onChange={num("grossSalary")} /></label>
          <label>Deductions<input required type="number" min="0" value={form.deductions} onChange={num("deductions")} /></label>
          <label>Net Salary<input required type="number" min="0" value={form.netSalary} onChange={num("netSalary")} /></label>
          <button type="submit" className="btn-primary span-2" disabled={saving}>{saving ? "Saving…" : "Save"}</button>
        </form>
      )}

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr><th>Employee</th><th>Month</th><th>Gross</th><th>Deductions</th><th>Net</th></tr>
          </thead>
          <tbody>
            {records.map((r) => (
              <tr key={r.id}>
                <td>{r.employee.fullName ?? r.employee.id}</td>
                <td>{r.payMonth}</td>
                <td>₹{r.grossSalary.toLocaleString()}</td>
                <td>₹{r.deductions.toLocaleString()}</td>
                <td className="font-bold">₹{r.netSalary.toLocaleString()}</td>
              </tr>
            ))}
            {records.length === 0 && <tr><td colSpan={5} className="empty">No payroll records.</td></tr>}
          </tbody>
        </table>
      </div>
    </PageShell>
  );
};
