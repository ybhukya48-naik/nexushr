package com.zidio.nexushr.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * A paid day is 8 working hours inside 9 hours. The other hour is a flexible lunch.
 * One casual leave and one paid leave each month are not deducted.
 * Sundays and listed public holidays are not working days.
 * Gorle Group also does not deduct the second Saturday.
 */
public final class HoursPayPolicy {

    public static final int DAY_MINUTES = 9 * 60;
    public static final int PAID_WORK_MINUTES = AttendanceRules.WORK_MINUTES;

    private HoursPayPolicy() {
    }

    public static boolean gorle(String companyCode) {
        if (companyCode == null) {
            return false;
        }
        String code = companyCode.toUpperCase();
        return code.contains("GORLE") || code.contains("GGEA");
    }

    public static boolean isSecondSaturday(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY
                && date.getDayOfMonth() >= 8
                && date.getDayOfMonth() <= 14;
    }

    public static int expectedMinutes(LocalDate start, LocalDate end, boolean gorle) {
        return expectedMinutes(start, end, gorle, Set.of());
    }

    public static int expectedMinutes(
            LocalDate start,
            LocalDate end,
            boolean gorle,
            Set<LocalDate> extraHolidays) {
        if (start == null || end == null || start.isAfter(end)) {
            return 0;
        }
        int days = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY || AttendanceRules.isPublicHoliday(date)) {
                continue;
            }
            if (extraHolidays != null && extraHolidays.contains(date)) {
                continue;
            }
            if (gorle && isSecondSaturday(date)) {
                continue;
            }
            days++;
        }
        return days * DAY_MINUTES;
    }

    public static int expectedPaidMinutes(
            LocalDate start,
            LocalDate end,
            boolean gorle,
            Set<LocalDate> extraHolidays) {
        int presence = expectedMinutes(start, end, gorle, extraHolidays);
        if (presence <= 0) {
            return 0;
        }
        return (presence / DAY_MINUTES) * PAID_WORK_MINUTES;
    }

    public static int expectedPaidMinutes(LocalDate start, LocalDate end, boolean gorle) {
        return expectedPaidMinutes(start, end, gorle, Set.of());
    }

    public static int deductibleShortfall(int expectedMinutes, int workedMinutes) {
        int shortfall = Math.max(0, expectedMinutes - Math.max(0, workedMinutes));
        int forgiven = 2 * AttendanceRules.WORK_MINUTES;
        return Math.max(0, shortfall - forgiven);
    }
}
