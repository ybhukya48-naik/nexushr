import { useEffect, useState } from "react";
import {
  getLeaveRequests, createLeave, updateLeaveStatus,
  type LeaveRequest, type LeaveInput
} from "../api/client";
import { PageShell } from "../components/PageShell";

const blank: LeaveInput = { employee: { id: 0 }, startDate: "", endDate: "", reason: "", status: "PENDING" };

const statusColor: Record<string, string> = { PENDING: "badge-yellow", APPROVED: "badge-green", REJECTED: "badge-red" };

export const LeavePage = () => {
  const [leaves, setLeaves] = useState<LeaveRequest[]>([]);
  const [form, setForm] = useState<LeaveInput>(blank);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const load = () => getLeaveRequests().then(setLeaves).catch((e) => setError(e.message));
  useEffect(() => { load(); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await createLeave(form);
      setForm(blank);
      setShowForm(false);
      await load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Save failed");
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = async (id: number, status: string) => {
    try { await updateLeaveStatus(id, status); await load(); }
    catch (e: unknown) { setError(e instanceof Error ? e.message : "Update failed"); }
  };

  return (
    <PageShell title="Leave Requests">
      {error && <p className="error-msg">{error}</p>}
      <button className="btn-primary mb" onClick={() => setShowForm((v) => !v)}>
        {showForm ? "Cancel" : "+ New Request"}
      </button>

      {showForm && (
        <form onSubmit={handleSubmit} className="form panel mb">
          <label>Employee ID<input required type="number" min="1" value={form.employee.id || ""} onChange={(e) => setForm((f) => ({ ...f, employee: { id: Number(e.target.value) } }))} /></label>
          <label>Start Date<input required type="date" value={form.startDate} onChange={(e) => setForm((f) => ({ ...f, startDate: e.target.value }))} /></label>
          <label>End Date<input required type="date" value={form.endDate} onChange={(e) => setForm((f) => ({ ...f, endDate: e.target.value }))} /></label>
          <label>Reason<input required value={form.reason} onChange={(e) => setForm((f) => ({ ...f, reason: e.target.value }))} /></label>
          <button type="submit" className="btn-primary" disabled={saving}>{saving ? "Saving…" : "Submit"}</button>
        </form>
      )}

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr><th>ID</th><th>Employee</th><th>Start</th><th>End</th><th>Reason</th><th>Status</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {leaves.map((l) => (
              <tr key={l.id}>
                <td>{l.id}</td>
                <td>{l.employee.fullName ?? l.employee.id}</td>
                <td>{l.startDate}</td>
                <td>{l.endDate}</td>
                <td>{l.reason}</td>
                <td><span className={`badge ${statusColor[l.status]}`}>{l.status}</span></td>
                <td>
                  {l.status === "PENDING" && (
                    <>
                      <button className="btn-sm btn-green" onClick={() => changeStatus(l.id, "APPROVED")}>Approve</button>
                      <button className="btn-sm btn-red" onClick={() => changeStatus(l.id, "REJECTED")}>Reject</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
            {leaves.length === 0 && <tr><td colSpan={7} className="empty">No leave requests.</td></tr>}
          </tbody>
        </table>
      </div>
    </PageShell>
  );
};
