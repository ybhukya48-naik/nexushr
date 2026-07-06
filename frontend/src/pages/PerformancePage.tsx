import { useEffect, useState } from "react";
import {
  getPerformanceReviews, createPerformanceReview,
  type PerformanceReview, type PerformanceInput
} from "../api/client";
import { PageShell } from "../components/PageShell";

const blank: PerformanceInput = { employee: { id: 0 }, reviewYear: new Date().getFullYear(), score: 70, feedback: "", reviewDate: "" };

const scoreColor = (s: number) => s >= 75 ? "#15803d" : s >= 60 ? "#b45309" : "#dc2626";

export const PerformancePage = () => {
  const [reviews, setReviews] = useState<PerformanceReview[]>([]);
  const [form, setForm] = useState<PerformanceInput>(blank);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const load = () => getPerformanceReviews().then(setReviews).catch((e) => setError(e.message));
  useEffect(() => { load(); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await createPerformanceReview(form);
      setForm(blank);
      setShowForm(false);
      await load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Save failed");
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageShell title="Performance Reviews">
      {error && <p className="error-msg">{error}</p>}
      <button className="btn-primary mb" onClick={() => setShowForm((v) => !v)}>
        {showForm ? "Cancel" : "+ Add Review"}
      </button>

      {showForm && (
        <form onSubmit={handleSubmit} className="form panel mb">
          <label>Employee ID<input required type="number" min="1" value={form.employee.id || ""} onChange={(e) => setForm((f) => ({ ...f, employee: { id: Number(e.target.value) } }))} /></label>
          <label>Year<input required type="number" min="2000" value={form.reviewYear} onChange={(e) => setForm((f) => ({ ...f, reviewYear: Number(e.target.value) }))} /></label>
          <label>Score (0–100)<input required type="number" min="0" max="100" value={form.score} onChange={(e) => setForm((f) => ({ ...f, score: Number(e.target.value) }))} /></label>
          <label>Review Date<input required type="date" value={form.reviewDate} onChange={(e) => setForm((f) => ({ ...f, reviewDate: e.target.value }))} /></label>
          <label>Feedback<input required value={form.feedback} onChange={(e) => setForm((f) => ({ ...f, feedback: e.target.value }))} /></label>
          <button type="submit" className="btn-primary" disabled={saving}>{saving ? "Saving…" : "Save"}</button>
        </form>
      )}

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr><th>Employee</th><th>Year</th><th>Score</th><th>Date</th><th>Feedback</th></tr>
          </thead>
          <tbody>
            {reviews.map((r) => (
              <tr key={r.id}>
                <td>{r.employee.fullName ?? r.employee.id}</td>
                <td>{r.reviewYear}</td>
                <td style={{ color: scoreColor(r.score), fontWeight: 600 }}>{r.score}</td>
                <td>{r.reviewDate}</td>
                <td>{r.feedback}</td>
              </tr>
            ))}
            {reviews.length === 0 && <tr><td colSpan={5} className="empty">No reviews.</td></tr>}
          </tbody>
        </table>
      </div>
    </PageShell>
  );
};
