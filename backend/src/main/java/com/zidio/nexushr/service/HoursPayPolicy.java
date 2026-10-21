package com.zidio.nexushr.service;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * A paid day is 9 hours. One casual leave each month is not deducted.
 * Gorle Group also does not deduct the second Saturday.
 */
public final class HoursPayPolicy {

    public static final int DAY_MINUTES = 9 * 60;

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
        if (start == null || end == null || start.isAfter(end)) {
            return 0;
        }
        int days = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            if (gorle && isSecondSaturday(date)) {
                continue;
            }
            days++;
        }
        return days * DAY_MINUTES;
    }

    public static int deductibleShortfall(int expectedMinutes, int workedMinutes) {
        int shortfall = Math.max(0, expectedMinutes - Math.max(0, workedMinutes));
        return Math.max(0, shortfall - DAY_MINUTES);
    }
}
