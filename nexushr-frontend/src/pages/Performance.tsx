import { useEffect, useMemo, useState } from "react";
import "./Performance.css";
import {
  createPerformanceFeedback,
  getCurrentEmployee,
  getEmployees,
  getPerformanceDashboard,
  type EmployeeResponse,
  type PerformanceDashboard,
} from "../api";

export default function Performance() {
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [dashboard, setDashboard] = useState<PerformanceDashboard | null>(null);
  const [selectedEmployee, setSelectedEmployee] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [feedbackRating, setFeedbackRating] = useState(5);
  const [feedbackComments, setFeedbackComments] = useState("");
  const [feedbackSubmitting, setFeedbackSubmitting] = useState(false);
  const [feedbackSuccess, setFeedbackSuccess] = useState("");

  const role = localStorage.getItem("nexushr_role") || "HR";
  const currentUser = localStorage.getItem("nexushr_user") || "";

  async function load(employeeId?: number) {
    setLoading(true);
    setError("");

    try {
      const isManagementRole = ["ADMIN", "HR", "MANAGER"].includes(
        role.toUpperCase(),
      );

      if (!isManagementRole) {
        const me = await getCurrentEmployee();

        setEmployees([me]);
        setSelectedEmployee(String(me.id));

        const performanceData = await getPerformanceDashboard(me.id);
        setDashboard(performanceData);

        return;
      }

      const employeeData = await getEmployees();

      const activeEmployees = employeeData.filter(
        (employee) => employee.active !== false,
      );

      setEmployees(activeEmployees);

      let target = employeeId;

      if (target) {
        const selectedExists = activeEmployees.some(
          (employee) => employee.id === target,
        );

        if (!selectedExists) {
          target = undefined;
        }
      }

      if (!target) {
        target = activeEmployees[0]?.id;
      }

      if (!target) {
        setSelectedEmployee("");
        setDashboard(null);
        throw new Error("No active employee records are available.");
      }

      setSelectedEmployee(String(target));

      const performanceData = await getPerformanceDashboard(target);
      setDashboard(performanceData);
    } catch (err) {
      console.error("Performance load failed:", err);

      setError(
        err instanceof Error
          ? err.message
          : "Unable to load performance data.",
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  // Employees receive new feedback automatically without manually
  // refreshing the Performance page.
  useEffect(() => {
    if (role !== "EMPLOYEE" || !selectedEmployee) {
      return;
    }

    const interval = window.setInterval(() => {
      void load(Number(selectedEmployee));
    }, 10000);

    return () => {
      window.clearInterval(interval);
    };
  }, [role, selectedEmployee]);

  async function submitFeedback() {
    const employeeId = Number(selectedEmployee);

    if (!employeeId || !selected) {
      setError("Please select an active employee.");
      return;
    }

    const reviewer = employees.find(
      (employee) =>
        employee.email?.toLowerCase() === currentUser.toLowerCase() ||
        employee.employeeCode?.toLowerCase() === currentUser.toLowerCase() ||
        employee.fullName?.toLowerCase() === currentUser.toLowerCase(),
    );

    if (!reviewer) {
      setError(
        "Your management profile could not be matched to an employee account.",
      );
      return;
    }

    if (reviewer.id === employeeId) {
      setError("You cannot submit performance feedback for yourself.");
      return;
    }

    const comments = feedbackComments.trim();

    if (!comments) {
      setError("Please enter feedback comments.");
      return;
    }

    if (feedbackRating < 1 || feedbackRating > 5) {
      setError("Rating must be between 1 and 5.");
      return;
    }

    try {
      setFeedbackSubmitting(true);
      setError("");
      setFeedbackSuccess("");

      await createPerformanceFeedback({
        employee: {
          id: employeeId,
        },
        reviewer: {
          id: reviewer.id,
        },
        rating: feedbackRating,
        comments,
      });

      // Immediately reload the selected employee dashboard.
      const refreshedDashboard = await getPerformanceDashboard(employeeId);

      setDashboard(refreshedDashboard);
      setSelectedEmployee(String(employeeId));
      setFeedbackComments("");
      setFeedbackRating(5);
      setFeedbackSuccess(
        `Feedback submitted successfully for ${selected.fullName}.`,
      );
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to submit performance feedback.",
      );
    } finally {
      setFeedbackSubmitting(false);
    }
  }

  const scorecard = dashboard?.scorecard;

  const goals = dashboard?.goals ?? [];
  const reviews = dashboard?.reviews ?? [];
  const feedback = dashboard?.feedback ?? [];

  const completedGoals = goals.filter((goal) => goal.completed).length;
  const completedReviews = reviews.length;

  const averageScore = useMemo(() => {
    if (!reviews.length) return 0;

    return Math.round(
      reviews.reduce((sum, review) => sum + Number(review.score || 0), 0) /
        reviews.length,
    );
  }, [reviews]);

  const selected = employees.find(
    (employee) => String(employee.id) === selectedEmployee,
  );

  if (loading) {
    return (
      <div className="performance-page">
        <section className="performance-panel">
          <h3>Loading performance intelligence...</h3>
          <p>Fetching live performance data from NexusHR.</p>
        </section>
      </div>
    );
  }

  if (error) {
    return (
      <div className="performance-page">
        <section className="performance-panel">
          <h3>Unable to load performance</h3>
          <p>{error}</p>
          <button
            className="performance-primary-button"
            type="button"
            onClick={() =>
              void load(
                selectedEmployee ? Number(selectedEmployee) : undefined,
              )
            }
          >
            Retry
          </button>
        </section>
      </div>
    );
  }

  return (
    <div className="performance-page">
      <section className="performance-hero">
        <div>
          <p className="performance-eyebrow">PERFORMANCE INTELLIGENCE</p>
          <h2>Performance Overview</h2>
          <p className="performance-subtitle">
            Live goals, reviews and employee performance insights.
          </p>
        </div>

        {role !== "EMPLOYEE" && (
          <select
            className="performance-filter"
            value={selectedEmployee}
            aria-label="Select employee"
            onChange={(event) => {
              const id = Number(event.target.value);

              if (!Number.isFinite(id) || id <= 0) {
                return;
              }

              void load(id);
            }}
          >
            {employees.map((employee) => (
              <option key={employee.id} value={employee.id}>
                {employee.employeeCode} - {employee.fullName}
              </option>
            ))}
          </select>
        )}
      </section>

      {selected && (
        <section className="performance-panel">
          <div className="performance-panel-header">
            <div>
              <h3>{selected.fullName}</h3>
              <p>
                {selected.employeeCode} - {selected.designation} -{" "}
                {selected.department}
              </p>
            </div>
          </div>
        </section>
      )}

      <section className="performance-stats">
        <div className="performance-stat-card featured">
          <div className="stat-card-top">
            <span>Performance Score</span>
          </div>

          <div className="score-row">
            <strong>{scorecard?.averageScore ?? averageScore}</strong>
            <span>/100</span>
          </div>

          <div className="score-track">
            <div
              className="score-fill"
              style={{
                width: `${Math.min(
                  100,
                  scorecard?.averageScore ?? averageScore,
                )}%`,
              }}
            />
          </div>

          <p>Live employee performance score</p>
        </div>

        <PerformanceStat
          label="Goals Completed"
          value={`${completedGoals}`}
          suffix={`/${goals.length}`}
          detail={
            goals.length
              ? `${Math.round((completedGoals / goals.length) * 100)}% completion`
              : "No goals recorded"
          }
          icon="Goals"
        />

        <PerformanceStat
          label="Reviews"
          value={`${completedReviews}`}
          suffix=""
          detail="Recorded performance reviews"
          icon="Reviews"
        />

        <PerformanceStat
          label="Feedback"
          value={`${feedback.length}`}
          suffix=""
          detail="Manager and peer feedback"
          icon="Feedback"
        />
      </section>

      <section className="performance-main-grid">
        <div className="performance-panel">
          <div className="performance-panel-header">
            <div>
              <h3>Performance Reviews</h3>
              <p>Live reviews stored in NexusHR.</p>
            </div>
          </div>

          {reviews.length === 0 ? (
            <div className="attention-content">
              <div className="attention-icon">i</div>
              <div>
                <strong>No reviews recorded</strong>
                <p>
                  Performance reviews will appear here when they are created.
                </p>
              </div>
            </div>
          ) : (
            <div className="feedback-list">
              {reviews.map((review) => (
                <div className="feedback-item" key={review.id}>
                  <div className="feedback-avatar">
                    {review.score}
                  </div>

                  <div className="feedback-body">
                    <div className="feedback-name-row">
                      <div>
                        <strong>Review {review.reviewYear}</strong>
                        <span>{review.reviewDate}</span>
                      </div>

                      <div className="feedback-rating">
                        Score: {review.score}/100
                      </div>
                    </div>

                    <p>{review.feedback || "No written feedback."}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="performance-panel">
          <div className="performance-panel-header">
            <div>
              <h3>Goals</h3>
              <p>Current employee objectives.</p>
            </div>
          </div>

          {goals.length === 0 ? (
            <div className="attention-content">
              <div>
                <strong>No goals recorded</strong>
                <p>Create goals to begin tracking progress.</p>
              </div>
            </div>
          ) : (
            <div className="department-performance-list">
              {goals.map((goal) => (
                <div className="performance-department" key={goal.id}>
                  <div className="department-performance-info">
                    <div>
                      <strong>{goal.title}</strong>
                      <span>
                        {goal.completed ? "Completed" : "In progress"}
                      </span>
                    </div>

                    <strong>
                      {goal.achievedScore ?? 0}/{goal.targetScore ?? 0}
                    </strong>
                  </div>

                  <div className="department-progress">
                    <div
                      style={{
                        width: `${Math.min(
                          100,
                          Number(goal.targetScore)
                            ? (Number(goal.achievedScore || 0) /
                                Number(goal.targetScore)) *
                                100
                            : 0,
                        )}%`,
                      }}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      {role !== "EMPLOYEE" && selected && (
        <section className="performance-panel feedback-panel">
          <div className="performance-panel-header">
            <div>
              <h3>Give Performance Feedback</h3>
              <p>
                Submit manager or peer feedback for {selected.fullName}.
              </p>
            </div>
          </div>

          {feedbackSuccess && (
            <div className="performance-success-message">
              {feedbackSuccess}
            </div>
          )}

          <div className="performance-feedback-form">
            <label>
              Rating
              <select
                className="performance-filter"
                value={feedbackRating}
                onChange={(event) =>
                  setFeedbackRating(Number(event.target.value))
                }
                disabled={feedbackSubmitting}
              >
                <option value={1}>1 / 5 - Needs Improvement</option>
                <option value={2}>2 / 5 - Developing</option>
                <option value={3}>3 / 5 - Meets Expectations</option>
                <option value={4}>4 / 5 - Very Good</option>
                <option value={5}>5 / 5 - Excellent</option>
              </select>
            </label>

            <label>
              Comments
              <textarea
                value={feedbackComments}
                onChange={(event) =>
                  setFeedbackComments(event.target.value)
                }
                placeholder={`Write performance feedback for ${selected.fullName}...`}
                rows={5}
                disabled={feedbackSubmitting}
              />
            </label>

            <button
              className="performance-primary-button"
              type="button"
              onClick={() => void submitFeedback()}
              disabled={feedbackSubmitting || !feedbackComments.trim()}
            >
              {feedbackSubmitting ? "Submitting..." : "Submit Feedback"}
            </button>
          </div>
        </section>
      )}

      <section className="performance-panel feedback-panel">
        <div className="performance-panel-header">
          <div>
            <h3>Recent Feedback</h3>
            <p>Live feedback from Cyond.</p>
          </div>
        </div>

        {feedback.length === 0 ? (
          <div className="employee-empty-state">
            <strong>No feedback recorded</strong>
            <span>Submitted performance feedback will appear here.</span>
          </div>
        ) : (
          <div className="feedback-list">
            {feedback.map((item) => (
              <div className="feedback-item" key={item.id}>
                <div className="feedback-avatar">
                  {item.reviewer?.fullName?.slice(0, 1).toUpperCase() || "F"}
                </div>

                <div className="feedback-body">
                  <div className="feedback-name-row">
                    <div>
                      <strong>
                        {item.reviewer?.fullName || "Reviewer"}
                      </strong>
                      <span>{item.createdAt || "Recently"}</span>
                    </div>

                    <div className="feedback-rating">
                      Rating: {item.rating ?? "Not rated"}/5
                    </div>
                  </div>

                  <p>{item.comments || item.feedback || "No comments provided."}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function PerformanceStat({
  label,
  value,
  suffix,
  detail,
  icon,
}: {
  label: string;
  value: string;
  suffix: string;
  detail: string;
  icon: string;
}) {
  return (
    <div className="performance-stat-card">
      <div className="performance-stat-icon">{icon}</div>
      <span className="performance-stat-label">{label}</span>

      <div className="performance-stat-value">
        <strong>{value}</strong>
        {suffix && <span>{suffix}</span>}
      </div>

      <span className="performance-stat-detail">{detail}</span>
    </div>
  );
}
