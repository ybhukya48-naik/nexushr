import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  getCurrentEmployee,
  getDashboardSummary,
  getEmployees,
  getHolidays,
  type CompanyHoliday,
  type DashboardSummary,
  type EmployeeResponse,
} from "../api";
import {
  getAttendanceByDate,
  getLeaves,
  type AttendanceRecord,
  type LeaveRequest,
} from "./Attendance";
import { isCompanyMatch } from "../utils/company";
import { getWorkspaceCompany } from "../utils/workspace";
import "./Dashboard.css";

const PUBLIC_HOLIDAYS = [
  { month: 1, day: 26, name: "Republic Day" },
  { month: 8, day: 15, name: "Independence Day" },
  { month: 10, day: 2, name: "Gandhi Jayanti" },
  { month: 12, day: 25, name: "Christmas" },
];

type HolidayMark = { date: string; name: string };

function todayIso() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

function clock(value?: string) {
  if (!value) {
    return "—";
  }
  const time = value.includes("T") ? value.split("T")[1] : value;
  return time.slice(0, 5);
}

function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return (parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? "");
}

function upcomingHolidays(extra: CompanyHoliday[], company: string): HolidayMark[] {
  const today = todayIso();
  const year = Number(today.slice(0, 4));
  const marks: HolidayMark[] = [];

  for (const holiday of PUBLIC_HOLIDAYS) {
    const month = String(holiday.month).padStart(2, "0");
    const day = String(holiday.day).padStart(2, "0");
    let date = `${year}-${month}-${day}`;
    if (date < today) {
      date = `${year + 1}-${month}-${day}`;
    }
    marks.push({ date, name: holiday.name });
  }

  for (const holiday of extra) {
    const code = holiday.companyCode?.trim();
    if (code && !isCompanyMatch(code, company)) {
      continue;
    }
    if (holiday.holidayDate >= today) {
      marks.push({ date: holiday.holidayDate, name: holiday.name });
    }
  }

  return marks
    .sort((a, b) => a.date.localeCompare(b.date))
    .filter((holiday, index, all) => all.findIndex((item) => item.date === holiday.date) === index)
    .slice(0, 3);
}

function monthCells(year: number, monthIndex: number) {
  const first = new Date(year, monthIndex, 1);
  const start = first.getDay();
  const days = new Date(year, monthIndex + 1, 0).getDate();
  const cells: Array<number | null> = Array.from({ length: start }, () => null);
  for (let day = 1; day <= days; day += 1) {
    cells.push(day);
  }
  return cells;
}

