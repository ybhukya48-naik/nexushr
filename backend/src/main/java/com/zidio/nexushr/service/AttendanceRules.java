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
 * 10:16 onward is a late arrival. Lunch is 13:00–13:45 and is not work.
 * A paid day is 8 working hours inside a 9 hour presence window.
 */
public final class AttendanceRules {

    public static final LocalTime SHIFT_START = LocalTime.of(9, 30);
    public static final LocalTime BUFFER_END = LocalTime.of(10, 15);
    public static final LocalTime LUNCH_START = LocalTime.of(13, 0);
    public static final LocalTime LUNCH_END = LocalTime.of(13, 45);
    public static final LocalTime LUNCH_RETURN_DEADLINE = LocalTime.of(14, 0);
    public static final LocalTime LOGOUT_DEADLINE = LocalTime.of(23, 0);

    public static final int WORK_MINUTES = 8 * 60;
    public static final int LUNCH_MINUTES = 45;
    public static final int PRESENCE_MINUTES = WORK_MINUTES + LUNCH_MINUTES;
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

    public static int workedMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        long raw = Duration.between(start, end).toMinutes();
        if (raw > Integer.MAX_VALUE) {
            raw = Integer.MAX_VALUE;
        }
        return (int) Math.max(0, raw - lunchOverlapMinutes(start, end));
    }

    public static boolean duringLunch(LocalTime time) {
        return time != null && !time.isBefore(LUNCH_START) && time.isBefore(LUNCH_END);
    }
}
