package com.zidio.nexushr.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttendanceDayTest {

    @Test
    void lunchGapIsBreakAndTheNextCheckInResumesWork() {
        AttendanceDay day = new AttendanceDay();
        LocalDateTime in = LocalDateTime.of(2026, 10, 21, 10, 0);
        day.checkIn(in);
        day.checkOut(in.plusHours(3));
        day.checkIn(in.plusHours(4));
        day.checkOut(in.plusHours(9));

        assertEquals(8 * 60, day.closedWorkMinutes());
        assertEquals(60, day.breakMinutes());
        assertEquals("CLOSED", day.status());
    }

    @Test
    void secondCheckInWithoutCheckOutIsRejected() {
        AttendanceDay day = new AttendanceDay();
        day.checkIn(LocalDateTime.of(2026, 10, 21, 10, 0));

        assertThrows(IllegalStateException.class,
                () -> day.checkIn(LocalDateTime.of(2026, 10, 21, 11, 0)));
    }
}