export default function Dashboard() {
  const navigate = useNavigate();
  const company = getWorkspaceCompany() ?? "CYOND";
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [me, setMe] = useState<EmployeeResponse | null>(null);
  const [people, setPeople] = useState<EmployeeResponse[]>([]);
  const [todayRows, setTodayRows] = useState<AttendanceRecord[]>([]);
  const [week, setWeek] = useState<Array<{ label: string; present: number }>>([]);
  const [leaves, setLeaves] = useState<LeaveRequest[]>([]);
  const [holidays, setHolidays] = useState<HolidayMark[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    const today = todayIso();
    const dates = Array.from({ length: 7 }, (_, index) => {
      const date = new Date();
      date.setDate(date.getDate() - (6 - index));
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");
      return `${date.getFullYear()}-${month}-${day}`;
    });

    Promise.all([
      getDashboardSummary().catch(() => null),
      getCurrentEmployee().catch(() => null),
      getEmployees().catch(() => [] as EmployeeResponse[]),
      getAttendanceByDate(today).catch(() => [] as AttendanceRecord[]),
      Promise.all(dates.map((date) => getAttendanceByDate(date).catch(() => [] as AttendanceRecord[]))),
      getLeaves().catch(() => [] as LeaveRequest[]),
      getHolidays().catch(() => [] as CompanyHoliday[]),
    ])
      .then(([dashboard, current, employees, attendance, history, leaveRows, extraHolidays]) => {
        if (cancelled) {
          return;
        }
        setSummary(dashboard);
        setMe(current);
        setPeople(employees.filter((employee) => isCompanyMatch(employee.companyCode, company)));
        setTodayRows(attendance.filter((row) => isCompanyMatch(row.employee?.companyCode, company)));
        setWeek(dates.map((date, index) => ({
          label: date.slice(8),
          present: history[index].filter((row) =>
            isCompanyMatch(row.employee?.companyCode, company)
            && row.checkInTime
            && row.attendanceStatus !== "INACTIVE",
          ).length,
        })));
        setLeaves(leaveRows.filter((leave) => isCompanyMatch(leave.employee?.companyCode, company)));
        setHolidays(upcomingHolidays(extraHolidays, company));
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load the dashboard.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [company]);

  if (loading) {
    return (
      <div className="page-loading">
        <div className="loading-spinner"></div>
        <strong>Loading workforce dashboard</strong>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page-heading">
        <h2>Workforce Dashboard</h2>
        <p>{error}</p>
      </div>
    );
  }

  const today = todayIso();
  const present = todayRows.filter((row) => row.checkInTime && row.attendanceStatus !== "INACTIVE");
  const onLeave = leaves.filter((leave) =>
    leave.status === "APPROVED" && leave.startDate <= today && leave.endDate >= today,
  );
  const pending = leaves.filter((leave) => leave.status === "PENDING");
  const missingCheckIn = people.filter((employee) =>
    employee.active
    && !todayRows.some((row) => row.employee?.id === employee.id && row.checkInTime),
  ).length;
  const missingCheckOut = todayRows.filter((row) =>
    row.checkInTime && !row.checkOutTime && row.attendanceStatus !== "INACTIVE",
  ).length;
  const late = todayRows.filter((row) => row.lateWarning || (row.lateArrivalMinutes ?? 0) > 0).length;
  const maxPresent = Math.max(1, ...week.map((day) => day.present));
  const now = new Date();
  const cells = monthCells(now.getFullYear(), now.getMonth());
  const holidayDays = new Set(holidays.filter((holiday) => holiday.date.startsWith(today.slice(0, 7))).map((holiday) => Number(holiday.date.slice(8))));
  const displayName = me?.fullName || localStorage.getItem("nexushr_user") || "there";

  return (
    <div className="hr-home">
      <header className="hr-home-head">
        <div>
          <p>Good day, {displayName}</p>
          <h2>Here is today for {company === "GORLE" ? "Gorle Group" : "CYOND"}.</h2>
        </div>
        <time dateTime={today}>
          {now.toLocaleDateString("en-IN", { weekday: "short", day: "numeric", month: "short", year: "numeric" })}
        </time>
      </header>

      <section className="hr-kpis">
        <button type="button" onClick={() => navigate("/employees")}>
          <span>Total employees</span>
          <strong>{people.length || summary?.totalEmployees || 0}</strong>
        </button>
        <button type="button" onClick={() => navigate("/attendance")}>
          <span>Present today</span>
          <strong>{present.length}</strong>
        </button>
        <button type="button" onClick={() => navigate("/leave")}>
          <span>On leave</span>
          <strong>{onLeave.length}</strong>
        </button>
        <button type="button" onClick={() => navigate("/leave")}>
          <span>Pending requests</span>
          <strong>{pending.length || summary?.pendingLeaveRequests || 0}</strong>
        </button>
      </section>

      <section className="hr-home-grid">
        <article className="hr-card">
          <div className="hr-card-title">
            <h3>This week</h3>
            <button type="button" onClick={() => navigate("/attendance")}>Attendance</button>
          </div>
          <div className="hr-bars" aria-label="Present employees for the last seven days">
            {week.map((day) => (
              <button key={day.label} type="button" onClick={() => navigate("/attendance")}>
                <span style={{ height: `${Math.max(8, (day.present / maxPresent) * 100)}%` }}></span>
                <small>{day.label}</small>
              </button>
            ))}
          </div>
          <div className="hr-alerts">
            <button type="button" onClick={() => navigate("/attendance")}>{late} late arrivals</button>
            <button type="button" onClick={() => navigate("/attendance")}>{missingCheckIn} did not check in</button>
            <button type="button" onClick={() => navigate("/attendance")}>{missingCheckOut} did not check out</button>
          </div>
        </article>

        <article className="hr-card">
          <div className="hr-card-title">
            <h3>Today</h3>
            <button type="button" onClick={() => navigate("/attendance")}>View all</button>
          </div>
          {present.length === 0 ? (
            <p className="hr-empty">No check-ins yet today.</p>
          ) : (
            <ul className="hr-people">
              {present.slice(0, 6).map((row) => (
                <li key={row.id}>
                  <button type="button" onClick={() => navigate(`/employees/${row.employee.id}`)}>
                    <span>{initials(row.employee.fullName || "Employee")}</span>
                    <strong>{row.employee.fullName}</strong>
                    <em>{row.checkOutTime ? "Checked out" : "Checked in"}</em>
                    <time>{clock(row.checkInTime)}</time>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </article>

        <article className="hr-card hr-actions">
          <h3>Open</h3>
          <button type="button" onClick={() => navigate("/attendance")}>Attendance</button>
          <button type="button" onClick={() => navigate("/leave")}>Leave</button>
          <button type="button" onClick={() => navigate("/payroll")}>Payroll</button>
          <button type="button" onClick={() => navigate("/employees")}>Employees</button>
        </article>
      </section>

      <section className="hr-home-grid hr-home-lower">
        <article className="hr-card">
          <div className="hr-card-title">
            <h3>Leave requests</h3>
            <button type="button" onClick={() => navigate("/leave")}>Open leave</button>
          </div>
          {leaves.length === 0 ? (
            <p className="hr-empty">No leave requests yet.</p>
          ) : (
            <ul className="hr-leaves">
              {leaves.slice(0, 4).map((leave) => (
                <li key={`${leave.id ?? leave.startDate}-${leave.employee?.id ?? ""}`}>
                  <button type="button" onClick={() => navigate("/leave")}>
                    <strong>{leave.employee?.fullName || "Employee"}</strong>
                    <span>{leave.startDate} to {leave.endDate}</span>
                    <em>{leave.status}</em>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </article>

        <article className="hr-card">
          <div className="hr-card-title">
            <h3>Upcoming holidays</h3>
          </div>
          <ul className="hr-holidays">
            {holidays.map((holiday) => (
              <li key={holiday.date}>
                <strong>{holiday.name}</strong>
                <span>{holiday.date}</span>
              </li>
            ))}
          </ul>
          <div className="hr-calendar" aria-label="This month">
            {["S", "M", "T", "W", "T", "F", "S"].map((label, index) => (
              <span key={`${label}-${index}`} className="hr-cal-label">{label}</span>
            ))}
            {cells.map((day, index) => (
              <span
                key={`${day ?? "empty"}-${index}`}
                className={
                  day === now.getDate()
                    ? "hr-cal-today"
                    : day && holidayDays.has(day)
                      ? "hr-cal-holiday"
                      : undefined
                }
              >
                {day ?? ""}
              </span>
            ))}
          </div>
        </article>
      </section>
    </div>
  );
}
