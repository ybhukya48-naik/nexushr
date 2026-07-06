import { useEffect, useState } from "react";
import { getDashboardSummary, getAttritionRisk, type DashboardSummary, type AttritionRisk } from "../api/client";
import { PageShell } from "../components/PageShell";

const bandColor: Record<string, string> = { LOW: "#15803d", MEDIUM: "#b45309", HIGH: "#dc2626" };

export const DashboardPage = () => {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [risk, setRisk] = useState<AttritionRisk | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([getDashboardSummary(), getAttritionRisk(2)])
      .then(([s, r]) => { setSummary(s); setRisk(r); })
      .catch((e) => setError(e.message));
  }, []);

  if (error) return <PageShell title="Dashboard"><p className="error-msg">{error}</p></PageShell>;

  const cards = summary ? [
    { label: "Employees",         value: summary.totalEmployees },
    { label: "Attendance Events", value: summary.attendanceEvents },
    { label: "Leave Requests",    value: summary.leaveRequests },
    { label: "Payroll Records",   value: summary.payrollRecords },
  ] : [];

  return (
    <PageShell title="Dashboard">
      <div className="cards">
        {cards.map((c) => (
          <article className="card" key={c.label}>
            <h3>{c.label}</h3>
            <p className="card-value">{c.value ?? "—"}</p>
          </article>
        ))}
      </div>

      {risk && (
        <section className="panel mt">
          <h3>AI Attrition Insight — Employee #2</h3>
          <p style={{ color: bandColor[risk.riskBand], fontWeight: 600 }}>
            {risk.riskBand} RISK ({(risk.attritionRisk * 100).toFixed(1)}%)
          </p>
          <p>{risk.recommendation}</p>
        </section>
      )}
    </PageShell>
  );
};
