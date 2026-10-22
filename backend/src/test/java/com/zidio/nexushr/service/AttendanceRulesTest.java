package com.zidio.nexushr.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendanceRulesTest {

    @Test
    void bufferUntilTenFifteenIsNotLate() {
        assertFalse(AttendanceRules.isLateArrival(LocalTime.of(9, 30)));
        assertFalse(AttendanceRules.isLateArrival(LocalTime.of(10, 15)));
        assertTrue(AttendanceRules.isLateArrival(LocalTime.of(10, 16)));
        assertEquals(46, AttendanceRules.lateArrivalMinutes(LocalTime.of(10, 16)));
    }

    @Test
    void lunchIsRemovedFromWorkAndALaterCheckInResumes() {
        LocalDateTime in = LocalDateTime.of(2026, 10, 22, 9, 30);
        LocalDateTime lunchOut = LocalDateTime.of(2026, 10, 22, 13, 0);
        LocalDateTime back = LocalDateTime.of(2026, 10, 22, 13, 45);
        LocalDateTime out = LocalDateTime.of(2026, 10, 22, 18, 15);

        AttendanceDay day = new AttendanceDay();
        day.checkIn(in);
        day.checkOut(lunchOut);
        day.checkIn(back);
        day.checkOut(out);

        assertEquals(AttendanceRules.WORK_MINUTES, day.closedWorkMinutes());
        assertEquals(45, day.breakMinutes());
    }

    @Test
    void sundaysAndRepublicDayAreNotWorkingDays() {
        assertFalse(AttendanceRules.isWorkingDay(LocalDate.of(2026, 10, 25), false));
        assertFalse(AttendanceRules.isWorkingDay(LocalDate.of(2026, 1, 26), false));
        assertTrue(AttendanceRules.isWorkingDay(LocalDate.of(2026, 10, 22), false));
    }
}
