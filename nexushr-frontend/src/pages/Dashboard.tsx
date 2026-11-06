import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  downloadPayslipPdf,
  emailPayslip,
  getCurrentEmployee,
  getDashboardSummary,
  getEmployees,
  getHolidays,
  getPayrollRecords,
  type PayrollRecord,
  type CompanyHoliday,
  type DashboardSummary,
  type EmployeeResponse,
} from "../api";
import {
  checkIn,
  checkOut,
  updateLeaveStatus,
  getAttendanceByDate,
  getAttendanceRange,
  getEmployeeAttendance,
  getLeaves,
  clockedWorkMinutes,
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

function kolkataClock(now = new Date()) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Kolkata",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(now);
  const hour = Number(parts.find((part) => part.type === "hour")?.value ?? "0");
  const minute = Number(parts.find((part) => part.type === "minute")?.value ?? "0");
  return { hour, minute };
}

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

function formatTimer(totalSeconds: number) {
  const safe = Math.max(0, totalSeconds);
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  const seconds = safe % 60;
  return `${hours}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function isoDate(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

function spanDates(kind: "week" | "lastWeek") {
  const today = new Date();
  const weekday = today.getDay();
  const mondayOffset = weekday === 0 ? 6 : weekday - 1;
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate() - mondayOffset);
  if (kind === "lastWeek") {
    start.setDate(start.getDate() - 7);
  }
  return Array.from({ length: 6 }, (_, index) => {
    const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + index);
    return isoDate(date);
  });
}

function displayDate(value?: string) {
  if (!value) {
    return "—";
  }
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
  const [rangeRows, setRangeRows] = useState<AttendanceRecord[]>([]);
  const [rosterView, setRosterView] = useState<"date" | "week" | "lastWeek" | "month">("date");
  const [rosterOpen, setRosterOpen] = useState(false);
  const [rosterDate, setRosterDate] = useState(todayIso);
  const [clockNow, setClockNow] = useState(() => new Date());
  const [payrollRows, setPayrollRows] = useState<PayrollRecord[]>([]);
  const [payslipId, setPayslipId] = useState("");
  const [payslipBusy, setPayslipBusy] = useState<"" | "download" | "email">("");
  const [payslipNote, setPayslipNote] = useState("");

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
      getPayrollRecords().catch(() => [] as PayrollRecord[]),
    ])
      .then(([dashboard, current, employees, attendance, leaveRows, extraHolidays, payroll]) => {
        if (cancelled) {
          return;
        }
        setSummary(dashboard);
        setMe(current);
        setPeople(asList<EmployeeResponse>(employees).filter((employee) => isCompanyMatch(employee.companyCode, company)));
        setTodayRows(asList<AttendanceRecord>(attendance).filter((row) => isCompanyMatch(row.employee?.companyCode, company)));
        setLeaves(asList<LeaveRequest>(leaveRows).filter((leave) => isCompanyMatch(leave.employee?.companyCode, company)));
        setHolidays(upcomingHolidays(asList<CompanyHoliday>(extraHolidays), company));
        const slips = asList<PayrollRecord>(payroll)
          .filter((row) => row?.employee && isCompanyMatch(row.employee.companyCode, company))
          .sort((a, b) => b.payMonth.localeCompare(a.payMonth));
        setPayrollRows(slips);
        setPayslipId((currentId) => currentId || (slips[0] ? String(slips[0].id) : ""));
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
        setRangeRows(monthRows);
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

  useEffect(() => {
    const timer = window.setInterval(() => setClockNow(new Date()), 1000);
    return () => window.clearInterval(timer);
  }, []);

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
    leave.status === "APPROVED" && String(leave.startDate ?? "") <= today && String(leave.endDate ?? "") >= today,
  );
  const pending = leaves.filter((leave) => leave.status === "PENDING");
  const nowForMissing = new Date();
  const kolkata = kolkataClock(nowForMissing);
  const missingCheckInDue = kolkata.hour > 11 || (kolkata.hour === 11 && kolkata.minute >= 0);
  const checkoutMissedDue = kolkata.hour >= 23;
  const activePeople = people.filter((person) => person.active);
  const onLeaveIds = new Set(onLeave.map((leave) => leave.employee?.id).filter((id): id is number => typeof id === "number"));
  const missedCheckInPeople = missingCheckInDue
    ? activePeople.filter((person) =>
        !onLeaveIds.has(person.id)
        && !todayRows.some((row) => row.employee?.id === person.id && row.checkInTime),
      )
    : [];
  const missedCheckOutPeople = todayRows.filter((row) =>
    row.checkInTime
    && !row.checkOutTime
    && row.employee
    && row.attendanceStatus !== "INACTIVE",
  );
  const noLunchLogin = todayRows.filter((row) =>
    row.checkInTime
    && row.employee
    && !row.employee.siteVisitor
    && row.attendanceStatus !== "INACTIVE"
    && (row.breakMinutes ?? 0) < 1,
  );
  const windowsPunch = isWindowsComputer();
  const late = todayRows.filter((row) => row.lateWarning || (row.lateArrivalMinutes ?? 0) > 0).length;
  const shownPeople = rosterOpen ? activePeople : activePeople.slice(0, 6);
  const onboardingCount = activePeople.filter((person) =>
    person.lifecycleStatus === "PENDING_ONBOARDING" || person.lifecycleStatus === "PENDING_APPROVAL",
  ).length;
  const offboardingCount = people.filter((person) => person.lifecycleStatus === "OFFBOARDING").length;
  const offboardedCount = people.filter((person) => person.lifecycleStatus === "OFFBOARDED").length;
  const waiting = [...pending].sort((a, b) => String(a.startDate ?? "").localeCompare(String(b.startDate ?? "")));
  const idleWarnings = todayRows.filter((row) => row.yellowFlag).length;
  const maxPresent = Math.max(1, ...week.map((day) => day.present));
  const now = new Date();
  const displayName = me?.fullName || localStorage.getItem("nexushr_user") || "there";
  const myRecord = todayRows.find((row) => row.employee?.id === me?.id);
  const selfOpen = Boolean(myRecord?.checkInTime && !myRecord?.checkOutTime);
  const selfOut = Boolean(myRecord?.checkOutTime);
  const lunchStarted = selfOut ? new Date(String(myRecord?.checkOutTime)).getTime() : 0;
  const lunchSeconds = lunchStarted ? Math.max(0, Math.floor((clockNow.getTime() - lunchStarted) / 1000)) : 0;
  const workSeconds = (() => {
    if (!myRecord?.checkInTime) {
      return 0;
    }
    const start = new Date(myRecord.checkInTime).getTime();
    const end = myRecord.checkOutTime ? new Date(myRecord.checkOutTime).getTime() : clockNow.getTime();
    const breakMs = (myRecord.breakMinutes ?? 0) * 60_000;
    return Math.max(0, Math.floor((end - start - breakMs) / 1000));
  })();
  const lunchPerson = people.find((person) => String(person.id) === lunchEmployeeId);
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

  async function punchSelf(kind: "out" | "in") {
    if (!me) {
      return;
    }
    setPunching(true);
    setPunchNote("");
    try {
      await (kind === "out" ? checkOut(me.id) : checkIn(me.id));
      setPunchNote(kind === "out" ? "Checked out. The lunch hour can be any time." : "Checked in. Working hours continue.");
      setRefreshTick((value) => value + 1);
    } catch (err) {
      setPunchNote(
        err instanceof Error
          ? err.message.replace(/^Attendance API failed:\s*/i, "")
          : "The punch could not be saved.",
      );
    } finally {
      setPunching(false);
    }
  }

  const selectedSlip = payrollRows.find((row) => String(row.id) === payslipId) ?? null;

  async function downloadSelectedPayslip() {
    if (!selectedSlip) {
      setPayslipNote("No payslip yet. Calculate pay on the Payroll page first.");
      return;
    }
    setPayslipBusy("download");
    setPayslipNote("");
    try {
      const pdf = await downloadPayslipPdf(selectedSlip.id);
      const url = window.URL.createObjectURL(pdf);
      const link = document.createElement("a");
      const companyPrefix = (selectedSlip.employee.companyCode || company).replace(/[^A-Za-z0-9]+/g, "");
      link.href = url;
      link.download = `${companyPrefix}-Payslip-${selectedSlip.employee.employeeCode}-${selectedSlip.payMonth}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      setPayslipNote("Payslip downloaded.");
    } catch (err) {
      setPayslipNote(err instanceof Error ? err.message : "Unable to download payslip.");
    } finally {
      setPayslipBusy("");
    }
  }

  async function emailSelectedPayslip() {
    if (!selectedSlip) {
      setPayslipNote("No payslip yet. Calculate pay on the Payroll page first.");
      return;
    }
    setPayslipBusy("email");
    setPayslipNote("");
    try {
      await emailPayslip(selectedSlip.id);
      setPayslipNote(`Payslip emailed only to ${selectedSlip.employee.fullName}.`);
    } catch (err) {
      setPayslipNote(err instanceof Error ? err.message : "Unable to email payslip.");
    } finally {
      setPayslipBusy("");
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
          <em>{company === "GORLE" ? "Gorle Group" : "CYOND"}</em>
        </button>
        <button type="button" onClick={() => navigate("/attendance")}>
          <span>Present</span>
          <strong>{present.length}</strong>
          <em>{people.length ? `${Math.round((present.length / people.length) * 100)}% of headcount` : "checked in"}</em>
        </button>
        <button type="button" onClick={() => navigate("/attendance")}>
          <span>On site</span>
          <strong>{todayRows.filter((row) => row.checkInTime && row.employee?.siteVisitor).length}</strong>
          <em>site check-in</em>
        </button>
        <button type="button" onClick={viewAllToday}>
          <span>Not checked in</span>
          <strong>{Math.max(0, activePeople.length - present.length)}</strong>
          <em>{missingCheckInDue ? "after 11:00" : "before 11:00"}</em>
        </button>
        <button type="button" onClick={() => navigate("/leave")}>
          <span>On leave</span>
          <strong>{onLeave.length}</strong>
          <em>approved today</em>
        </button>
        <button type="button" onClick={() => navigate("/attendance")}>
          <span>Late</span>
          <strong>{late}</strong>
          <em>after 10:15</em>
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
            <ul className="hr-approvals">
              {waiting.slice(0, 4).map((leave) => {
                const started = leave.startDate ? new Date(`${leave.startDate.slice(0, 10)}T00:00:00`) : null;
                const waited = started ? Math.max(0, Math.round((Date.now() - started.getTime()) / 86400000)) : 0;
                return (
                  <li key={leave.id}>
                    <span>
                      Leave · {leave.employee?.fullName || "Employee"} · {displayDate(leave.startDate)} to {displayDate(leave.endDate)}
                      {waited > 0 ? ` · waiting ${waited} day${waited === 1 ? "" : "s"}` : ""}
                    </span>
                    <button type="button" onClick={() => void updateLeaveStatus(leave.id!, "APPROVED").then(() => setRefreshTick((value) => value + 1))}>Approve</button>
                    <button type="button" onClick={() => {
                      if (window.confirm("Reject this leave request?")) {
                        void updateLeaveStatus(leave.id!, "REJECTED").then(() => setRefreshTick((value) => value + 1));
                      }
                    }}>Reject</button>
                  </li>
                );
              })}
            </ul>
          )}
        </article>
        <article className="hr-card">
          <div className="hr-card-title">
            <h3>Exceptions today</h3>
            <button type="button" onClick={viewAllToday}>View all</button>
          </div>
          <div className="hr-miss-lists">
            <div>
              <strong>No check-in {!missingCheckInDue ? "" : `(${missedCheckInPeople.length})`}</strong>
              {!missingCheckInDue ? (
                <p className="hr-empty">Shown after 11:00.</p>
              ) : missedCheckInPeople.length === 0 ? (
                <p className="hr-empty">Nobody missed check-in.</p>
              ) : (
                <ul>
                  {missedCheckInPeople.map((person) => (
                    <li key={person.id}>
                      <button type="button" onClick={() => openEmployeeMonth(person.id)}>{person.fullName}</button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div>
              <strong>{checkoutMissedDue ? "Check-out missed" : "Not checked out"} ({missedCheckOutPeople.length})</strong>
              {missedCheckOutPeople.length === 0 ? (
                <p className="hr-empty">{checkoutMissedDue ? "Nobody missed check-out." : "Everyone who checked in has checked out."}</p>
              ) : (
                <ul>
                  {missedCheckOutPeople.map((row) => (
                    <li key={row.employee?.id ?? row.id}>
                      <button type="button" onClick={() => openEmployeeMonth(row.employee?.id)}>{row.employee?.fullName || "Employee"}</button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div>
              <strong>No lunch login ({noLunchLogin.length})</strong>
              {noLunchLogin.length === 0 ? (
                <p className="hr-empty">Everyone who checked in has logged back from lunch.</p>
              ) : (
                <ul>
                  {noLunchLogin.map((row) => (
                    <li key={`lunch-${row.employee?.id ?? row.id}`}>
                      <button type="button" onClick={() => openEmployeeMonth(row.employee?.id)}>{row.employee?.fullName || "Employee"}</button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
          <p className="hr-exception-line">
            <button type="button" onClick={() => navigate("/attendance")}>Idle warnings {idleWarnings}</button>
            <button type="button" onClick={() => navigate("/attendance")}>Late {late}</button>
          </p>
        </article>
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
          <p className="hr-exception-line">
            <button type="button" onClick={() => navigate("/employees?status=Onboarding")}>Joining {onboardingCount}</button>
            <button type="button" onClick={() => navigate("/employees?status=Offboarding")}>Leaving {offboardingCount}</button>
            <button type="button" onClick={() => navigate("/employees?status=Offboarded")}>Already left {offboardedCount}</button>
          </p>
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

      <section className="hr-card hr-lunch">
        <div>
          <strong>Check in and check out</strong>
          <p className="hr-empty">The lunch hour is flexible. Check out when you leave, and check in when you are back. It can be any hour.</p>
        </div>
        <div className="hr-timer" aria-live="polite">
          <span>{selfOut ? "Lunch timer" : "Work timer"}</span>
          <strong>{formatTimer(selfOut ? lunchSeconds : workSeconds)}</strong>
          <em>{selfOut ? "1 hour is paid. Extra time stays a break." : "1 flexible hour"}</em>
        </div>
        <div className="attendance-actions">
          <button className="primary-button" type="button" disabled={punching || !windowsPunch || !me || selfOpen} onClick={() => void punchSelf("in")}>
            Check in
          </button>
          <button className="secondary-button" type="button" disabled={punching || !windowsPunch || !me || !selfOpen} onClick={() => void punchSelf("out")}>
            Check out
          </button>
        </div>
        {!windowsPunch && <p className="hr-empty">Check in and check out work only on a Windows computer.</p>}
        {punchNote && <p className="hr-empty">{punchNote}</p>}
      </section>

      <section className="hr-card hr-payslip">
        <div>
          <strong>Payslip</strong>
          <p className="hr-empty">Download a payslip, or email that same payslip only to the employee.</p>
        </div>
        <label className="hr-date-pick">
          Employee
          <select value={payslipId} onChange={(event) => setPayslipId(event.target.value)}>
            {payrollRows.length === 0 && <option value="">No payslip yet</option>}
            {payrollRows.map((row) => (
              <option key={row.id} value={row.id}>
                {row.employee.fullName} · {row.payMonth}
              </option>
            ))}
          </select>
        </label>
        <div className="attendance-actions">
          <button className="primary-button" type="button" disabled={payslipBusy !== ""} onClick={() => void downloadSelectedPayslip()}>
            {payslipBusy === "download" ? "Downloading..." : "Download payslip"}
          </button>
          <button className="secondary-button" type="button" disabled={payslipBusy !== ""} onClick={() => void emailSelectedPayslip()}>
            {payslipBusy === "email" ? "Emailing..." : "Email payslip"}
          </button>
        </div>
        {payslipNote && <p className="hr-empty">{payslipNote}</p>}
      </section>

      <section className="hr-card" id="employee-month">
        <div className="hr-card-title">
          <h3>{lunchPerson ? `${lunchPerson.fullName} · last 30 days` : "All employees"}</h3>
          {lunchPerson ? (
            <button type="button" onClick={viewAllToday}>View all</button>
          ) : (
            <div className="hr-view-tabs">
              <button type="button" onClick={() => setRosterOpen((open) => !open)}>{rosterOpen ? "View less" : "View all"}</button>
              <button type="button" className={rosterView === "date" ? "active" : ""} onClick={() => setRosterView("date")}>Date</button>
              <button type="button" className={rosterView === "week" ? "active" : ""} onClick={() => setRosterView("week")}>Week</button>
              <button type="button" className={rosterView === "lastWeek" ? "active" : ""} onClick={() => setRosterView("lastWeek")}>Last week</button>
              <button type="button" className={rosterView === "month" ? "active" : ""} onClick={() => setRosterView("month")}>Month</button>
            </div>
          )}
        </div>
        {lunchPerson ? (
          <p className="hr-empty">Only {lunchPerson.fullName}'s check-in, check-out, and working hours. View all returns to everyone.</p>
        ) : (
          <p className="hr-empty">
            {rosterOpen ? `Showing all ${activePeople.length}.` : `Showing ${Math.min(6, activePeople.length)} of ${activePeople.length}.`} Click a name to open that person's month.
          </p>
        )}
        {!lunchPerson && rosterView === "date" && (
          <label className="hr-date-pick">
            Date
            <input type="date" value={rosterDate} onChange={(event) => setRosterDate(event.target.value)} />
          </label>
        )}
        {!lunchPerson && rosterView === "date" ? (
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
              {shownPeople.length === 0 ? (
                <tr>
                  <td colSpan={4}>No employees for this company.</td>
                </tr>
              ) : shownPeople.map((person) => {
                const row = (rosterDate === today ? todayRows : rangeRows).find((item) => item.employee?.id === person.id && String(item.attendanceDate ?? "").slice(0, 10) === rosterDate);
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
        {!lunchPerson && (rosterView === "week" || rosterView === "lastWeek") ? (
        <div className="hr-history-table">
          <table className="employees-table">
            <thead>
              <tr>
                <th>Employee</th>
                {spanDates(rosterView).map((date) => <th key={date}>{displayDate(date).slice(0, 6)}</th>)}
              </tr>
            </thead>
            <tbody>
              {shownPeople.map((person) => (
                <tr key={person.id}>
                  <td>
                    <button type="button" className="panel-link" onClick={() => openEmployeeMonth(person.id)}>{person.fullName}</button>
                  </td>
                  {spanDates(rosterView).map((date) => {
                    const row = rangeRows.find((item) => item.employee?.id === person.id && String(item.attendanceDate ?? "").slice(0, 10) === date);
                    return <td key={date}>{row?.checkInTime ? `${Math.floor(clockedWorkMinutes(row) / 60)}h ${clockedWorkMinutes(row) % 60}m` : "—"}</td>;
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        ) : null}
        {!lunchPerson && rosterView === "month" ? (
        <div className="hr-history-table">
          <table className="employees-table">
            <thead>
              <tr>
                <th>Employee</th>
                <th>Days present</th>
                <th>Hours</th>
              </tr>
            </thead>
            <tbody>
              {shownPeople.map((person) => {
                const rows = rangeRows.filter((item) => item.employee?.id === person.id && item.checkInTime);
                const minutes = rows.reduce((sum, row) => sum + clockedWorkMinutes(row), 0);
                return (
                  <tr key={person.id}>
                    <td>
                      <button type="button" className="panel-link" onClick={() => openEmployeeMonth(person.id)}>{person.fullName}</button>
                    </td>
                    <td>{rows.length}</td>
                    <td>{Math.floor(minutes / 60)}h {minutes % 60}m</td>
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
