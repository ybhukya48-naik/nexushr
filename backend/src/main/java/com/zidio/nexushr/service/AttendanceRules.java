package com.zidio.nexushr.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.util.Set;

/**
 * Office day for CYOND and Gorle Group.
 * Shift starts at 09:30. 09:30–10:15 is the arrival buffer.
 * 10:16 onward is a late arrival.
 * A day is 9 hours from check-in: 8 hours of work and 1 flexible lunch hour.
 * The employee chooses when to take that hour. It is not fixed at 1:00.
 */
public final class AttendanceRules {

    public static final LocalTime SHIFT_START = LocalTime.of(9, 30);
    public static final LocalTime BUFFER_END = LocalTime.of(10, 15);
    public static final LocalTime LUNCH_START = LocalTime.of(13, 0);
    public static final LocalTime LUNCH_END = LocalTime.of(13, 45);
    public static final LocalTime LUNCH_RETURN_DEADLINE = LocalTime.of(14, 0);
    public static final LocalTime LOGOUT_DEADLINE = LocalTime.of(23, 0);

    public static final int WORK_MINUTES = 8 * 60;
    public static final int LUNCH_MINUTES = 60;
    public static final int PRESENCE_MINUTES = 9 * 60;
    public static final int INACTIVE_LIMIT_MINUTES = 15;
    public static final int WARNINGS_BEFORE_PENALTY = 3;
    public static final int LATE_DEDUCT_MINUTES = 2 * 60;
    public static final int HALF_DAY_MINUTES = WORK_MINUTES / 2;

    private static final Set<MonthDay> PUBLIC_HOLIDAYS = Set.of(
            MonthDay.of(1, 26),
            MonthDay.of(8, 15),
            MonthDay.of(10, 2),
            MonthDay.of(12, 25)
    );

    private AttendanceRules() {
    }

    public static boolean isLateArrival(LocalTime time) {
        return time != null && time.isAfter(BUFFER_END);
    }

    public static int lateArrivalMinutes(LocalTime time) {
        if (!isLateArrival(time)) {
            return 0;
        }
        return (int) Duration.between(SHIFT_START, time).toMinutes();
    }

    public static boolean isPublicHoliday(LocalDate date) {
        return date != null && PUBLIC_HOLIDAYS.contains(MonthDay.from(date));
    }

    public static boolean isWorkingDay(LocalDate date, boolean gorle) {
        if (date == null || date.getDayOfWeek() == DayOfWeek.SUNDAY || isPublicHoliday(date)) {
            return false;
        }
        return !(gorle && HoursPayPolicy.isSecondSaturday(date));
    }

    public static int lunchOverlapMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return 0;
        }
        LocalDateTime lunchStart = start.toLocalDate().atTime(LUNCH_START);
        LocalDateTime lunchEnd = start.toLocalDate().atTime(LUNCH_END);
        LocalDateTime from = start.isAfter(lunchStart) ? start : lunchStart;
        LocalDateTime to = end.isBefore(lunchEnd) ? end : lunchEnd;
        if (!to.isAfter(from)) {
            return 0;
        }
        long minutes = Duration.between(from, to).toMinutes();
        return minutes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) minutes;
    }

    public static int sessionMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        long raw = Duration.between(start, end).toMinutes();
        if (raw > Integer.MAX_VALUE) {
            raw = Integer.MAX_VALUE;
        }
        return (int) raw;
    }

    /**
     * One flexible lunch hour per day. It is not removed from the morning.
     * A break already taken counts toward that hour. Any lunch still due is
     * removed only after 8 hours, so a 9 hour stay is 8 hours of work.
     */
    public static int payableWorkMinutes(int checkedInMinutes, int breakMinutes) {
        int checkedIn = Math.max(0, checkedInMinutes);
        int taken = Math.max(0, breakMinutes);
        int lunchStillDue = Math.max(0, LUNCH_MINUTES - Math.min(LUNCH_MINUTES, taken));
        int afterEightHours = Math.max(0, checkedIn + taken - WORK_MINUTES);
        int automaticLunch = Math.min(lunchStillDue, afterEightHours);
        return Math.max(0, checkedIn - automaticLunch);
    }

    public static int workedMinutes(LocalDateTime start, LocalDateTime end) {
        return payableWorkMinutes(sessionMinutes(start, end), 0);
    }

    public static boolean duringLunch(LocalTime time) {
        return time != null && !time.isBefore(LUNCH_START) && time.isBefore(LUNCH_END);
    }

    public static int countableInactiveMinutes(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || !to.isAfter(from)) {
            return 0;
        }
        long raw = Duration.between(from, to).toMinutes();
        return raw > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) raw;
    }

    public static boolean duringInactivityGrace(LocalTime time) {
        return false;
    }

    /** A day is finished after check-out, or after 11:00 PM if check-out was missed. */
    public static boolean dayComplete(
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            LocalDate attendanceDate,
            LocalDateTime now) {
        if (checkIn == null) {
            return false;
        }
        if (checkOut != null) {
            return true;
        }
        if (attendanceDate == null || now == null) {
            return false;
        }
        return !now.isBefore(attendanceDate.atTime(LOGOUT_DEADLINE));
    }

    /** Hours that count toward the month total. An open day stays out until it is finished. */
    public static int finishedDayMinutes(
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            LocalDate attendanceDate,
            int storedWorkMinutes,
            int breakMinutes,
            LocalDateTime now) {
        if (!dayComplete(checkIn, checkOut, attendanceDate, now)) {
            return 0;
        }
        int minutes = countedWorkMinutes(checkIn, checkOut, attendanceDate, storedWorkMinutes, breakMinutes, now);
        return Math.min(WORK_MINUTES, minutes);
    }

    /**
     * A finished day uses the stored minutes. An open check-in counts from
     * that check-in until now, and stops at 11:00 PM.
     */
    public static int countedWorkMinutes(
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            LocalDate attendanceDate,
            int storedWorkMinutes,
            int breakMinutes,
            LocalDateTime now) {
        if (checkIn == null) {
            return 0;
        }
        if (checkOut != null) {
            return Math.max(0, storedWorkMinutes);
        }
        LocalDateTime end = now == null ? checkIn : now;
        if (attendanceDate != null) {
            LocalDateTime cap = attendanceDate.atTime(LOGOUT_DEADLINE);
            if (end.isAfter(cap)) {
                end = cap;
            }
        }
        if (!end.isAfter(checkIn)) {
            return Math.max(0, storedWorkMinutes);
        }
        long presence = Duration.between(checkIn, end).toMinutes();
        return (int) Math.max(0, presence - Math.max(0, breakMinutes));
    }
}
