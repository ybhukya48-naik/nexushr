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
  getAttendanceByDate,
  getAttendanceRange,
  getEmployeeAttendance,
  getLeaves,
  clockedWorkMinutes,
  isCheckoutMissing,
  isWindowsComputer,
  monthDateKeys,
  shownCheckOut,
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

function displayDate(value: string) {
  const parsed = new Date(`${value.slice(0, 10)}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
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
  const [personHistoryLoading, setPersonHistoryLoading] = useState(false);
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
        setWeek(dates.slice(-7).map((date) => ({
          label: date.slice(8),
          present: (byDate.get(date) ?? []).filter((row) =>
            row.checkInTime
            && row.attendanceStatus !== "INACTIVE",
          ).length,
        })));
      })
      .catch(() => undefined);

    return () => {
      cancelled = true;
    };
  }, [company, refreshTick]);

  useEffect(() => {
    if (!lunchEmployeeId) {
      setPersonHistory([]);
      setPersonHistoryLoading(false);
      return;
    }
    let cancelled = false;
    setPersonHistoryLoading(true);
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
      })
      .finally(() => {
        if (!cancelled) {
          setPersonHistoryLoading(false);
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
  const nowForMissing = new Date();
  const missingCheckInDue = nowForMissing.getHours() > 11 || (nowForMissing.getHours() === 11 && nowForMissing.getMinutes() >= 0);
  const missingCheckIn = missingCheckInDue
    ? people.filter((employee) =>
        employee.active
        && !todayRows.some((row) => row.employee?.id === employee.id && row.checkInTime),
      ).length
    : 0;
  const missingCheckOut = todayRows.filter((row) => isCheckoutMissing(row)).length;
  const windowsPunch = isWindowsComputer();
  const late = todayRows.filter((row) => row.lateWarning || (row.lateArrivalMinutes ?? 0) > 0).length;
  const activePeople = people.filter((person) => person.active);
  const onboardingCount = activePeople.filter((person) =>
    person.lifecycleStatus === "PENDING_ONBOARDING" || person.lifecycleStatus === "PENDING_APPROVAL",
  ).length;
  const offboardingCount = people.filter((person) => person.lifecycleStatus === "OFFBOARDING").length;
  const offboardedCount = people.filter((person) => person.lifecycleStatus === "OFFBOARDED").length;
  const waiting = [...pending].sort((a, b) => a.startDate.localeCompare(b.startDate));
  const idleWarnings = todayRows.filter((row) => row.yellowFlag).length;
  const maxPresent = Math.max(1, ...week.map((day) => day.present));
  const now = new Date();
  const displayName = me?.fullName || localStorage.getItem("nexushr_user") || "there";
  const lunchPerson = people.find((person) => String(person.id) === lunchEmployeeId);
  const lunchRecord = todayRows.find((row) => String(row.employee?.id) === lunchEmployeeId);
  const lunchOpen = Boolean(
    lunchPerson && !lunchPerson.siteVisitor && lunchRecord?.checkInTime && !lunchRecord.checkOutTime,
  );
  const lunchResume = Boolean(
    lunchPerson && !lunchPerson.siteVisitor && lunchRecord?.checkOutTime,
  );

  function viewAllToday() {
    setLunchEmployeeId("");
    setPunchNote("");
    window.setTimeout(() => {
      document.getElementById("employee-month")?.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 0);
  }

  function openEmployeeMonth(employeeId?: number) {
    if (!employeeId) {
      return;
    }
    setLunchEmployeeId(String(employeeId));
    setPunchNote("");
    window.setTimeout(() => {
      document.getElementById("employee-month")?.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 0);
  }

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
          <p>Operations · {company === "GORLE" ? "Gorle Group" : "CYOND"} · as of {now.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })}</p>
          <h2 title="A day is 9 hours from check-in: 8 hours of work and 1 flexible lunch hour. Three idle warnings cut half a day.">
            Good day, {displayName}
          </h2>
        </div>
        <time dateTime={today}>
          {now.toLocaleDateString("en-IN", { weekday: "short", day: "2-digit", month: "short", year: "numeric" })}
        </time>
      </header>

      <section className="hr-kpis">
        <button type="button" onClick={() => navigate("/employees")}>
          <span>Headcount</span>
          <strong>{people.length || summary?.totalEmployees || 0}</strong>
        </button>
        <button type="button" onClick={() => navigate("/attendance")}>
          <span>Present</span>
          <strong>{present.length}</strong>
        </button>
        <button type="button" onClick={() => navigate("/attendance")}>
          <span>On site</span>
          <strong>{todayRows.filter((row) => row.checkInTime && row.employee?.siteVisitor).length}</strong>
        </button>
        <button type="button" onClick={viewAllToday}>
          <span>Not checked in</span>
          <strong>{Math.max(0, activePeople.length - present.length)}</strong>
        </button>
        <button type="button" onClick={() => navigate("/leave")}>
          <span>On leave</span>
          <strong>{onLeave.length}</strong>
        </button>
        <button type="button" onClick={() => navigate("/attendance")}>
          <span>Late</span>
          <strong>{late}</strong>
        </button>
      </section>

      <section className="hr-split">
        <article className="hr-card">
          <div className="hr-card-title">
            <h3>Approvals waiting</h3>
            <button type="button" onClick={() => navigate("/leave")}>Open leave</button>
          </div>
          {waiting.length === 0 ? (
            <p className="hr-empty">No leave is waiting.</p>
          ) : (
            <ul className="hr-leaves">
              {waiting.slice(0, 5).map((leave) => (
                <li key={leave.id}>
                  <button type="button" onClick={() => navigate("/leave")}>
                    <strong>{leave.employee?.fullName || "Employee"}</strong>
                    <span>
                      {displayDate(leave.startDate)} to {displayDate(leave.endDate)}
                      {leave.endDate < today ? " · dates have passed" : " · Pending"}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </article>
        <article className="hr-card">
          <div className="hr-card-title">
            <h3>Exceptions today</h3>
            <button type="button" onClick={viewAllToday}>View all</button>
          </div>
          <ul className="hr-leaves">
            <li><button type="button" onClick={() => navigate("/attendance")}><strong>{missingCheckIn}</strong><span>No check-in after 11:00</span></button></li>
            <li><button type="button" onClick={() => navigate("/attendance")}><strong>{missingCheckOut}</strong><span>Check-out missed</span></button></li>
            <li><button type="button" onClick={() => navigate("/attendance")}><strong>{idleWarnings}</strong><span>Idle warnings</span></button></li>
            <li><button type="button" onClick={() => navigate("/attendance")}><strong>{late}</strong><span>Late arrivals</span></button></li>
          </ul>
        </article>
      </section>

      <section className="hr-card hr-lunch">
        <div className="hr-card-title">
          <h3>Lunch</h3>
          <p className="hr-empty">Start lunch pauses hours. End lunch continues them.</p>
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
            disabled={punching || !windowsPunch || !lunchOpen}
            onClick={() => void punchLunch("out")}
          >
            Start lunch
          </button>
          <button
            className="secondary-button"
            type="button"
            disabled={punching || !windowsPunch || !lunchResume}
            onClick={() => void punchLunch("in")}
          >
            End lunch
          </button>
        </div>
        {!windowsPunch && <p className="hr-empty">Check in and check out work only on a Windows computer.</p>}
        {punchNote && <p className="hr-empty">{punchNote}</p>}
      </section>

      <section className="hr-ops">
        <article className="hr-card">
          <div className="hr-card-title">
            <h3>Attendance this week</h3>
            <button type="button" onClick={() => navigate("/attendance")}>Open attendance</button>
          </div>
          <div className="hr-bars" aria-label="Present employees for the last seven days">
            {week.map((day) => (
              <button key={day.label} type="button" onClick={() => navigate("/attendance")}>
                <em>{day.present}</em>
                <span style={{ height: `${Math.max(8, (day.present / maxPresent) * 100)}%` }}></span>
                <small>{day.label}</small>
              </button>
            ))}
          </div>
        </article>
        <article className="hr-card">
          <div className="hr-card-title">
            <h3>Onboarding and exits</h3>
            <button type="button" onClick={() => navigate("/employees?status=Onboarding")}>Open directory</button>
          </div>
          <ul className="hr-leaves">
            <li><button type="button" onClick={() => navigate("/employees?status=Onboarding")}><strong>{onboardingCount}</strong><span>Joining</span></button></li>
            <li><button type="button" onClick={() => navigate("/employees?status=Offboarding")}><strong>{offboardingCount}</strong><span>Leaving</span></button></li>
            <li><button type="button" onClick={() => navigate("/employees?status=Offboarded")}><strong>{offboardedCount}</strong><span>Already left</span></button></li>
          </ul>
        </article>
        <article className="hr-card">
          <div className="hr-card-title">
            <h3>Upcoming</h3>
          </div>
          {holidays.length === 0 ? (
            <p className="hr-empty">No holidays are saved yet.</p>
          ) : (
            <ul className="hr-holidays">
              {holidays.slice(0, 4).map((holiday) => (
                <li key={holiday.date}>
                  <strong>{holiday.name}</strong>
                  <span>{displayDate(holiday.date)}</span>
                </li>
              ))}
            </ul>
          )}
        </article>
      </section>

      <section className="hr-card" id="employee-month">
        <div className="hr-card-title">
          <h3>{lunchPerson ? `${lunchPerson.fullName} · last 30 days` : "All employees today"}</h3>
          {lunchPerson ? (
            <button type="button" onClick={viewAllToday}>View all</button>
          ) : (
            <button type="button" onClick={() => navigate("/attendance")}>View attendance</button>
          )}
        </div>
        {lunchPerson ? (
          <p className="hr-empty">Only {lunchPerson.fullName}'s check-in, check-out, and working hours. View all returns to everyone for today.</p>
        ) : (
          <p className="hr-empty">{present.length} present today. Click a name to open that employee's month.</p>
        )}
        {!lunchPerson ? (
        <div className="hr-history-table">
          <table className="employees-table">
            <thead>
              <tr>
                <th>Employee</th>
                <th>Check in</th>
                <th>Check out</th>
                <th>Working hours</th>
              </tr>
            </thead>
            <tbody>
              {people.filter((person) => person.active).length === 0 ? (
                <tr>
                  <td colSpan={4}>No employees for this company.</td>
                </tr>
              ) : people.filter((person) => person.active).map((person) => {
                const row = todayRows.find((item) => item.employee?.id === person.id);
                const checkOut = shownCheckOut(row);
                return (
                  <tr key={person.id}>
                    <td>
                      <button type="button" className="panel-link" onClick={() => openEmployeeMonth(person.id)}>
                        {person.fullName}
                      </button>
                    </td>
                    <td>{row?.checkInTime ? clock(row.checkInTime) : "—"}</td>
                    <td>{checkOut ? clock(checkOut) : row?.checkInTime ? "Still in" : "—"}</td>
                    <td>{row?.checkInTime ? `${Math.floor(clockedWorkMinutes(row) / 60)}h ${clockedWorkMinutes(row) % 60}m` : "No check-in"}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        ) : null}
        {lunchPerson ? (
        <div className="hr-history-table">
          <table className="employees-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Check in</th>
                <th>Check out</th>
                <th>Working hours</th>
              </tr>
            </thead>
            <tbody>
              {personHistoryLoading && personHistory.length === 0 ? (
                <tr>
                  <td colSpan={4}>Loading {lunchPerson.fullName}'s month...</td>
                </tr>
              ) : monthDateKeys().slice().reverse().map((date) => {
                const row = personHistory.find((item) => item.attendanceDate?.slice(0, 10) === date);
                const checkOut = shownCheckOut(row);
                return (
                  <tr key={date}>
                    <td>{displayDate(date)}</td>
                    <td>{row?.checkInTime ? clock(row.checkInTime) : "—"}</td>
                    <td>{checkOut ? clock(checkOut) : row?.checkInTime ? "Still in" : "—"}</td>
                    <td>{row?.checkInTime ? `${Math.floor(clockedWorkMinutes(row) / 60)}h ${clockedWorkMinutes(row) % 60}m` : "No check-in"}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        ) : null}
      </section>

    </div>
  );
}
