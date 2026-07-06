import { FormEvent, useEffect, useState } from "react";
import { getEmployees, getAttritionRisk, type Employee, type AttritionRisk } from "../api/client";
import { PageShell } from "../components/PageShell";

const bandColor: Record<string, string> = { LOW: "#15803d", MEDIUM: "#b45309", HIGH: "#dc2626" };
const bandBg:    Record<string, string> = { LOW: "#dcfce7", MEDIUM: "#fef3c7", HIGH: "#fee2e2" };

export const AiPage = () => {
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [employeeId, setEmployeeId] = useState("");
  const [risk, setRisk] = useState<AttritionRisk | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    getEmployees().then(setEmployees).catch(() => {});
  }, []);

  const analyze = async (e: FormEvent) => {
    e.preventDefault();
    if (!employeeId) return;
    setLoading(true);
    setError("");
    setRisk(null);
    try {
      setRisk(await getAttritionRisk(Number(employeeId)));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Analysis failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <PageShell title="AI Attrition Insights">
      <p className="muted mb">Select an employee to compute their attrition risk score using the AI model.</p>

      <form onSubmit={analyze} className="row-controls mb">
        <select value={employeeId} onChange={(e) => setEmployeeId(e.target.value)} className="input-inline" required>
          <option value="">— Select Employee —</option>
          {employees.map((emp) => (
            <option key={emp.id} value={emp.id}>{emp.fullName} ({emp.employeeCode})</option>
          ))}
        </select>
        <button type="submit" className="btn-primary" disabled={loading}>
          {loading ? "Analysing…" : "Analyse"}
        </button>
      </form>

      {error && <p className="error-msg">{error}</p>}

      {risk && (
        <div className="panel risk-card" style={{ borderLeft: `4px solid ${bandColor[risk.riskBand]}`, background: bandBg[risk.riskBand] }}>
          <h3 style={{ color: bandColor[risk.riskBand] }}>
            {risk.riskBand} RISK — {(risk.attritionRisk * 100).toFixed(1)}%
          </h3>
          <p className="risk-label">Employee #{risk.employeeId}</p>
          <div className="risk-bar-track">
            <div className="risk-bar-fill" style={{ width: `${risk.attritionRisk * 100}%`, background: bandColor[risk.riskBand] }} />
          </div>
          <p className="risk-rec">{risk.recommendation}</p>
        </div>
      )}
    </PageShell>
  );
};
