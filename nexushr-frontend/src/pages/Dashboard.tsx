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
  checkIn,
  checkOut,
  getAttendanceAudit,
  getAttendanceByDate,
  getAttendanceRange,
  getEmployeeAttendance,
  getLeaves,
  type AttendanceAuditEntry,
  clockedWorkMinutes,
  monthDateKeys,
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
  const match = String(value ?? "").match(/(\d{2}):(\d{2})/);
  return match ? `${match[1]}:${match[2]}` : "—";
}

function punchPairs(entries: AttendanceAuditEntry[], employeeId: number, date: string, record?: AttendanceRecord) {
  const pairs: Array<{ checkIn: string; checkOut?: string }> = [];
  const relevant = asList<AttendanceAuditEntry>(entries)
    .filter((entry) => entry.employeeId === employeeId
      && (entry.attendanceDate ?? "").slice(0, 10) === date
      && (entry.action === "CHECK_IN" || entry.action === "CHECK_OUT"))
    .sort((a, b) => (a.createdAt ?? "").localeCompare(b.createdAt ?? ""));
  for (const entry of relevant) {
    const time = clock(entry.detail) === "—" ? clock(entry.createdAt) : clock(entry.detail);
    if (entry.action === "CHECK_IN") {
      pairs.push({ checkIn: time });
    } else if (pairs.length > 0 && !pairs[pairs.length - 1].checkOut) {
      pairs[pairs.length - 1].checkOut = time;
    }
  }
  if (pairs.length === 0 && record?.checkInTime) {
    pairs.push({
      checkIn: clock(record.checkInTime),
      checkOut: record.checkOutTime ? clock(record.checkOutTime) : undefined,
    });
  }
  return pairs;
}

function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return (parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? "");
}

