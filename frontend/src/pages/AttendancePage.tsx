import { useState } from "react";
import { getAttendanceByDate, createAttendance, type AttendanceRecord, type AttendanceInput } from "../api/client";
import { PageShell } from "../components/PageShell";

const today = () => new Date().toISOString().slice(0, 10);

export const AttendancePage = () => {
  const [date, setDate] = useState(today());
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [searched, setSearched] = useState(false);
  const [form, setForm] = useState<AttendanceInput>({
    employee: { id: 0 }, attendanceDate: today(),
    checkInTime: null, checkOutTime: null, workMinutes: 0,
  });
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const load = () =>
    getAttendanceByDate(date)
      .then((r) => { setRecords(r); setSearched(true); })
      .catch((e) => setError(e.message));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await createAttendance(form);
      setShowForm(false);
      await load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Save failed");
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageShell title="Attendance">
      {error && <p className="error-msg">{error}</p>}

      <div className="row-controls mb">
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} className="input-inline" />
        <button className="btn-primary" onClick={load}>Search</button>
        <button className="btn-secondary" onClick={() => setShowForm((v) => !v)}>
          {showForm ? "Cancel" : "+ Log Attendance"}
        </button>
      </div>

      {showForm && (
        <form onSubmit={handleSubmit} className="form panel mb">
          <label>Employee ID<input required type="number" min="1" value={form.employee.id || ""} onChange={(e) => setForm((f) => ({ ...f, employee: { id: Number(e.target.value) } }))} /></label>
          <label>Date<input required type="date" value={form.attendanceDate} onChange={(e) => setForm((f) => ({ ...f, attendanceDate: e.target.value }))} /></label>
          <label>Check-in<input type="datetime-local" value={form.checkInTime ?? ""} onChange={(e) => setForm((f) => ({ ...f, checkInTime: e.target.value || null }))} /></label>
          <label>Check-out<input type="datetime-local" value={form.checkOutTime ?? ""} onChange={(e) => setForm((f) => ({ ...f, checkOutTime: e.target.value || null }))} /></label>
          <label>Work Minutes<input required type="number" min="0" value={form.workMinutes} onChange={(e) => setForm((f) => ({ ...f, workMinutes: Number(e.target.value) }))} /></label>
          <button type="submit" className="btn-primary" disabled={saving}>{saving ? "Saving…" : "Save"}</button>
        </form>
      )}

      {searched && (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr><th>Employee</th><th>Date</th><th>Check-in</th><th>Check-out</th><th>Work (min)</th></tr>
            </thead>
            <tbody>
              {records.map((r) => (
                <tr key={r.id}>
                  <td>{r.employee.fullName ?? r.employee.id}</td>
                  <td>{r.attendanceDate}</td>
                  <td>{r.checkInTime ?? "—"}</td>
                  <td>{r.checkOutTime ?? "—"}</td>
                  <td>{r.workMinutes}</td>
                </tr>
              ))}
              {records.length === 0 && <tr><td colSpan={5} className="empty">No records for {date}.</td></tr>}
            </tbody>
          </table>
        </div>
      )}
    </PageShell>
  );
};