function asList<T>(value: unknown): T[] {
  return Array.isArray(value) ? value : [];
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

  for (const holiday of asList<CompanyHoliday>(extra)) {
    if (!holiday?.holidayDate) {
      continue;
    }
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
  const [recentRows, setRecentRows] = useState<AttendanceRecord[]>([]);
  const [audit, setAudit] = useState<AttendanceAuditEntry[]>([]);
  const [week, setWeek] = useState<Array<{ label: string; present: number }>>([]);
  const [leaves, setLeaves] = useState<LeaveRequest[]>([]);
  const [holidays, setHolidays] = useState<HolidayMark[]>([]);
  const [loading, setLoading] = useState(true);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshTick, setRefreshTick] = useState(0);
  const [lunchEmployeeId, setLunchEmployeeId] = useState("");
  const [punching, setPunching] = useState(false);
  const [punchNote, setPunchNote] = useState("");
  const [personHistory, setPersonHistory] = useState<AttendanceRecord[]>([]);

  useEffect(() => {
    let cancelled = false;
    const today = todayIso();
    const dates = monthDateKeys();
    const from = dates[0];
    const to = dates[dates.length - 1];
    setHistoryLoading(true);

    Promise.all([
      getDashboardSummary().catch(() => null),
      getCurrentEmployee().catch(() => null),
      getEmployees().catch(() => [] as EmployeeResponse[]),
      getAttendanceByDate(today).catch(() => [] as AttendanceRecord[]),
      getLeaves().catch(() => [] as LeaveRequest[]),
      getHolidays().catch(() => [] as CompanyHoliday[]),
    ])
      .then(([dashboard, current, employees, attendance, leaveRows, extraHolidays]) => {
        if (cancelled) {
          return;
        }
        setSummary(dashboard);
        setMe(current);
        setPeople(asList<EmployeeResponse>(employees).filter((employee) => isCompanyMatch(employee.companyCode, company)));
        setTodayRows(asList<AttendanceRecord>(attendance).filter((row) => isCompanyMatch(row.employee?.companyCode, company)));
        setLeaves(asList<LeaveRequest>(leaveRows).filter((leave) => isCompanyMatch(leave.employee?.companyCode, company)));
        setHolidays(upcomingHolidays(asList<CompanyHoliday>(extraHolidays), company));
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

    getAttendanceRange(from, to)
      .catch(() =>
        Promise.all(dates.slice(-7).map((date) => getAttendanceByDate(date).catch(() => [] as AttendanceRecord[])))
          .then((days) => days.flat()),
      )
      .then((history) => {
        if (cancelled) {
          return;
        }
        const monthRows = asList<AttendanceRecord>(history).filter((row) =>
          isCompanyMatch(row.employee?.companyCode, company),
        );
        const byDate = new Map<string, AttendanceRecord[]>();
        for (const row of monthRows) {
          const key = String(row.attendanceDate ?? "").slice(0, 10);
          const bucket = byDate.get(key) ?? [];
          bucket.push(row);
          byDate.set(key, bucket);
        }
        setRecentRows(monthRows.filter((row) => row.checkInTime && row.employee).sort((a, b) =>
          String(b.attendanceDate).localeCompare(String(a.attendanceDate)),
        ));
        setWeek(dates.slice(-7).map((date) => ({
          label: date.slice(8),
          present: (byDate.get(date) ?? []).filter((row) =>
            row.checkInTime
            && row.attendanceStatus !== "INACTIVE",
          ).length,
        })));
      })
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) {
          setHistoryLoading(false);
        }
      });

    getAttendanceAudit()
      .catch(() => [] as AttendanceAuditEntry[])
      .then((punches) => {
        if (!cancelled) {
          setAudit(asList<AttendanceAuditEntry>(punches));
        }
      });

    return () => {
      cancelled = true;
    };
  }, [company, refreshTick]);

  useEffect(() => {
    if (!lunchEmployeeId) {
      setPersonHistory([]);
      return;
    }
    let cancelled = false;
    getEmployeeAttendance(Number(lunchEmployeeId))
      .then((rows) => {
        if (!cancelled) {
          setPersonHistory(rows);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setPersonHistory([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [lunchEmployeeId, refreshTick]);

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
  const holidayDays = new Set(holidays.filter((holiday) => String(holiday.date ?? "").startsWith(today.slice(0, 7))).map((holiday) => Number(String(holiday.date).slice(8))));
  const displayName = me?.fullName || localStorage.getItem("nexushr_user") || "there";
  const lunchPerson = people.find((person) => String(person.id) === lunchEmployeeId);
  const lunchRecord = todayRows.find((row) => String(row.employee?.id) === lunchEmployeeId);
  const lunchOpen = Boolean(
    lunchPerson && !lunchPerson.siteVisitor && lunchRecord?.checkInTime && !lunchRecord.checkOutTime,
  );
  const lunchResume = Boolean(
    lunchPerson && !lunchPerson.siteVisitor && lunchRecord?.checkOutTime,
  );

  async function punchLunch(kind: "out" | "in") {
    if (!lunchPerson) {
      return;
    }
    setPunching(true);
    setPunchNote("");
    try {
      await (kind === "out" ? checkOut(lunchPerson.id) : checkIn(lunchPerson.id));
      setPunchNote(
        kind === "out"
          ? "Lunch check-out saved. Working hours are paused."
          : "Lunch check-in saved. Working hours continue.",
      );
      setRefreshTick((value) => value + 1);
    } catch (err) {
      setPunchNote(
        err instanceof Error
          ? err.message.replace(/^Attendance API failed:\s*/i, "")
          : "Lunch punch could not be saved.",
      );
    } finally {
      setPunching(false);
    }
  }

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

      <section className="hr-card hr-lunch">
        <div className="hr-card-title">
          <h3>Lunch</h3>
        </div>
        <label className="hr-lunch-pick">
          <span>Employee</span>
          <select
            value={lunchEmployeeId}
            onChange={(event) => {
              setLunchEmployeeId(event.target.value);
              setPunchNote("");
            }}
          >
            <option value="">Select an employee</option>
            {people.filter((person) => person.active).map((person) => (
              <option key={person.id} value={person.id}>
                {person.fullName} - {person.employeeCode}
              </option>
            ))}
          </select>
        </label>
        <div className="attendance-actions">
          <button
            className="secondary-button"
            type="button"
            disabled={punching || !lunchOpen}
            onClick={() => void punchLunch("out")}
          >
            Lunch Check Out
          </button>
          <button
            className="secondary-button"
            type="button"
            disabled={punching || !lunchResume}
            onClick={() => void punchLunch("in")}
          >
            Lunch Check In
          </button>
        </div>
        {punchNote && <p className="hr-empty">{punchNote}</p>}
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
            <button type="button" onClick={() => navigate("/attendance")}>
              {todayRows.filter((row) => row.yellowFlag).length} inactive warnings
            </button>
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
                  <button type="button" onClick={() => row.employee?.id && navigate(`/employees/${row.employee.id}`)}>
                    <span>{initials(row.employee?.fullName || "Employee")}</span>
                    <strong>{row.employee?.fullName || "Employee"}</strong>
                    <em>
                      {punchPairs(audit, row.employee?.id ?? 0, today, row).map((pair) =>
                        `${pair.checkIn}–${pair.checkOut ?? "in"}`).join(", ")}
                      {" · "}
                      {Math.floor(clockedWorkMinutes(row) / 60)}h {clockedWorkMinutes(row) % 60}m
                      {row.yellowFlag ? " · warning" : ""}
                    </em>
                  </button>
                </li>
              ))}
            </ul>
          )}
          <p className="hr-empty">A day is 9 hours from check-in: 8 hours of work and 1 flexible lunch hour. Three idle warnings cut half a day.</p>
        </article>

        <article className="hr-card hr-actions">
          <h3>Open</h3>
          <button type="button" onClick={() => navigate("/attendance")}>Attendance</button>
          <button type="button" onClick={() => navigate("/leave")}>Leave</button>
          <button type="button" onClick={() => navigate("/payroll")}>Payroll</button>
          <button type="button" onClick={() => navigate("/employees?status=Active")}>Active</button>
          <button type="button" onClick={() => navigate("/employees?status=Onboarding")}>Onboarding</button>
          <button type="button" onClick={() => navigate("/employees?status=Offboarding")}>Offboarding</button>
        </article>
      </section>

      {(missingCheckIn > 0 || missingCheckOut > 0) && (
        <section className="hr-card">
          <div className="hr-card-title">
            <h3>Check-in and check-out missing</h3>
            <button type="button" onClick={() => navigate("/attendance")}>Open attendance</button>
          </div>
          <ul className="hr-leaves">
            {people.filter((employee) => employee.active && !todayRows.some((row) => row.employee?.id === employee.id && row.checkInTime)).slice(0, 6).map((employee) => (
              <li key={employee.id}>
                <button type="button" onClick={() => navigate(`/employees/${employee.id}`)}>
                  <strong>{employee.fullName}</strong>
                  <span>Check-in is missing</span>
                </button>
              </li>
            ))}
            {todayRows.filter((row) => row.checkInTime && !row.checkOutTime && row.employee).slice(0, 6).map((row) => (
              <li key={`out-${row.id}`}>
                <button type="button" onClick={() => navigate(`/employees/${row.employee.id}`)}>
                  <strong>{row.employee.fullName}</strong>
                  <span>Checked in {clock(row.checkInTime)}. Check-out is missing.</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="hr-card">
        <div className="hr-card-title">
          <h3>{lunchPerson ? `${lunchPerson.fullName} · last 30 days` : "Attendance history · last 30 days"}</h3>
          <button type="button" onClick={() => navigate("/attendance")}>Open attendance</button>
        </div>
        <p className="hr-empty">Check-in and check-out for each day. Working hours, and salary, are calculated from these times.</p>
        <div className="hr-history-table">
          <table className="employees-table">
            <thead>
              <tr>
                <th>Date</th>
                {lunchPerson ? null : <th>Employee</th>}
                <th>Check in</th>
                <th>Check out</th>
                <th>Working hours</th>
              </tr>
            </thead>
            <tbody>
              {!lunchPerson && historyLoading && recentRows.length === 0 ? (
                <tr>
                  <td colSpan={5}>Loading the last 30 days...</td>
                </tr>
              ) : lunchPerson ? monthDateKeys().slice().reverse().map((date) => {
                const row = personHistory.find((item) => item.attendanceDate?.slice(0, 10) === date);
                return (
                  <tr key={date}>
                    <td>{date}</td>
                    <td>{row?.checkInTime ? clock(row.checkInTime) : "—"}</td>
                    <td>{row?.checkInTime && !row.checkOutTime ? "Still in" : row?.checkOutTime ? clock(row.checkOutTime) : "—"}</td>
                    <td>{row?.checkInTime ? `${Math.floor(clockedWorkMinutes(row) / 60)}h ${clockedWorkMinutes(row) % 60}m` : "No check-in"}</td>
                  </tr>
                );
              }) : recentRows.length === 0 ? (
                <tr>
                  <td colSpan={5}>No attendance in the last 30 days. Select an employee above to see one person's days, including days with no check-in.</td>
                </tr>
              ) : recentRows.map((row) => (
                <tr key={row.id}>
                  <td>{String(row.attendanceDate ?? "").slice(0, 10)}</td>
                  <td>{row.employee?.fullName ?? "Employee"}</td>
                  <td>{clock(row.checkInTime)}</td>
                  <td>{row.checkOutTime ? clock(row.checkOutTime) : "Still in"}</td>
                  <td>{Math.floor(clockedWorkMinutes(row) / 60)}h {clockedWorkMinutes(row) % 60}m</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
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
